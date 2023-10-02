package uk.gov.justice.digital.hmpps.visitscheduler.integration.mock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevelDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLocationsDto
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

  fun stubGetPrisonerDetails(offenderNo: String, prisonCode: String) {
    val prisonerDetailsDto = PrisonerDetailsDto(nomsId = offenderNo, establishmentCode = prisonCode, bookingId = 1)
    stubGetPrisonerDetails(offenderNo, prisonerDetailsDto)
  }

  fun stubGetPrisonerDetails(offenderNo: String, prisonerDetailsDto: PrisonerDetailsDto?) {
    stubFor(
      get("/api/prisoners/$offenderNo/full-status")
        .willReturn(
          if (prisonerDetailsDto == null) {
            aResponse().withStatus(HttpStatus.NOT_FOUND.value())
          } else {
            aResponse()
              .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
              .withStatus(200)
              .withBody(
                getJsonString(prisonerDetailsDto),
              )
          },
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
      val values = it.split("-")

      for (i in 1..4) {
        val code = values.getOrNull(i)
        code?.let {
          housingLevels.add(getHousingLevel(i, code))
        }
      }
    }
    return housingLevels.toList()
  }

  private fun getHousingLevel(level: Int, code: String): PrisonerHousingLevelDto {
    return PrisonerHousingLevelDto(code = code, level = level, description = "level $level")
  }
}
