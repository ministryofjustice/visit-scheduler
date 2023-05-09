package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonerOffenderSearchClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.OffenderNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerCellLocationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevelDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels.LEVEL_FOUR
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels.LEVEL_ONE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels.LEVEL_THREE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels.LEVEL_TWO
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLocationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.TransitionalLocationTypes
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevels

@Service
class PrisonerService(
  private val prisonApiClient: PrisonApiClient,
  private val prisonerOffenderSearchClient: PrisonerOffenderSearchClient,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getOffenderNonAssociationList(prisonerId: String): List<OffenderNonAssociationDetailDto> {
    val offenderNonAssociationDetails = prisonApiClient.getOffenderNonAssociation(prisonerId)
    offenderNonAssociationDetails?.let {
      val offenderNonAssociationList = offenderNonAssociationDetails.nonAssociations
      LOG.debug("sessionHasNonAssociation prisonerId : $prisonerId has ${offenderNonAssociationList.size} non associations!")
      return offenderNonAssociationList
    }
    return emptyList()
  }

  fun getPrisonerFullStatus(prisonerId: String): PrisonerDetailsDto? {
    return prisonApiClient.getPrisonerDetails(prisonerId)
  }

  fun getPrisonerLastHousingLocation(prisonerId: String, prisonCode: String): PrisonerHousingLocationsDto? {
    val prisonerDetailsDto = prisonApiClient.getPrisonerDetails(prisonerId)
    prisonerDetailsDto?.let {
      LOG.debug("Entered getPrisonerLastHousingLocation for : $prisonerDetailsDto")
      val lastLocation = getLastLocation(prisonerDetailsDto, prisonCode)
      LOG.debug("getPrisonerLastHousingLocation response : $lastLocation")
      return lastLocation?.let {
        return PrisonerHousingLocationsDto(levels = lastLocation.levels)
      }
    }
    return null
  }

  private fun getLastLocation(
    prisonerDetails: PrisonerDetailsDto,
    prisonCode: String,
  ): PrisonerCellLocationDto? {
    LOG.debug("Entered getLastLocation : ${prisonerDetails.bookingId}")
    val cellHistory = prisonApiClient.getCellHistory(prisonerDetails.bookingId)
    LOG.debug("getLastLocation response : $cellHistory")
    return cellHistory?.let {
      return cellHistory.history.firstOrNull { !isPrisonerInTemporaryLocation(it.levels) && it.prisonCode == prisonCode }
    }
  }

  fun getPrisonerHousingLocation(prisonerId: String, prisonCode: String): PrisonerHousingLocationsDto? {
    val prisonerHousingLocationsDto = prisonApiClient.getPrisonerHousingLocation(prisonerId)
    return prisonerHousingLocationsDto?.let {
      if (isPrisonerInTemporaryLocation(prisonerHousingLocationsDto.levels)) {
        return getPrisonerLastHousingLocation(prisonerId, prisonCode)
      }
      return prisonerHousingLocationsDto
    }
  }

  private fun isPrisonerInTemporaryLocation(levels: List<PrisonerHousingLevelDto>): Boolean {
    if (levels.isNotEmpty()) {
      return TransitionalLocationTypes.contains(levels[0].code)
    }
    return false
  }

  fun getLevelsMapForPrisoner(prisonerHousingLocationsDto: PrisonerHousingLocationsDto): Map<PrisonerHousingLevels, String?> {
    val levelsMap: MutableMap<PrisonerHousingLevels, String?> = mutableMapOf()

    levelsMap[LEVEL_ONE] = getHousingLevelByLevelNumber(prisonerHousingLocationsDto.levels, LEVEL_ONE.level)?.code
    levelsMap[LEVEL_TWO] = getHousingLevelByLevelNumber(prisonerHousingLocationsDto.levels, LEVEL_TWO.level)?.code
    levelsMap[LEVEL_THREE] = getHousingLevelByLevelNumber(prisonerHousingLocationsDto.levels, LEVEL_THREE.level)?.code
    levelsMap[LEVEL_FOUR] = getHousingLevelByLevelNumber(prisonerHousingLocationsDto.levels, LEVEL_FOUR.level)?.code

    return levelsMap.toMap()
  }

  private fun getHousingLevelByLevelNumber(levels: List<PrisonerHousingLevelDto>, housingLevel: Int): PrisonerHousingLevelDto? {
    return levels.stream().filter { level -> level.level == housingLevel }.findFirst().orElse(null)
  }

  fun getPrisoner(prisonerId: String): PrisonerDto? {
    val prisonerSearchResultDto = prisonerOffenderSearchClient.getPrisoner(prisonerId)
    val incentiveLevelCode = prisonerSearchResultDto?.currentIncentive?.level?.code
    var incentiveLevel: IncentiveLevels? = null
    incentiveLevelCode?.let {
      incentiveLevel = IncentiveLevels.getIncentiveLevel(it)
    }
    return PrisonerDto(prisonerSearchResultDto?.category, incentiveLevel)
  }
}
