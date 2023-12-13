package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.client.NonAssociationsApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonerOffenderSearchClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevelDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels.LEVEL_FOUR
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels.LEVEL_ONE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels.LEVEL_THREE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels.LEVEL_TWO
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLocationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.ItemNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.TransitionalLocationTypes
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel

@Service
class PrisonerService(
  private val prisonApiClient: PrisonApiClient,
  private val nonAssociationsApiClient: NonAssociationsApiClient,
  private val prisonerOffenderSearchClient: PrisonerOffenderSearchClient,
  private val prisonService: PrisonsService,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getPrisonerNonAssociationList(prisonerId: String): List<PrisonerNonAssociationDetailDto> {
    val prisonerNonAssociationDetails = nonAssociationsApiClient.getPrisonerNonAssociation(prisonerId)
    prisonerNonAssociationDetails?.let {
      val nonAssociationList = prisonerNonAssociationDetails.nonAssociations
      LOG.debug("sessionHasNonAssociation prisonerId : $prisonerId has ${nonAssociationList.size} non associations!")
      return nonAssociationList
    }
    return emptyList()
  }

  fun hasPrisonerGotANonAssociationWith(prisonerId: String, nonAssociationPrisonerId: String): Boolean {
    val prisonerNonAssociationDetails = nonAssociationsApiClient.getPrisonerNonAssociation(prisonerId)
    prisonerNonAssociationDetails?.let {
      return prisonerNonAssociationDetails.nonAssociations.any { it.otherPrisonerDetails.prisonerNumber == nonAssociationPrisonerId }
    }
    return false
  }

  fun getPrisonerFullStatus(prisonerId: String): PrisonerDetailsDto? {
    return prisonApiClient.getPrisonerDetails(prisonerId)
  }

  fun getPrisonerHousingLocation(prisonerId: String, prisonCode: String): PrisonerHousingLocationsDto? {
    return prisonApiClient.getPrisonerHousingLocation(prisonerId)
  }

  private fun isPrisonerInTemporaryLocation(levels: List<PrisonerHousingLevelDto>): Boolean {
    if (levels.isNotEmpty()) {
      return TransitionalLocationTypes.contains(levels[0].code)
    }
    return false
  }

  fun getLevelsMapForPrisoner(prisonerHousingLocationsDto: PrisonerHousingLocationsDto): Map<PrisonerHousingLevels, String?> {
    with(prisonerHousingLocationsDto) {
      val housingLevel = if (isPrisonerInTemporaryLocation(levels)) lastPermanentLevels else levels

      val levelsMap: MutableMap<PrisonerHousingLevels, String?> = mutableMapOf()

      levelsMap[LEVEL_ONE] = getHousingLevelByLevelNumber(housingLevel, LEVEL_ONE)?.code
      levelsMap[LEVEL_TWO] = getHousingLevelByLevelNumber(housingLevel, LEVEL_TWO)?.code
      levelsMap[LEVEL_THREE] = getHousingLevelByLevelNumber(housingLevel, LEVEL_THREE)?.code
      levelsMap[LEVEL_FOUR] = getHousingLevelByLevelNumber(housingLevel, LEVEL_FOUR)?.code

      return levelsMap.toMap()
    }
  }

  private fun getHousingLevelByLevelNumber(levels: List<PrisonerHousingLevelDto>, housingLevel: PrisonerHousingLevels): PrisonerHousingLevelDto? {
    return levels.stream().filter { level -> level.level == housingLevel.level }.findFirst().orElse(null)
  }

  fun getPrisoner(prisonerId: String): PrisonerDto? {
    val prisonerSearchResultDto = prisonerOffenderSearchClient.getPrisoner(prisonerId)
    val incentiveLevelCode = prisonerSearchResultDto?.currentIncentive?.level?.code
    var incentiveLevel: IncentiveLevel? = null
    incentiveLevelCode?.let {
      incentiveLevel = IncentiveLevel.getIncentiveLevel(it)

      if (incentiveLevel == null) {
        LOG.error("Incentive level - $it for prisoner - $prisonerId not available in IncentiveLevel enum.")
      }
    }
    return PrisonerDto(prisonerSearchResultDto?.category, incentiveLevel, prisonCode = prisonerSearchResultDto?.prisonId)
  }

  fun getPrisonerSupportedPrisonCode(prisonerCode: String): String? {
    try {
      val prisonCode = prisonerOffenderSearchClient.getPrisoner(prisonerCode)?.prisonId
      prisonCode?.let {
        return this.prisonService.getSupportedPrison(prisonCode)
      }
    } catch (_: ItemNotFoundException) {}
    return null
  }
}
