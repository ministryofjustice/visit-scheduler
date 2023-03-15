package uk.gov.justice.digital.hmpps.visitscheduler.integration.mock

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevelDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLocationsDto

class PrisonApiMockServer : WireMockServer(8092) {

  fun stubGetOffenderNonAssociation(
    offenderNo: String,
    nonAssociationId: String,
    effectiveDate: String,
    expiryDate: String? = null,
  ) {
    stubFor(
      get("/api/offenders/$offenderNo/non-association-details")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody(
              if (expiryDate.isNullOrEmpty()) {
                """
                {
                  "nonAssociations": [
                    {
                      "effectiveDate": "$effectiveDate",
                      "offenderNonAssociation": {
                        "offenderNo": "$nonAssociationId"
                      }
                    }
                  ]
                }
                """.trimIndent()
              } else {
                """
                {
                  "nonAssociations": [
                    {
                      "effectiveDate": "$effectiveDate",
                      "expiryDate": "$expiryDate",
                      "offenderNonAssociation": {
                        "offenderNo": "$nonAssociationId"
                      }
                    }
                  ]
                }
                """.trimIndent()
              },
            ),
        ),
    )
  }

  fun stubGetOffenderNonAssociationEmpty(offenderNo: String) {
    stubFor(
      get("/api/offenders/$offenderNo/non-association-details")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody(
              """
                {
                  "nonAssociations": []
                }
              """.trimIndent(),
            ),
        ),
    )
  }

  fun stubGetPrisonerHousingLocation(offenderNo: String, internalLocation: String?) {
    val levels = getLevels(internalLocation)
    val housingLocationsDto = PrisonerHousingLocationsDto(levels)

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
    val prisonerDetailsDto = PrisonerDetailsDto(nomsId = offenderNo, establishmentCode = prisonCode)

    stubFor(
      get("/api/prisoners/$offenderNo/full-status")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody(
              getJsonString(prisonerDetailsDto),
            ),
        ),
    )
  }

  private fun getJsonString(obj: Any): String {
    return ObjectMapper().writer().withDefaultPrettyPrinter().writeValueAsString(obj)
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
