package uk.gov.justice.digital.hmpps.visitscheduler.integration.mock

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch.CurrentIncentiveDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch.IncentiveLevelDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch.PrisonerIncentiveLevelDto
import java.time.LocalDate
import java.time.LocalDateTime

class PrisonOffenderSearchMockServer : WireMockServer(8093) {

  companion object {
    val MAPPER: ObjectMapper = JsonMapper.builder()
      .addModule(JavaTimeModule())
      .build()
  }

  fun stubGetPrisonerIncentiveLevel(
    prisonerId: String,
    prisonCode: String,
    incentiveLevel: IncentiveLevelDto
  ) {
    val currentIncentive = CurrentIncentiveDto(incentiveLevel, LocalDateTime.now().minusMonths(1), LocalDate.now().plusMonths(1))
    val prisonerIncentiveLevelDto = PrisonerIncentiveLevelDto(prisonerId, currentIncentive, prisonCode)

    stubFor(
      get("/prisoner/$prisonerId")
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

  fun stubGetPrisonerIncentiveLevel(
    prisonerId: String,
    prisonCode: String,
    incentiveLevelCode: String
  ) {
    val incentiveLevel = IncentiveLevelDto(code = incentiveLevelCode, description = "")
    return stubGetPrisonerIncentiveLevel(prisonerId, prisonCode, incentiveLevel)
  }

  private fun getJsonString(obj: Any): String {
    return MAPPER.writer().withDefaultPrettyPrinter().writeValueAsString(obj)
  }
}
