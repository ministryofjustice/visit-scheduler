package uk.gov.justice.digital.hmpps.visitscheduler.integration.mock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.OffenderNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.OffenderNonAssociationDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.OffenderNonAssociationDto
import java.time.LocalDate

class NonAssociationsApiMockServer : WireMockServer(8094) {

  fun stubGetOffenderNonAssociation(
    offenderNo: String,
    nonAssociationId: String,
    effectiveDate: LocalDate,
    expiryDate: LocalDate? = null,
  ) {
    val offenderNonAssociation = OffenderNonAssociationDto(offenderNo = nonAssociationId)
    val details = mutableListOf<OffenderNonAssociationDetailDto>()
    if (expiryDate == null) {
      details.add(OffenderNonAssociationDetailDto(effectiveDate = effectiveDate, offenderNonAssociation = offenderNonAssociation))
    } else {
      details.add(OffenderNonAssociationDetailDto(effectiveDate, expiryDate, offenderNonAssociation))
    }
    val jsonBody = getJsonString(OffenderNonAssociationDetailsDto(details))

    stubFor(
      get("/legacy/api/offenders/$offenderNo/non-association-details?currentPrisonOnly=false&excludeInactive=true")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody(jsonBody),
        ),
    )
  }

  fun stubGetOffenderNonAssociationHttpError(status: HttpStatus = HttpStatus.BAD_REQUEST) {
    stubFor(
      get("/legacy/api/offenders/FAKE-offenderNo/non-association-details?currentPrisonOnly=false&excludeInactive=true")
        .willReturn(
          aResponse()
            .withStatus(status.value()),
        ),
    )
  }

  fun stubGetOffenderNonAssociationEmpty(offenderNo: String) {
    stubGetOffenderNonAssociation(offenderNo, offenderNonAssociationDetailsDto = OffenderNonAssociationDetailsDto())
  }

  fun stubGetOffenderNonAssociation(offenderNo: String, offenderNonAssociationDetailsDto: OffenderNonAssociationDetailsDto? = null, status: HttpStatus = HttpStatus.NOT_FOUND) {
    stubFor(
      get("/legacy/api/offenders/$offenderNo/non-association-details?currentPrisonOnly=false&excludeInactive=true")
        .willReturn(
          if (offenderNonAssociationDetailsDto == null) {
            aResponse().withStatus(status.value())
          } else {
            aResponse()
              .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
              .withStatus(200)
              .withBody(
                getJsonString(offenderNonAssociationDetailsDto),
              )
          },
        ),
    )
  }
}
