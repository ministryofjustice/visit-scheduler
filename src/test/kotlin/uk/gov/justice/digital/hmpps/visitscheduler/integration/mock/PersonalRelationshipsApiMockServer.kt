package uk.gov.justice.digital.hmpps.visitscheduler.integration.mock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import uk.gov.justice.digital.hmpps.visitscheduler.dto.relationships.ContactRestrictionDto

class PersonalRelationshipsApiMockServer : WireMockServer(8097) {

  companion object {
    val MAPPER: ObjectMapper = jacksonObjectMapper()
  }

  fun stubGetContactRestrictions(
    contactId: Long,
    result: List<ContactRestrictionDto>?,
    httpStatus: HttpStatus = HttpStatus.NOT_FOUND,
  ) {
    stubFor(
      get("/contact/$contactId/restriction")
        .willReturn(
          if (result == null) {
            aResponse().withStatus(httpStatus.value())
          } else {
            aResponse()
              .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
              .withStatus(200)
              .withBody(
                getJsonString(result),
              )
          },
        ),
    )
  }

  private fun getJsonString(obj: Any): String = MAPPER.writer().withDefaultPrettyPrinter().writeValueAsString(obj)
}
