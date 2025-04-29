package uk.gov.justice.digital.hmpps.visitscheduler.integration.mock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonercontactregistry.PrisonerContactDto

class PrisonerContactRegistryMockServer : WireMockServer(8095) {
  fun stubGetPrisonerContacts(
    prisonerId: String,
    withAddress: Boolean = false,
    approvedVisitorsOnly: Boolean = true,
    contactsList: List<PrisonerContactDto>?,
    httpStatus: HttpStatus = HttpStatus.NOT_FOUND,
  ) {
    stubFor(
      get("/v2/prisoners/$prisonerId/contacts/social?${getContactsQueryParams(withAddress, approvedVisitorsOnly)}")
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
    approvedVisitorsOnly: Boolean? = null,
  ): String {
    val queryParams = ArrayList<String>()
    withAddress?.let {
      queryParams.add("withAddress=$it")
    }
    approvedVisitorsOnly?.let {
      queryParams.add("approvedVisitorsOnly=$it")
    }

    return queryParams.joinToString("&")
  }
}
