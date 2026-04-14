package uk.gov.justice.digital.hmpps.visitscheduler.integration.mock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonerContactRegistryClient.Companion.GET_PRISONERS_APPROVED_SOCIAL_CONTACTS_URL
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonerContactRegistryClient.Companion.GET_PRISONER_CONTACT_DETAILS_WITH_RESTRICTIONS_URL
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonercontactregistry.PrisonerContactDto

class PrisonerContactRegistryMockServer : WireMockServer(8095) {
  fun stubGetPrisonerApprovedSocialContacts(
    prisonerId: String,
    withAddress: Boolean = false,
    withRestrictions: Boolean = false,
    contactsList: List<PrisonerContactDto>?,
    httpStatus: HttpStatus = HttpStatus.NOT_FOUND,
  ) {
    val url = GET_PRISONERS_APPROVED_SOCIAL_CONTACTS_URL.replace("{prisonerId}", prisonerId)
    stubFor(
      get("$url?${getContactsQueryParams(withAddress, withRestrictions)}")
        .willReturn(
          if (contactsList == null) {
            aResponse()
              .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
              .withStatus(httpStatus.value())
          } else {
            aResponse()
              .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
              .withStatus(HttpStatus.OK.value())
              .withBody(getJsonString(contactsList))
          },
        ),
    )
  }

  fun stubGetPrisonerContactRelationshipDetailsWithRestrictions(
    prisonerId: String,
    contactId: Long,
    prisonerContactId: Long,
    contact: PrisonerContactDto?,
    httpStatus: HttpStatus = HttpStatus.NOT_FOUND,
  ) {
    val url = GET_PRISONER_CONTACT_DETAILS_WITH_RESTRICTIONS_URL.replace("{prisonerId}", prisonerId).replace("{contactId}", contactId.toString()).replace("{relationshipId}", prisonerContactId.toString())
    stubFor(
      get(url)
        .willReturn(
          if (contact == null) {
            aResponse()
              .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
              .withStatus(httpStatus.value())
          } else {
            aResponse()
              .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
              .withStatus(HttpStatus.OK.value())
              .withBody(getJsonString(contact))
          },
        ),
    )
  }

  private fun getContactsQueryParams(
    withAddress: Boolean? = null,
    withRestrictions: Boolean? = null,
  ): String {
    val queryParams = ArrayList<String>()
    withAddress?.let {
      queryParams.add("withAddress=$it")
    }
    withRestrictions?.let {
      queryParams.add("withRestrictions=$it")
    }

    return queryParams.joinToString("&")
  }
}
