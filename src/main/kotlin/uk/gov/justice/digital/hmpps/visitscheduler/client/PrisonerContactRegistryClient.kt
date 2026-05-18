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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonercontactregistry.ContactWithOptionalPrisonerRelationshipDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonercontactregistry.PrisonerContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonercontactregistry.RestrictionDto
import java.time.Duration

@Component
class PrisonerContactRegistryClient(
  @param:Qualifier("prisonerContactRegistryWebClient") private val webClient: WebClient,
  @param:Value("\${prisoner-contact.registry.timeout:10s}") private val apiTimeout: Duration,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    const val GET_PRISONERS_APPROVED_SOCIAL_CONTACTS_URL = "/v2/prisoners/{prisonerId}/contacts/social/approved"
    const val GET_PRISONER_CONTACT_DETAILS_WITH_RESTRICTIONS_URL = "/v2/prisoners/{prisonerId}/contacts/{contactId}/relationships/{relationshipId}?withRestrictions=true"
    const val GET_CONTACT_GLOBAL_RESTRICTIONS_URL = "/v2/contacts/{contactId}/restrictions/global"
    const val GET_SEARCH_CONTACTS_URL: String = "/v2/contacts/search"
  }

  fun searchContacts(contactIds: List<Long>, prisonerId: String? = null, withRestrictions: Boolean = false): List<ContactWithOptionalPrisonerRelationshipDto>? {
    LOG.info("Searching for $contactIds, with restrictions: $withRestrictions and prisonerId = $prisonerId")
    return webClient.get().uri(GET_SEARCH_CONTACTS_URL) {
      getSearchContactsUriBuilder(contactIds, prisonerId, withRestrictions, it).build()
    }
      .retrieve()
      .bodyToMono<List<ContactWithOptionalPrisonerRelationshipDto>>()
      .onErrorResume { e ->
        LOG.error("searchContacts Failed for get request $GET_SEARCH_CONTACTS_URL, $e")
        Mono.empty()
      }
      .block(apiTimeout)
  }

  fun getPrisonerContactRelationshipDetailsWithRestrictions(
    prisonerId: String,
    contactId: Long,
    relationshipId: Long,
  ): PrisonerContactDto? {
    val uri = GET_PRISONER_CONTACT_DETAILS_WITH_RESTRICTIONS_URL.replace("{prisonerId}", prisonerId).replace("{contactId}", contactId.toString()).replace("{relationshipId}", relationshipId.toString())
    return webClient.get().uri(uri)
      .retrieve()
      .bodyToMono<PrisonerContactDto>()
      .onErrorResume { e ->
        if (!isNotFoundError(e)) {
          LOG.error("getPrisonerContactRelationshipDetailsWithRestrictions Failed for get request $uri")
          Mono.error(e)
        } else {
          LOG.info("getPrisonerContactRelationshipDetailsWithRestrictions NOT_FOUND for get request $uri")
          return@onErrorResume Mono.justOrEmpty(null)
        }
      }
      .block(apiTimeout)
  }

  fun getContactGlobalRestrictions(
    contactId: Long,
  ): List<RestrictionDto>? {
    val uri = GET_CONTACT_GLOBAL_RESTRICTIONS_URL.replace("{contactId}", contactId.toString())
    return webClient.get().uri(uri)
      .retrieve()
      .bodyToMono<List<RestrictionDto>>()
      .onErrorResume { e ->
        if (!isNotFoundError(e)) {
          LOG.error("getContactGlobalRestrictions Failed for get request $uri")
          Mono.error(e)
        } else {
          LOG.info("getContactGlobalRestrictions NOT_FOUND for get request $uri, returning empty list")
          Mono.just(emptyList())
        }
      }.block(apiTimeout)
  }

  private fun getSearchContactsUriBuilder(contactIds: List<Long>, prisonerId: String? = null, withRestrictions: Boolean = false, uriBuilder: UriBuilder): UriBuilder {
    uriBuilder.queryParam("contactIds", contactIds.joinToString(","))
    uriBuilder.queryParam("withRestrictions", withRestrictions)

    prisonerId?.let { uriBuilder.queryParam("prisonerId", it) }

    return uriBuilder
  }
}
