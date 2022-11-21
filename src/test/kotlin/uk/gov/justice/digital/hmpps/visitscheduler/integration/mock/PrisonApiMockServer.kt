package uk.gov.justice.digital.hmpps.visitscheduler.integration.mock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.springframework.http.MediaType

class PrisonApiMockServer : WireMockServer(8092) {

  fun stubGetOffenderNonAssociation(offenderNo: String, nonAssociationId: String, effectiveDate: String, expiryDate: String? = null) {
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

  fun stubGetPrisonerDetails(offenderNo: String, internalLocation: String?) {
    var unitCode1: String? = null
    var unitCode2: String? = null
    var unitCode3: String? = null
    var unitCode4: String? = null

    internalLocation?.let {
      val values = it.split("-")
      unitCode1 = values.getOrNull(1)
      unitCode2 = values.getOrNull(2)
      unitCode3 = values.getOrNull(3)
      unitCode4 = values.getOrNull(4)
    }

    stubFor(
      get("/api/prisoners/$offenderNo/full-status")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody(
              """
                {
                  "nomsId": "$offenderNo",
                  "unitCode1": "$unitCode1",
                  "unitCode2": "$unitCode2",
                  "unitCode3": "$unitCode3",
                  "unitCode4": "$unitCode4"
                }
              """.trimIndent()
            )
        )
    )
  }
}
