package uk.gov.justice.digital.hmpps.visitscheduler.client

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.OffenderNonAssociationDetailsDto
import java.time.Duration

@Component
class NonAssociationsApiClient(
  @Qualifier("nonAssociationsApiWebClient") private val webClient: WebClient,
  @Value("\${prison.api.timeout:60s}") private val apiTimeout: Duration,
) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    private val TYPE_FOR_OFFENDER_NONASSOCIATION = object : ParameterizedTypeReference<OffenderNonAssociationDetailsDto>() {}
  }

  fun getOffenderNonAssociation(offenderNo: String): OffenderNonAssociationDetailsDto? {
    LOG.debug("Entered getOffenderNonAssociation $offenderNo")
    return webClient.get()
      .uri("/legacy/api/offenders/$offenderNo/non-association-details?currentPrisonOnly=true&excludeInactive=true")
      .retrieve()
      .bodyToMono(TYPE_FOR_OFFENDER_NONASSOCIATION)
      .onErrorResume {
          e ->
        if (!isNotFoundError(e)) {
          LOG.error("getOffenderNonAssociation Failed get request /legacy/api/offenders/$offenderNo/non-association-details")
          Mono.error(e)
        } else {
          LOG.debug("getOffenderNonAssociation Not Found get request /legacy/api/offenders/$offenderNo/non-association-details")
          return@onErrorResume Mono.justOrEmpty(null)
        }
      }
      .block(apiTimeout)
  }
}
