package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.OffenderNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevelDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLocationsDto

@Service
class PrisonApiService(
  private val prisonApiClient: PrisonApiClient
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getOffenderNonAssociationList(prisonerId: String): List<OffenderNonAssociationDetailDto> {
    try {
      val offenderNonAssociationList = prisonApiClient.getOffenderNonAssociation(prisonerId)?.nonAssociations ?: emptyList()
      LOG.debug("sessionHasNonAssociation prisonerId : $prisonerId has ${offenderNonAssociationList.size} non associations!")
      return offenderNonAssociationList
    } catch (e: WebClientResponseException) {
      if (e.statusCode != HttpStatus.NOT_FOUND) {
        LOG.error("Exception thrown on prison API call - /api/offenders/$prisonerId/non-association-details", e)
        throw e
      }
    }

    return emptyList()
  }

  fun getPrisonerFullStatus(prisonerId: String): PrisonerDetailsDto? {
    try {
      return prisonApiClient.getPrisonerDetails(prisonerId)
    } catch (e: WebClientResponseException) {
      if (e.statusCode != HttpStatus.NOT_FOUND) {
        LOG.error("Exception thrown on prison API call - /api/prisoners/$prisonerId/full-status", e)
        throw e
      }
    }

    return null
  }

  fun getPrisonerHousingLocation(prisonerId: String): PrisonerHousingLocationsDto? {
    try {
      return prisonApiClient.getPrisonerHousingLocation(prisonerId)
    } catch (e: WebClientResponseException) {
      if (e.statusCode != HttpStatus.NOT_FOUND) {
        LOG.error("Exception thrown on prison API call - /api/offenders/$prisonerId/housing-location", e)
        throw e
      }
    }

    return null
  }

  fun getLevelsMapForPrisoner(prisonerHousingLocationsDto: PrisonerHousingLocationsDto): Map<PrisonerHousingLevels, String?> {
    val levelsMap: MutableMap<PrisonerHousingLevels, String?> = mutableMapOf()

    levelsMap[PrisonerHousingLevels.LEVEL_ONE] = getHousingLevelByLevelNumber(prisonerHousingLocationsDto.levels, PrisonerHousingLevels.LEVEL_ONE.level)?.code
    levelsMap[PrisonerHousingLevels.LEVEL_TWO] = getHousingLevelByLevelNumber(prisonerHousingLocationsDto.levels, PrisonerHousingLevels.LEVEL_TWO.level)?.code
    levelsMap[PrisonerHousingLevels.LEVEL_THREE] = getHousingLevelByLevelNumber(prisonerHousingLocationsDto.levels, PrisonerHousingLevels.LEVEL_THREE.level)?.code
    levelsMap[PrisonerHousingLevels.LEVEL_FOUR] = getHousingLevelByLevelNumber(prisonerHousingLocationsDto.levels, PrisonerHousingLevels.LEVEL_FOUR.level)?.code

    return levelsMap.toMap()
  }

  fun hasPrisonerGotEnhancedPrivilege(prisonerId: String): Boolean {
    // TODO This must implemented by another technical task this is just a dummy call
    return true
  }

  private fun getHousingLevelByLevelNumber(levels: List<PrisonerHousingLevelDto>, housingLevel: Int): PrisonerHousingLevelDto? {
    return levels.stream().filter { level -> level.level == housingLevel }.findFirst().orElse(null)
  }
}
