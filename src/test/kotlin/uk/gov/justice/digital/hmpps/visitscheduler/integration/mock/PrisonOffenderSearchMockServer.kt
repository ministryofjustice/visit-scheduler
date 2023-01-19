package uk.gov.justice.digital.hmpps.visitscheduler.integration.mock

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisoner.offender.search.CurrentIncentiveDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisoner.offender.search.IncentiveLevelDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisoner.offender.search.PrisonerIncentiveLevelDto
import java.time.LocalDate
import java.time.LocalDateTime

class PrisonOffenderSearchMockServer : WireMockServer(8093) {
  fun stubGetPrisonerIncentiveLevel(
    offenderNo: String,
    prisonId: String?,
    incentiveLevel: IncentiveLevelDto
  ) {
    val currentIncentive = CurrentIncentiveDto(incentiveLevel, LocalDateTime.now().minusMonths(1), LocalDate.now().plusMonths(1))
    val prisonerIncentiveLevelDto = PrisonerIncentiveLevelDto(offenderNo, currentIncentive, prisonId)

    stubFor(
      get("/prisoner/$offenderNo")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody(
              getJsonString(prisonerIncentiveLevelDto)
            )
        )
    )
  }

  private fun getJsonString(obj: Any): String {
    return ObjectMapper().writer().withDefaultPrettyPrinter().writeValueAsString(obj)
  }
}
