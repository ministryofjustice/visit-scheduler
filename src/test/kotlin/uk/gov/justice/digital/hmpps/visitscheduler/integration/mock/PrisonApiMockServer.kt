package uk.gov.justice.digital.hmpps.visitscheduler.integration.mock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.springframework.http.MediaType
import java.lang.StringBuilder

class PrisonApiMockServer : WireMockServer(8092) {

  fun stubGetOffenderNonAssociation(
    offenderNo: String,
    nonAssociationId: String,
    effectiveDate: String,
    expiryDate: String? = null
  ) {
    stubFor(
      get("/api/offenders/$offenderNo/non-association-details")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody(
              if (expiryDate.isNullOrEmpty())
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
              else
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
            )
        )
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
              """.trimIndent()
            )
        )
    )
  }

  fun stubGetPrisonerHousingLocation(offenderNo: String, internalLocation: String?) {
    val levels = getLevels(internalLocation)

    stubFor(
      get("/api/offenders/$offenderNo/housing-location")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody(
              getHousingLocationJsonResponse(levels)
            )
        )
    )
  }

  private fun getHousingLocationJsonResponse(levels: Map<Int, String?>): String {

    val jsonResponse = StringBuilder("")
    jsonResponse.append(
      """
      {
        "levels" : [
      """
    )
    jsonResponse.append(getLevelsText(levels))
    jsonResponse.append(
      """
        ]
      }
      """.trimIndent()
    )

    return jsonResponse.toString()
  }

  private fun getLevelsText(levels: Map<Int, String?>): String {
    val levelsText = StringBuilder("")
    for (level in 1..4) {
      val code = levels[level]
      if (code != null) {
        if (level > 1) {
          levelsText.append(",")
        }
        levelsText.append(getLevelText(level, code))
      }
    }

    return levelsText.toString()
  }

  private fun getLevelText(level: Int, code: String): String {
    return """
        {
          "level" : $level,
          "code" : "$code",
          "type" : "$level",
          "description" : "level $level"
        }
    """.trimIndent()
  }

  private fun getLevels(internalLocation: String?): Map<Int, String?> {
    val valuesMap = mutableMapOf<Int, String?>()

    internalLocation?.let {
      val values = it.split("-")
      valuesMap[1] = values.getOrNull(1)
      valuesMap[2] = values.getOrNull(2)
      valuesMap[3] = values.getOrNull(3)
      valuesMap[4] = values.getOrNull(4)
    }

    return valuesMap.toMap()
  }
}
