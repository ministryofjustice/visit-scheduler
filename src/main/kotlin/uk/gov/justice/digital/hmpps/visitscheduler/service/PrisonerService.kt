package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonerOffenderSearchClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.OffenderNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevelDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLocationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.ItemNotFoundException

@Service
class PrisonerService(
  private val prisonApiClient: PrisonApiClient,
  private val prisonerOffenderSearchClient: PrisonerOffenderSearchClient,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    private const val ENHANCED_INCENTIVE_PRIVILEGE = "ENH"
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

  private fun getHousingLevelByLevelNumber(levels: List<PrisonerHousingLevelDto>, housingLevel: Int): PrisonerHousingLevelDto? {
    return levels.stream().filter { level -> level.level == housingLevel }.findFirst().orElse(null)
  }

  fun getPrisoner(prisonerId: String?): PrisonerDto? {
    prisonerId?.let {
      try {
        val prisonerSearchResultDto = prisonerOffenderSearchClient.getPrisoner(prisonerId)
        val enhanced = ENHANCED_INCENTIVE_PRIVILEGE == prisonerSearchResultDto?.currentIncentive?.level?.code
        return PrisonerDto(category = prisonerSearchResultDto?.category, enhanced = enhanced)
      } catch (e: WebClientResponseException) {
        if (e.statusCode == HttpStatus.NOT_FOUND) {
          LOG.error("Exception thrown on prisoner offender search call - /prisoner/$prisonerId", e)
          throw ItemNotFoundException("Prisoner not found $prisonerId", e)
        } else {
          LOG.error("Exception thrown on prisoner offender search call - /prisoner/$prisonerId", e)
          throw e
        }
      }
    }
    return null
  }
}
