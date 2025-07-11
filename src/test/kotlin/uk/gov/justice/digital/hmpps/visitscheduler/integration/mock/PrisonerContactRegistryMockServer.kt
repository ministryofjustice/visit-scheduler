package uk.gov.justice.digital.hmpps.visitscheduler.integration.mock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonerContactRegistryClient.Companion.GET_PRISONERS_APPROVED_SOCIAL_CONTACTS_URL
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonercontactregistry.PrisonerContactDto

class PrisonerContactRegistryMockServer : WireMockServer(8095) {
  fun stubGetPrisonerApprovedSocialContacts(
    prisonerId: String,
    withAddress: Boolean = false,
    contactsList: List<PrisonerContactDto>?,
    httpStatus: HttpStatus = HttpStatus.NOT_FOUND,
  ) {
    val url = GET_PRISONERS_APPROVED_SOCIAL_CONTACTS_URL.replace("{prisonerId}", prisonerId)
    stubFor(
      get("$url?${getContactsQueryParams(withAddress)}")
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

  private fun getContactsQueryParams(
    withAddress: Boolean? = null,
  ): String {
    val queryParams = ArrayList<String>()
    withAddress?.let {
      queryParams.add("withAddress=$it")
    }

    return queryParams.joinToString("&")
  }
}
