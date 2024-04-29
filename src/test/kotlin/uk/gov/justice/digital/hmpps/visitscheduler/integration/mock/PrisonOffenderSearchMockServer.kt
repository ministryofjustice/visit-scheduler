package uk.gov.justice.digital.hmpps.visitscheduler.integration.mock

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch.CurrentIncentiveDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch.IncentiveLevelDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch.PrisonerSearchResultDto
import java.time.LocalDate
import java.time.LocalDateTime

class PrisonOffenderSearchMockServer : WireMockServer(8093) {

  companion object {
    val MAPPER: ObjectMapper = JsonMapper.builder()
      .addModule(JavaTimeModule())
      .build()
  }

  fun stubGetPrisoner(
    prisonerId: String,
    prisonerSearchResultDto: PrisonerSearchResultDto?,
  ) {
    stubFor(
      get("/prisoner/$prisonerId")
        .willReturn(
          if (prisonerSearchResultDto == null) {
            aResponse().withStatus(HttpStatus.NOT_FOUND.value())
          } else {
            aResponse()
              .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
              .withStatus(200)
              .withBody(
                getJsonString(prisonerSearchResultDto),
              )
          },
        ),
    )
  }

  fun stubGetPrisonerByString(
    prisonerId: String,
    prisonCode: String,
    incentiveLevelCode: IncentiveLevel? = null,
    category: String? = null,
  ) {
    val incentiveLevel = incentiveLevelCode ?.let { IncentiveLevelDto(code = incentiveLevelCode.code, description = "") }
    val currentIncentive = incentiveLevel?.let { CurrentIncentiveDto(incentiveLevel, LocalDateTime.now().minusMonths(1), LocalDate.now().plusMonths(1)) }
    val prisonerSearchResultDto = PrisonerSearchResultDto(prisonerId, currentIncentive, prisonCode, category = category)

    stubGetPrisoner(
      prisonerId,
      prisonerSearchResultDto,
    )
  }

  private fun getJsonString(obj: Any): String {
    return MAPPER.writer().withDefaultPrettyPrinter().writeValueAsString(obj)
  }
}
