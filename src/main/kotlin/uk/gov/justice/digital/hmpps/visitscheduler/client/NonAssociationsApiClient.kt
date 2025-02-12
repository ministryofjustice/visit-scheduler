package uk.gov.justice.digital.hmpps.visitscheduler.client

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerNonAssociationDetailsDto
import java.time.Duration

@Component
class NonAssociationsApiClient(
  @Qualifier("nonAssociationsApiWebClient") private val webClient: WebClient,
  @Value("\${prison.api.timeout:60s}") private val apiTimeout: Duration,
) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    private val TYPE_FOR_OFFENDER_NONASSOCIATION = object : ParameterizedTypeReference<PrisonerNonAssociationDetailsDto>() {}
  }

  fun getPrisonerNonAssociation(prisonerNumber: String): PrisonerNonAssociationDetailsDto? {
    LOG.debug("Entered getPrisonerNonAssociation $prisonerNumber")

    return webClient.get()
      .uri("/prisoner/$prisonerNumber/non-associations?includeOtherPrisons=true")
      .retrieve()
      .bodyToMono(TYPE_FOR_OFFENDER_NONASSOCIATION)
      .onErrorResume { e ->
        if (!isNotFoundError(e)) {
          LOG.error("getPrisonerNonAssociation Failed get request /prisoner/$prisonerNumber/non-associations")
          Mono.error(e)
        } else {
          LOG.debug("getPrisonerNonAssociation Not Found get request /prisoner/$prisonerNumber/non-associations")
          return@onErrorResume Mono.justOrEmpty(null)
        }
      }
      .block(apiTimeout)
  }
}
