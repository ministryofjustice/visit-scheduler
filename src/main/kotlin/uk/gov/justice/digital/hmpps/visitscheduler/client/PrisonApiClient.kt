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
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLocationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.VisitBalancesDto
import java.time.Duration

@Component
class PrisonApiClient(
  @param:Qualifier("prisonApiWebClient") private val webClient: WebClient,
  @param:Value("\${prison.api.timeout:10s}") private val apiTimeout: Duration,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    private val TYPE_FOR_PRISONER_HOUSING_LOCATIONS = object : ParameterizedTypeReference<PrisonerHousingLocationsDto>() {}
  }

  fun getPrisonerHousingLocation(offenderNo: String): PrisonerHousingLocationsDto? {
    LOG.debug("Entered getPrisonerHousingLocation $offenderNo")
    return webClient.get()
      .uri("/api/offenders/$offenderNo/housing-location")
      .retrieve()
      .bodyToMono(TYPE_FOR_PRISONER_HOUSING_LOCATIONS)
      .onErrorResume { e ->
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

  fun getVisitBalances(prisonerId: String): VisitBalancesDto? {
    LOG.debug("Entered getVisitBalances $prisonerId")
    val uri = "/api/bookings/offenderNo/$prisonerId/visit/balances"
    return webClient.get()
      .uri(uri)
      .retrieve()
      .bodyToMono<VisitBalancesDto>()
      .onErrorResume { e ->
        if (!isNotFoundError(e)) {
          LOG.error("getVisitBalances Failed get request $uri")
          Mono.error(e)
        } else {
          LOG.debug("getVisitBalances Not Found get request $uri")
          return@onErrorResume Mono.justOrEmpty(null)
        }
      }
      .block(apiTimeout)
  }
}

fun isNotFoundError(e: Throwable?) = e is WebClientResponseException && e.statusCode == NOT_FOUND
