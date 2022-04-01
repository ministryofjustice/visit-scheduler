package uk.gov.justice.digital.hmpps.visitscheduler.client

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.visitscheduler.data.OffenderNonAssociationDetails
import java.time.Duration

@Component
class PrisonApiClient(
  @Qualifier("prisonApiWebClient") private val webClient: WebClient,
  @Value("\${prison.api.timeout:60s}") val apiTimeout: Duration
) {

  private val offenderNonAssociationDetails = object : ParameterizedTypeReference<OffenderNonAssociationDetails>() {}

  fun getOffenderNonAssociation(offenderNo: String): OffenderNonAssociationDetails? {
    return webClient.get()
      .uri("/api/offenders/$offenderNo/non-association-details")
      .retrieve()
      .bodyToMono(offenderNonAssociationDetails)
      .block(apiTimeout)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
