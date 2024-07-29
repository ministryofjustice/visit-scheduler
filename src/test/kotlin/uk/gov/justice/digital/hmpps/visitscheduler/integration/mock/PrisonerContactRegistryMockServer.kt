package uk.gov.justice.digital.hmpps.visitscheduler.integration.mock

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonercontactregistry.VisitorActiveRestrictionsDto

class PrisonerContactRegistryMockServer : WireMockServer(8095) {

  companion object {
    val MAPPER: ObjectMapper = JsonMapper.builder()
      .addModule(JavaTimeModule())
      .build()
  }

  fun stubGetVisitorActiveRestrictions(
    prisonerId: String,
    visitorId: Long,
    visitorActiveRestrictionsDto: VisitorActiveRestrictionsDto?,
  ) {
    stubFor(
      get("/prisoners/$prisonerId/contacts/social/approved/$visitorId/restrictions/active")
        .willReturn(
          if (visitorActiveRestrictionsDto == null) {
            aResponse().withStatus(HttpStatus.NOT_FOUND.value())
          } else {
            aResponse()
              .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
              .withStatus(200)
              .withBody(
                getJsonString(visitorActiveRestrictionsDto),
              )
          },
        ),
    )
  }

  private fun getJsonString(obj: Any): String {
    return MAPPER.writer().withDefaultPrettyPrinter().writeValueAsString(obj)
  }
}
