package uk.gov.justice.digital.hmpps.visitscheduler.client

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch.PrisonerSearchResultDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.ItemNotFoundException
import java.time.Duration

@Component
class PrisonerOffenderSearchClient(
  @Qualifier("prisonerOffenderSearchWebClient") private val webClient: WebClient,
  @Value("\${prisoner.offender.search.timeout:10s}") private val apiTimeout: Duration,
) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    private val PRISONER_SEARCH_RESULT_DTO = object : ParameterizedTypeReference<PrisonerSearchResultDto>() {}
  }

  fun getPrisoner(offenderNo: String): PrisonerSearchResultDto? = webClient.get()
    .uri("/prisoner/$offenderNo")
    .accept(MediaType.APPLICATION_JSON)
    .retrieve()
    .bodyToMono(PRISONER_SEARCH_RESULT_DTO)
    .onErrorResume { e ->
      if (isNotFoundError(e)) {
        LOG.error("Exception thrown on prisoner offender search call - /prisoner/$offenderNo", e)
        Mono.error(ItemNotFoundException("Prisoner with prisonNumber - $offenderNo not found on offender search", e) as Throwable)
      } else {
        LOG.error("Exception thrown on prisoner offender search call - /prisoner/$offenderNo using offender search", e)
        Mono.error(e)
      }
    }.block(apiTimeout)

  private fun isNotFoundError(e: Throwable?) = e is WebClientResponseException && e.statusCode == NOT_FOUND
}
