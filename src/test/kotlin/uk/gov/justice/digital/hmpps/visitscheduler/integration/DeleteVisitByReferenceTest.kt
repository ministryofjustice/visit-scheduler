package uk.gov.justice.digital.hmpps.visitscheduler.integration

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitContactCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitSupportCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitVisitorCreator
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDateTime

@DisplayName("DELETE /visits/{reference}")
class DeleteVisitByReferenceTest : IntegrationTestBase() {

  private val visitTime: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30, 44)

  @Autowired
  private lateinit var visitRepository: VisitRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @AfterEach
  internal fun deleteAllVisits() = visitDeleter(visitRepository)

  @Test
  fun `delete visit by reference`() {

    // Given

    val visitCC = visitCreator(visitRepository)
      .withPrisonerId("FF0000CC")
      .withVisitStart(visitTime.plusDays(2))
      .withVisitEnd(visitTime.plusDays(2).plusHours(1))
      .withPrisonId("LEI")
      .save()

    visitContactCreator(visit = visitCC, name = "Jane Doe", phone = "01234 098765")
    visitVisitorCreator(visit = visitCC, nomisPersonId = 123L, visitContact = true)
    visitSupportCreator(visit = visitCC, name = "OTHER", details = "Some Text")
    visitRepository.saveAndFlush(visitCC)

    // When
    val responseSpec = callDeleteVisitEndPoint("/visits/${visitCC.reference}")

    // Then
    responseSpec.expectStatus().isOk

    val visit = visitRepository.findByReference(visitCC.reference)
    Assertions.assertThat(visit).isNull()

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
    // Given
    val reference = "123456"

    // When
    val responseSpec = callDeleteVisitEndPoint("/visits/$reference")

    // Then
    responseSpec.expectStatus().isOk

    verify(telemetryClient, times(0)).trackEvent(eq("visit-scheduler-prison-visit-deleted"), any(), isNull())
  }

  fun callDeleteVisitEndPoint(url: String, roles: List<String> = listOf("ROLE_VISIT_SCHEDULER")): ResponseSpec {
    return webTestClient.delete().uri(url)
      .headers(setAuthorisation(roles = roles))
      .exchange()
  }
}
