package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.client.NonAssociationsApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonerOffenderSearchClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TransitionalLocationTypes
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevelDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels.LEVEL_FOUR
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels.LEVEL_ONE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels.LEVEL_THREE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels.LEVEL_TWO
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLocationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerAlertCreatedUpdatedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.ItemNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate

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

  fun getPrisonerHousingLocation(prisonerId: String, prisonCode: String): PrisonerHousingLocationsDto? {
    return prisonApiClient.getPrisonerHousingLocation(prisonerId)
  }

  fun getLevelsMapForPrisoner(prisonerHousingLocation: PrisonerHousingLocationsDto, sessionTemplates: List<SessionTemplate>?): Map<PrisonerHousingLevels, String?> {
    with(prisonerHousingLocation) {
      val housingLevel = if (sessionTemplates != null && isPrisonerInTemporaryLocation(levels)) {
        getHousingLevelForTransitionalPrisoner(levels[0].code, lastPermanentLevels, sessionTemplates)
      } else {
        levels
      }

      val levelsMap: MutableMap<PrisonerHousingLevels, String?> = mutableMapOf()

      levelsMap[LEVEL_ONE] = getHousingLevelByLevelNumber(housingLevel, LEVEL_ONE)?.code
      levelsMap[LEVEL_TWO] = getHousingLevelByLevelNumber(housingLevel, LEVEL_TWO)?.code
      levelsMap[LEVEL_THREE] = getHousingLevelByLevelNumber(housingLevel, LEVEL_THREE)?.code
      levelsMap[LEVEL_FOUR] = getHousingLevelByLevelNumber(housingLevel, LEVEL_FOUR)?.code

      return levelsMap.toMap()
    }
  }

  fun getPrisonerHousingLevels(prisonerId: String, prisonCode: String, sessionTemplates: List<SessionTemplate>?): Map<PrisonerHousingLevels, String?>? {
    val prisonerHousingLocation = getPrisonerHousingLocation(prisonerId, prisonCode)

    return prisonerHousingLocation?.let {
      getLevelsMapForPrisoner(it, sessionTemplates)
    }
  }

  fun getVisitBalance(prisonerId: String): Int {
    val visitBalance = prisonApiClient.getVisitBalances(prisonerId)
    return ((visitBalance?.remainingPvo ?: 0) + (visitBalance?.remainingVo ?: 0))
  }

  private fun getHousingLevelForTransitionalPrisoner(transitionalLocation: String, lastPermanentLevels: List<PrisonerHousingLevelDto>, sessionTemplates: List<SessionTemplate>): List<PrisonerHousingLevelDto> {
    // if there are sessions for the prisoners temporary location - level one code needs to be that transitional location
    // else return the prisoner's last permanent levels
    return if (sessionTemplates.isNotEmpty() && hasPrisonGotSessionsWithPrisonersTransitionalLocation(sessionTemplates, transitionalLocation)) {
      getHousingLevelForPrisonerInTemporaryLocation(transitionalLocation)
    } else {
      lastPermanentLevels
    }
  }

  private fun getHousingLevelForPrisonerInTemporaryLocation(prisonersTransitionalLocation: String): List<PrisonerHousingLevelDto> {
    val prisonerHousingLocationForTransitionalPrisoners = mutableListOf<PrisonerHousingLevelDto>()
    val prisonerHousingLocation = PrisonerHousingLevelDto(level = LEVEL_ONE.level, code = prisonersTransitionalLocation, description = prisonersTransitionalLocation)
    prisonerHousingLocationForTransitionalPrisoners.add(prisonerHousingLocation)
    return prisonerHousingLocationForTransitionalPrisoners.toList()
  }

  private fun getHousingLevelByLevelNumber(levels: List<PrisonerHousingLevelDto>, housingLevel: PrisonerHousingLevels): PrisonerHousingLevelDto? {
    return levels.stream().filter { level -> level.level == housingLevel.level }.findFirst().orElse(null)
  }

  fun getPrisoner(prisonerId: String): PrisonerDto? {
    val prisonerSearchResultDto = prisonerOffenderSearchClient.getPrisoner(prisonerId)

    return prisonerSearchResultDto?.let {
      val incentiveLevelCode = prisonerSearchResultDto.currentIncentive?.level?.code
      var incentiveLevel: IncentiveLevel? = null
      incentiveLevelCode?.let {
        incentiveLevel = IncentiveLevel.getIncentiveLevel(it)

        if (incentiveLevel == null) {
          LOG.error("Incentive level - $it for prisoner - $prisonerId not available in IncentiveLevel enum.")
        }
      }

      PrisonerDto(prisonerId, prisonerSearchResultDto.category, incentiveLevel, prisonCode = prisonerSearchResultDto.prisonId, alerts = prisonerSearchResultDto.alerts)
    }
  }

  fun isPrisonerInTemporaryLocation(levels: List<PrisonerHousingLevelDto>): Boolean {
    return (levels.isNotEmpty() && TransitionalLocationTypes.contains(levels[0].code))
  }

  fun hasPrisonGotSessionsWithPrisonersTransitionalLocation(sessionTemplates: List<SessionTemplate>, prisonersTransitionalLocation: String): Boolean {
    return sessionTemplates.asSequence().filter { it.includeLocationGroupType }.map { it.permittedSessionLocationGroups }.flatten().map { it.sessionLocations }.flatten().any { it.levelOneCode == prisonersTransitionalLocation }
  }

  fun getPrisonerPrisonCode(prisonerCode: String): String? {
    try {
      val prisonCode = prisonerOffenderSearchClient.getPrisoner(prisonerCode)?.prisonId
      prisonCode?.let {
        return this.prisonService.getPrisonCode(prisonCode)
      }
    } catch (_: ItemNotFoundException) {}
    return null
  }

  fun getPrisonerActiveAlertCodes(notificationDto: PrisonerAlertCreatedUpdatedNotificationDto): List<String> {
    return prisonerOffenderSearchClient.getPrisoner(notificationDto.prisonerNumber)?.alerts?.filter { it.active }?.map { it.alertCode }
      ?: emptyList()
  }
}
