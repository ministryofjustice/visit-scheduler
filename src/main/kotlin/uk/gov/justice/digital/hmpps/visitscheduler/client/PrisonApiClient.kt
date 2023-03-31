package uk.gov.justice.digital.hmpps.visitscheduler.client

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.OffenderNonAssociationDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerCellHistoryDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLocationsDto
import java.time.Duration
import java.util.*

@Component
class PrisonApiClient(
  @Qualifier("prisonApiWebClient") private val webClient: WebClient,
  @Value("\${prison.api.timeout:60s}") private val apiTimeout: Duration,
) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    private val TYPE_FOR_OFFENDER_NONASSOCIATION = object : ParameterizedTypeReference<Optional<OffenderNonAssociationDetailsDto>>() {}
    private val TYPE_FOR_PRISONER_HOUSING_LOCATIONS = object : ParameterizedTypeReference<Optional<PrisonerHousingLocationsDto>>() {}
    private val TYPE_FOR_PRISONER_DETAILS = object : ParameterizedTypeReference<Optional<PrisonerDetailsDto>>() {}
    private val TYPE_FOR_PRISONER_CELL_HISTORY = object : ParameterizedTypeReference<Optional<PrisonerCellHistoryDto>>() {}
  }

  private fun isNotFoundError(e: Throwable?) =
    e is WebClientResponseException && e.statusCode == NOT_FOUND

  fun getOffenderNonAssociation(offenderNo: String): OffenderNonAssociationDetailsDto? {
    return webClient.get()
      .uri("/api/offenders/$offenderNo/non-association-details")
      .retrieve()
      .bodyToMono(TYPE_FOR_OFFENDER_NONASSOCIATION)
      .onErrorResume {
          e ->
        if (!isNotFoundError(e)) {
          LOG.error("Failed get request /api/offenders/$offenderNo/non-association-details")
          Mono.error(e)
        } else {
          return@onErrorResume Mono.just(Optional.empty())
        }
      }
      .block(apiTimeout)?.orElseGet { null }
  }

  fun getPrisonerHousingLocation(offenderNo: String): PrisonerHousingLocationsDto? {
    return webClient.get()
      .uri("/api/offenders/$offenderNo/housing-location")
      .retrieve()
      .bodyToMono(TYPE_FOR_PRISONER_HOUSING_LOCATIONS)
      .onErrorResume {
          e ->
        if (!isNotFoundError(e)) {
          LOG.error("Failed get request /api/offenders/$offenderNo/housing-location")
          Mono.error(e)
        } else {
          return@onErrorResume Mono.just(Optional.empty())
        }
      }
      .block(apiTimeout)?.orElseGet { null }
  }

  fun getPrisonerDetails(offenderNo: String): PrisonerDetailsDto? {
    return webClient.get()
      .uri("/api/prisoners/$offenderNo/full-status")
      .retrieve()
      .bodyToMono(TYPE_FOR_PRISONER_DETAILS)
      .onErrorResume { e ->
        if (!isNotFoundError(e)) {
          LOG.error("Failed get request /api/prisoners/$offenderNo/full-status")
          Mono.error(e)
        } else {
          return@onErrorResume Mono.just(Optional.empty())
        }
      }
      .block(apiTimeout)?.orElseGet { null }
  }

  fun getCellHistory(bookingId: Int): PrisonerCellHistoryDto? {
    return webClient.get()
      .uri("/api/bookings/$bookingId/cell-history?page=0&size=3")
      .retrieve()
      .bodyToMono(TYPE_FOR_PRISONER_CELL_HISTORY)
      .onErrorResume {
          e ->
        if (!isNotFoundError(e)) {
          LOG.error("Failed get request /api/bookings/$bookingId/cell-history?page=0&size=3s")
          Mono.error(e)
        } else {
          return@onErrorResume Mono.just(Optional.empty())
        }
      }
      .block(apiTimeout)?.orElseGet { null }
  }
}
