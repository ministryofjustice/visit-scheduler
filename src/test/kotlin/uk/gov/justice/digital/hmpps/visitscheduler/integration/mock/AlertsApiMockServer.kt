package uk.gov.justice.digital.hmpps.visitscheduler.integration.mock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import uk.gov.justice.digital.hmpps.visitscheduler.dto.alerts.AlertDto

class AlertsApiMockServer : WireMockServer(8097) {

  companion object {
    val MAPPER: ObjectMapper = jacksonObjectMapper()
  }

  fun stubGetAlertDetails(
    alertUuid: String,
    alert: AlertDto?,
    httpStatus: HttpStatus = HttpStatus.NOT_FOUND,
  ) {
    stubFor(
      get("/alerts/$alertUuid")
        .willReturn(
          if (alert == null) {
            aResponse().withStatus(httpStatus.value())
          } else {
            aResponse()
              .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
              .withStatus(200)
              .withBody(
                getJsonString(alert),
              )
          },
        ),
    )
  }

  private fun getJsonString(obj: Any): String = MAPPER.writer().withDefaultPrettyPrinter().writeValueAsString(obj)
}
