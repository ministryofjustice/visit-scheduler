package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCancelVisit
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import java.time.LocalDateTime

@DisplayName("Cancellation days have been set as zero")
@TestPropertySource(properties = ["visit.cancel.day-limit=0"])
class CancelZeroDayLimitConfiguredTest : IntegrationTestBase() {
  @Value("\${visit.cancel.day-limit}")
  var visitCancellationDayLimit: Long = 28

  @Test
  fun `when cancel day limit configured as zero cancel future visit does not return error`() {
    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        OutcomeStatus.CANCELLATION,
        "No longer joining.",
      ),
      "user-1",
      NOT_KNOWN,
    )
    // Given
    val now = LocalDateTime.now().plusDays(1)
    val slotDate = now.toLocalDate()
    val visitStart = now.toLocalTime()
    val visit = visitEntityHelper.create(visitStatus = BOOKED, slotDate = slotDate, visitStart = visitStart, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), visit.reference, cancelVisitDto)

    // Then
    Assertions.assertThat(visitCancellationDayLimit).isEqualTo(0)

    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertHelper.assertVisitCancellation(visitCancelled, OutcomeStatus.CANCELLATION, cancelVisitDto.actionedBy, NOT_KNOWN)
  }
}
