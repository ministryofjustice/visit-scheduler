package uk.gov.justice.digital.hmpps.visitscheduler.helper

import com.fasterxml.jackson.databind.ObjectMapper
import io.netty.handler.codec.http.HttpResponseStatus.UNPROCESSABLE_ENTITY
import org.assertj.core.api.Assertions
import org.hamcrest.Matchers
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.config.ApplicationValidationErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationValidationErrorCodes.APPLICATION_INVALID_NO_SLOT_CAPACITY
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestEventAuditRepository

@Component
class AssertHelper {
  @Autowired
  protected lateinit var objectMapper: ObjectMapper

  @Autowired
  protected lateinit var eventAuditRepository: TestEventAuditRepository

  fun assertVisitCancellation(
    cancelledVisit: VisitDto,
    expectedOutcomeStatus: OutcomeStatus = OutcomeStatus.CANCELLATION,
    cancelledBy: String,
    applicationMethodType: ApplicationMethodType? = ApplicationMethodType.PHONE,
  ) {
    val eventAudit = this.eventAuditRepository.findLastEventByBookingReference(cancelledVisit.reference)

    Assertions.assertThat(eventAudit.type).isEqualTo(EventAuditType.CANCELLED_VISIT)
    Assertions.assertThat(eventAudit.actionedBy.userName).isEqualTo(cancelledBy)
    Assertions.assertThat(eventAudit.actionedBy.userType).isEqualTo(STAFF)
    Assertions.assertThat(eventAudit.applicationMethodType).isEqualTo(applicationMethodType)
    Assertions.assertThat(eventAudit.bookingReference).isEqualTo(cancelledVisit.reference)
    Assertions.assertThat(eventAudit.sessionTemplateReference).isEqualTo(cancelledVisit.sessionTemplateReference)
    Assertions.assertThat(eventAudit.applicationReference).isEqualTo(cancelledVisit.applicationReference)

    Assertions.assertThat(cancelledVisit.visitStatus).isEqualTo(VisitStatus.CANCELLED)
    Assertions.assertThat(cancelledVisit.outcomeStatus).isEqualTo(expectedOutcomeStatus)
  }

  fun assertIgnoredVisit(
    visit: VisitDto,
    actionedBy: String,
    userType: UserType,
    reason: String,
  ) {
    val eventAudit = this.eventAuditRepository.findLastEventByBookingReference(visit.reference)

    Assertions.assertThat(eventAudit.type).isEqualTo(EventAuditType.IGNORE_VISIT_NOTIFICATIONS_EVENT)
    Assertions.assertThat(eventAudit.actionedBy.userName).isEqualTo(actionedBy)
    Assertions.assertThat(eventAudit.actionedBy.userType).isEqualTo(userType)
    Assertions.assertThat(eventAudit.applicationMethodType).isEqualTo(ApplicationMethodType.NOT_APPLICABLE)
    Assertions.assertThat(eventAudit.bookingReference).isEqualTo(visit.reference)
    Assertions.assertThat(eventAudit.sessionTemplateReference).isEqualTo(visit.sessionTemplateReference)
    Assertions.assertThat(eventAudit.applicationReference).isEqualTo(visit.applicationReference)
    Assertions.assertThat(eventAudit.text).isEqualTo(reason)

    Assertions.assertThat(visit.visitStatus).isEqualTo(VisitStatus.BOOKED)
  }

  fun assertBookingCapacityError(
    responseSpec: ResponseSpec,
  ) {
    responseSpec.expectStatus().isEqualTo(UNPROCESSABLE_ENTITY.code())

    val validationErrorResponse = getApplicationValidationErrorResponse(responseSpec)
    Assertions.assertThat(validationErrorResponse.validationErrors.size).isEqualTo(1)
    Assertions.assertThat(validationErrorResponse.validationErrors).contains(
      APPLICATION_INVALID_NO_SLOT_CAPACITY,
    )
  }

  fun assertCapacityError(
    responseSpec: ResponseSpec,
  ) {
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.developerMessage")
      .value(Matchers.containsString("Application can not be reserved because capacity has been exceeded for the slot"))
  }

  private fun getApplicationValidationErrorResponse(responseSpec: ResponseSpec): ApplicationValidationErrorResponse =
    objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, ApplicationValidationErrorResponse::class.java)
}
