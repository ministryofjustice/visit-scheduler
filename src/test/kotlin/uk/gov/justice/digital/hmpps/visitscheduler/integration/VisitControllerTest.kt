package uk.gov.justice.digital.hmpps.visitscheduler.integration

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.SpyBean
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitContactCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitSupportCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitVisitorCreator
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDateTime

class VisitControllerTest : IntegrationTestBase() {
  @Autowired
  private lateinit var visitRepository: VisitRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @AfterEach
  internal fun deleteAllVisits() = visitDeleter(visitRepository)

  @DisplayName("GET /visits/{reference}")
  @Nested
  inner class GetVisitsByReference {
    @BeforeEach
    internal fun createVisits() {
      visitCreator(visitRepository)
        .withPrisonerId("FF0000AA")
        .withVisitStart(visitTime)
        .withPrisonId("MDI")
        .save()
    }
    @Test
    fun `get visit by reference`() {
      val createdVisit = visitCreator(visitRepository)
        .withPrisonerId("FF0000AA")
        .withVisitStart(visitTime)
        .save()

      webTestClient.get().uri("/visits/${createdVisit.reference}")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.reference").isEqualTo(createdVisit.reference)
    }

    @Test
    fun `get visit by reference - not found`() {
      webTestClient.get().uri("/visits/12345")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isNotFound
    }
  }

  @DisplayName("DELETE /visits/{reference}")
  @Nested
  inner class DeleteVisitByReference {
    @Test
    fun `delete visit by reference`() {
      val visitCC = visitCreator(visitRepository)
        .withPrisonerId("FF0000CC")
        .withVisitStart(visitTime.plusDays(2))
        .withVisitEnd(visitTime.plusDays(2).plusHours(1))
        .withPrisonId("LEI")
        .save()
      visitContactCreator(visit = visitCC, name = "Jane Doe", phone = "01234 098765")
      visitVisitorCreator(visit = visitCC, nomisPersonId = 123L)
      visitSupportCreator(visit = visitCC, name = "OTHER", details = "Some Text")
      visitRepository.saveAndFlush(visitCC)

      webTestClient.delete().uri("/visits/${visitCC.reference}")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk

      webTestClient.get().uri("/visits/${visitCC.reference}")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isNotFound

      verify(telemetryClient).trackEvent(
        eq("visit-scheduler-prison-visit-deleted"),
        org.mockito.kotlin.check {
          Assertions.assertThat(it["reference"]).isEqualTo(visitCC.reference)
        },
        isNull()
      )
      verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-deleted"), any(), isNull())
    }

    @Test
    fun `delete visit by reference NOT Found`() {
      webTestClient.delete().uri("/visits/123456")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk

      verify(telemetryClient, times(0)).trackEvent(eq("visit-scheduler-prison-visit-deleted"), any(), isNull())
    }
  }

  companion object {
    val visitTime: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30, 44)
  }
}
