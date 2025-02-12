package uk.gov.justice.digital.hmpps.visitscheduler.integration.mock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevelDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLocationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.VisitBalancesDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.mock.dto.PrisonerCellHistoryNativeDto

class PrisonApiMockServer : WireMockServer(8092) {

  fun stubGetPrisonerHousingLocation(offenderNo: String, internalLocation: String?, lastPermanentLevels: String? = null) {
    val levelsArray = getLevels(internalLocation)
    val lastPermanentLevelsArray = getLevels(lastPermanentLevels)

    val housingLocationsDto = PrisonerHousingLocationsDto(levelsArray, lastPermanentLevelsArray)

    stubFor(
      get("/api/offenders/$offenderNo/housing-location")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody(
              getJsonString(housingLocationsDto),
            ),
        ),
    )
  }

  fun stubGetCellHistory(bookingId: Int, prisonerCellHistoryNativeDto: PrisonerCellHistoryNativeDto? = null, status: HttpStatus = HttpStatus.NOT_FOUND) {
    stubFor(
      get("/api/bookings/$bookingId/cell-history?page=0&size=10")
        .willReturn(
          if (prisonerCellHistoryNativeDto == null) {
            aResponse().withStatus(status.value())
          } else {
            aResponse()
              .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
              .withStatus(200)
              .withBody(
                getJsonString(prisonerCellHistoryNativeDto),
              )
          },
        ),
    )
  }

  private fun getLevels(internalLocation: String?): List<PrisonerHousingLevelDto> {
    val housingLevels = mutableListOf<PrisonerHousingLevelDto>()

    internalLocation?.let {
      val values = internalLocation.split("-")
      if (values.isNotEmpty()) {
        for (i in 1..4) {
          val code = values.getOrNull(i)
          code?.let {
            housingLevels.add(getHousingLevel(i, code))
          }
        }
      }
    }

    return housingLevels.toList()
  }

  fun stubGetVisitBalances(prisonerId: String, visitBalances: VisitBalancesDto?) {
    val responseBuilder = aResponse()
      .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)

    stubFor(
      get("/api/bookings/offenderNo/$prisonerId/visit/balances")
        .willReturn(
          if (visitBalances == null) {
            responseBuilder
              .withStatus(HttpStatus.NOT_FOUND.value())
          } else {
            responseBuilder
              .withStatus(HttpStatus.OK.value())
              .withBody(getJsonString(visitBalances))
          },
        ),
    )
  }

  private fun getHousingLevel(level: Int, code: String): PrisonerHousingLevelDto = PrisonerHousingLevelDto(code = code, level = level, description = "level $level")
}
