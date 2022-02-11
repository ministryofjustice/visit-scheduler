package uk.gov.justice.digital.hmpps.visitscheduler.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.springframework.http.MediaType

class PrisonApiMockServer : WireMockServer(8091) {

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
}
