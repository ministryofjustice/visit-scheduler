package uk.gov.justice.digital.hmpps.visitscheduler.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch.PrisonerIncentiveLevelDto
import java.time.Duration

@Component
class PrisonerOffenderSearchClient(
  @Qualifier("prisonerOffenderSearchWebClient") private val webClient: WebClient,
  @Value("\${prisoner-offender-search.timeout:10s}") val apiTimeout: Duration
) {
  fun getPrisonerIncentiveLevel(offenderNo: String): PrisonerIncentiveLevelDto? {
    return webClient.get()
      .uri("/prisoner/$offenderNo")
      .accept(MediaType.APPLICATION_JSON)
      .retrieve().bodyToMono<PrisonerIncentiveLevelDto>().block(apiTimeout)
  }
}
