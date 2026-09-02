package uk.gov.justice.digital.hmpps.visitscheduler.integration.mock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visit.allocation.VisitOrderPrisonerBalanceDto

class VisitAllocationApiMockServer : WireMockServer(8098) {

  fun stubGetPrisonerVOBalance(
    prisonerId: String,
    prisonerBalance: VisitOrderPrisonerBalanceDto?,
    httpStatus: HttpStatus = HttpStatus.NOT_FOUND,
  ) {
    val responseBuilder = createJsonResponseBuilder()
    stubFor(
      WireMock.get("/visits/allocation/prisoner/$prisonerId/balance")
        .willReturn(
          if (prisonerBalance == null) {
            responseBuilder
              .withStatus(httpStatus.value())
          } else {
            responseBuilder
              .withStatus(HttpStatus.OK.value())
              .withBody(getJsonString(prisonerBalance))
          },
        ),
    )
  }
}
