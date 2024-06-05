package uk.gov.justice.digital.hmpps.visitscheduler.integration.mock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.OtherPrisonerDetails
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerNonAssociationDetailsDto

class NonAssociationsApiMockServer : WireMockServer(8094) {

  fun stubGetPrisonerNonAssociation(
    prisonerNumber: String,
    nonAssociationPrisonerId: String,
  ) {
    val otherPrisonerDetails = OtherPrisonerDetails(prisonerNumber = nonAssociationPrisonerId)
    val details = mutableListOf<PrisonerNonAssociationDetailDto>()

    details.add(PrisonerNonAssociationDetailDto(otherPrisonerDetails))

    val jsonBody = getJsonString(PrisonerNonAssociationDetailsDto(details))

    stubFor(
      get("/prisoner/$prisonerNumber/non-associations?includeOtherPrisons=false")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody(jsonBody),
        ),
    )
  }

  fun stubGetPrisonerNonAssociationEmpty(prisonerNumber: String) {
    stubGetPrisonerNonAssociation(prisonerNumber, prisonerNonAssociationDetailsDto = PrisonerNonAssociationDetailsDto())
  }

  fun stubGetPrisonerNonAssociation(prisonerNumber: String, prisonerNonAssociationDetailsDto: PrisonerNonAssociationDetailsDto? = null, status: HttpStatus = HttpStatus.NOT_FOUND) {
    stubFor(
      get("/prisoner/$prisonerNumber/non-associations?includeOtherPrisons=false")
        .willReturn(
          if (prisonerNonAssociationDetailsDto == null) {
            aResponse().withStatus(status.value())
          } else {
            aResponse()
              .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
              .withStatus(200)
              .withBody(
                getJsonString(prisonerNonAssociationDetailsDto),
              )
          },
        ),
    )
  }
}
