package uk.gov.justice.digital.hmpps.visitscheduler.client

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.util.UriBuilder
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonercontactregistry.PrisonerContactDto
import java.time.Duration

@Component
class PrisonerContactRegistryClient(
  @Qualifier("prisonerContactRegistryWebClient") private val webClient: WebClient,
  @Value("\${prisoner-contact.registry.timeout:10s}") private val apiTimeout: Duration,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getPrisonersSocialContacts(
    prisonerId: String,
    withAddress: Boolean,
    approvedVisitorsOnly: Boolean,
  ): List<PrisonerContactDto>? {
    val uri = "/v2/prisoners/$prisonerId/contacts/social"
    return getPrisonersSocialContactsAsMono(prisonerId, withAddress = withAddress, approvedVisitorsOnly = approvedVisitorsOnly)
      .onErrorResume { e ->
        if (!isNotFoundError(e)) {
          LOG.error("getPrisonersSocialContacts Failed for get request $uri")
          Mono.empty()
        } else {
          LOG.error("getPrisonersSocialContacts NOT_FOUND for get request $uri")
          Mono.empty()
        }
      }
      .block(apiTimeout)
  }

  private fun getPrisonersSocialContactsAsMono(
    prisonerId: String,
    withAddress: Boolean,
    approvedVisitorsOnly: Boolean,
  ): Mono<List<PrisonerContactDto>> {
    val uri = "/v2/prisoners/$prisonerId/contacts/social"
    return webClient.get().uri(uri) {
      getSocialContactsUriBuilder(withAddress = withAddress, approvedVisitorsOnly = approvedVisitorsOnly, uriBuilder = it).build()
    }
      .retrieve()
      .bodyToMono<List<PrisonerContactDto>>()
  }

  private fun getSocialContactsUriBuilder(
    withAddress: Boolean,
    approvedVisitorsOnly: Boolean,
    uriBuilder: UriBuilder,
  ): UriBuilder {
    uriBuilder.queryParam("withAddress", withAddress)
    uriBuilder.queryParam("approvedVisitorsOnly", approvedVisitorsOnly)
    return uriBuilder
  }
}
