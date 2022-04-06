package uk.gov.justice.digital.hmpps.visitscheduler.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OffenderNonAssociationDetailsDto
import java.time.Duration

@Component
class PrisonApiClient(
  @Qualifier("prisonApiWebClient") private val webClient: WebClient,
  @Value("\${prison.api.timeout:60s}") val apiTimeout: Duration
) {

  private val offenderNonAssociationDetails = object : ParameterizedTypeReference<OffenderNonAssociationDetailsDto>() {}

  fun getOffenderNonAssociation(offenderNo: String): OffenderNonAssociationDetailsDto? {
    return webClient.get()
      .uri("/api/offenders/$offenderNo/non-association-details")
      .retrieve()
      .bodyToMono(offenderNonAssociationDetails)
      .block(apiTimeout)
  }
}
