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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerCellHistoryDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLocationsDto
import java.time.Duration

@Component
class PrisonApiClient(
  @Qualifier("prisonApiWebClient") private val webClient: WebClient,
  @Value("\${prison.api.timeout:60s}") private val apiTimeout: Duration,
) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    private val TYPE_FOR_PRISONER_HOUSING_LOCATIONS = object : ParameterizedTypeReference<PrisonerHousingLocationsDto>() {}
    private val TYPE_FOR_PRISONER_DETAILS = object : ParameterizedTypeReference<PrisonerDetailsDto>() {}
    private val TYPE_FOR_PRISONER_CELL_HISTORY = object : ParameterizedTypeReference<PrisonerCellHistoryDto>() {}
  }

  fun getPrisonerHousingLocation(offenderNo: String): PrisonerHousingLocationsDto? {
    LOG.debug("Entered getPrisonerHousingLocation $offenderNo")
    return webClient.get()
      .uri("/api/offenders/$offenderNo/housing-location")
      .retrieve()
      .bodyToMono(TYPE_FOR_PRISONER_HOUSING_LOCATIONS)
      .onErrorResume {
          e ->
        if (!isNotFoundError(e)) {
          LOG.error("getPrisonerHousingLocation Failed get request /api/offenders/$offenderNo/housing-location")
          Mono.error(e)
        } else {
          LOG.debug("getPrisonerHousingLocation Not Found get request /api/offenders/$offenderNo/housing-location")
          return@onErrorResume Mono.justOrEmpty(null)
        }
      }
      .block(apiTimeout)
  }

  fun getPrisonerDetails(offenderNo: String): PrisonerDetailsDto? {
    LOG.debug("Entered getPrisonerDetails $offenderNo")
    return webClient.get()
      .uri("/api/prisoners/$offenderNo/full-status")
      .retrieve()
      .bodyToMono(TYPE_FOR_PRISONER_DETAILS)
      .onErrorResume { e ->
        if (!isNotFoundError(e)) {
          LOG.error("getPrisonerDetails Failed get request /api/prisoners/$offenderNo/full-status")
          Mono.error(e)
        } else {
          LOG.debug("getPrisonerDetails Not Found get request /api/prisoners/$offenderNo/full-status")
          return@onErrorResume Mono.justOrEmpty(null)
        }
      }
      .block(apiTimeout)
  }
}

fun isNotFoundError(e: Throwable?) =
  e is WebClientResponseException && e.statusCode == NOT_FOUND
