package uk.gov.justice.digital.hmpps.visitscheduler.client

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.visitscheduler.dto.relationships.ContactRestrictionDto
import java.time.Duration

@Component
class PersonalRelationshipApiClient(
  @param:Qualifier("personalRelationshipApiWebClient") private val webClient: WebClient,
  @param:Value("\${prisoner.offender.search.timeout:10s}") private val apiTimeout: Duration,
) {
  companion object {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getContactRestrictions(contactId: Long): List<ContactRestrictionDto>? {
    val uri = "/contact/$contactId/restriction"
    return webClient.get()
      .uri(uri)
      .accept(MediaType.APPLICATION_JSON)
      .retrieve()
      .bodyToMono<List<ContactRestrictionDto>>()
      .onErrorResume { e ->
        if (!isNotFoundError(e)) {
          logger.error("getContactRestrictions Failed get request $uri")
          throw e
        } else {
          logger.error("getContactRestrictions returned a NOT_FOUND for request $uri")
          Mono.empty()
        }
      }
      .block(apiTimeout)
  }
}
