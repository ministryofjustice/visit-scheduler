package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.OffenderNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonApiGetLevelsEndpoint
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLocationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerUnitCodeDto

@Service
class PrisonApiService(
  @Value("\${prison.api.levels-endpoint:HOUSING_LOCATION}")
  private val prisonApiLevelsEndpoint: PrisonApiGetLevelsEndpoint = PrisonApiGetLevelsEndpoint.HOUSING_LOCATION,

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

  fun getPrisonerFullStatus(prisonerId: String): PrisonerUnitCodeDto? {
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

  fun getPrisonerDetails(prisonerId: String): PrisonerDetailsDto? {
    return if (prisonApiLevelsEndpoint == PrisonApiGetLevelsEndpoint.FULL_STATUS)
      getPrisonerFullStatus(prisonerId)
    else
      getPrisonerHousingLocation(prisonerId)
  }
}
