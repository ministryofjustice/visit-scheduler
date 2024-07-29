package uk.gov.justice.digital.hmpps.visitscheduler.client

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonercontactregistry.VisitorActiveRestrictionsDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.ItemNotFoundException
import java.time.Duration

@Component
class PrisonerContactRegistryClient(
  @Qualifier("prisonerContactRegistryWebClient") private val webClient: WebClient,
  @Value("\${prisoner-contact.registry.timeout:10s}") private val apiTimeout: Duration,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getVisitorActiveRestrictions(prisonerId: String, visitorId: Long): VisitorActiveRestrictionsDto {
    val uri = "/prisoners/$prisonerId/contacts/social/approved/$visitorId/restrictions/active"

    return webClient.get()
      .uri(uri)
      .retrieve()
      .bodyToMono<VisitorActiveRestrictionsDto>()
      .onErrorResume {
          e ->
        if (!isNotFoundError(e)) {
          LOG.error("getVisitorActiveRestrictions Failed get request $uri")
          Mono.error(e)
        } else {
          LOG.error("getVisitorActiveRestrictions NOT FOUND get request $uri")
          Mono.error { ItemNotFoundException("No Active restrictions could be found for visitor - $visitorId") }
        }
      }
      .blockOptional(apiTimeout).get()
  }
}
