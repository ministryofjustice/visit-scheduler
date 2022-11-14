package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCancelVisit
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import java.time.LocalDateTime

@DisplayName("Cancellation days have been set as zero")
@TestPropertySource(properties = ["visit.cancel.day-limit=-2"])
class CancelNegativeDayLimitConfiguredTest(@Autowired private val objectMapper: ObjectMapper) : IntegrationTestBase() {
  @Value("\${visit.cancel.day-limit}")
  var visitCancellationDayLimit: Long = -7

  @Test
  fun `when cancel day limit configured as a negative value cancel future visit does not return error`() {
    val outcomeDto = OutcomeDto(
      OutcomeStatus.CANCELLATION,
      "No longer joining."
    )
    // Given
    val visitStart = LocalDateTime.now().plusDays(1)
    val visit = visitEntityHelper.create(visitStatus = BOOKED, visitStart = visitStart)

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), visit.reference, outcomeDto)

    // Then
    Assertions.assertThat(visitCancellationDayLimit).isEqualTo(-2)

    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    CancelVisitTest.assertVisitCancellation(visitCancelled, OutcomeStatus.CANCELLATION)
  }

  @Test
  fun `cancel expired visit before current time returns error when cancel day limit configured as a negative value`() {
    val outcomeDto = OutcomeDto(
      OutcomeStatus.CANCELLATION,
      "No longer joining."
    )
    // Given
    // visit has expired based on current date
    // as the configured limit is 0 - any cancellations before current time should be allowed
    val visitStart = LocalDateTime.now().minusMinutes(10)
    val expiredVisit = visitEntityHelper.create(visitStatus = BOOKED, visitStart = visitStart)

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), expiredVisit.reference, outcomeDto)

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").isEqualTo("Validation failure: trying to change / cancel an expired visit")
      .jsonPath("$.developerMessage").isEqualTo("Visit with booking reference - ${expiredVisit.reference} is in the past, it cannot be cancelled")
  }
}
