package uk.gov.justice.digital.hmpps.visitscheduler.helper

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.visitscheduler.config.ApplicationValidationErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.PHONE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationValidationErrorCodes.APPLICATION_INVALID_NO_SLOT_CAPACITY
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestEventAuditRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryClientService
import java.time.format.DateTimeFormatter

@Transactional(readOnly = true)
@Component
class AssertHelper {
  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Autowired
  private lateinit var eventAuditRepository: TestEventAuditRepository

  @Autowired
  private lateinit var telemetryClient: TelemetryClient

  fun assertCancelledVisitTelemetryClientEvents(
    cancelledVisit: VisitDto,
    type: TelemetryVisitEvents,
  ) {
    val eventAudit = eventAuditRepository.findLastEventByBookingReference(cancelledVisit.reference)
    val visitors = cancelledVisit.visitors.map { visitor -> TelemetryClientService.VisitorDetails(visitor.nomisPersonId.toString(), null) }

    verify(telemetryClient).trackEvent(
      eq("visit-cancelled"),
      check {
        assertThat(it["reference"]).isEqualTo(cancelledVisit.reference)
        assertThat(it["applicationReference"]).isEqualTo(cancelledVisit.applicationReference)
        assertThat(it["prisonerId"]).isEqualTo(cancelledVisit.prisonerId)
        assertThat(it["prisonId"]).isEqualTo(cancelledVisit.prisonCode)
        assertThat(it["visitStatus"]).isEqualTo(cancelledVisit.visitStatus.name)
        assertThat(it["visitSubStatus"]).isEqualTo(cancelledVisit.visitSubStatus.name)
        assertThat(it["visitRestriction"]).isEqualTo(cancelledVisit.visitRestriction.name)
        assertThat(it["visitStart"]).isEqualTo(cancelledVisit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitEnd"]).isEqualTo(cancelledVisit.endTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitType"]).isEqualTo(cancelledVisit.visitType.name)
        assertThat(it["visitRoom"]).isEqualTo(cancelledVisit.visitRoom)
        assertThat(it["hasPhoneNumber"]).isEqualTo(((cancelledVisit.visitContact.telephone != null).toString()))
        assertThat(it["hasEmail"]).isEqualTo(((cancelledVisit.visitContact.email != null).toString()))
        assertThat(it["totalVisitors"]).isEqualTo(cancelledVisit.visitors.size.toString())
        assertThat(it["visitors"]).isEqualTo(objectMapper.writeValueAsString(visitors))
        eventAudit.actionedBy.userName?.let { value ->
          assertThat(it["actionedBy"]).isEqualTo(value)
        }
        assertThat(it["source"]).isEqualTo(eventAudit.actionedBy.userType.name)
        assertThat(it["applicationMethodType"]).isEqualTo(eventAudit.applicationMethodType.name)
        assertThat(it["outcomeStatus"]).isEqualTo(cancelledVisit.outcomeStatus?.name)
      },
      isNull(),
    )

    val actionedBy = if (eventAudit.actionedBy.userType == STAFF) eventAudit.actionedBy.userName else eventAudit.actionedBy.bookerReference
    val eventsMap = mutableMapOf(
      "reference" to cancelledVisit.reference,
      "applicationReference" to cancelledVisit.applicationReference,
      "prisonerId" to cancelledVisit.prisonerId,
      "prisonId" to cancelledVisit.prisonCode,
      "visitStatus" to cancelledVisit.visitStatus.name,
      "visitSubStatus" to cancelledVisit.visitSubStatus.name,
      "visitRestriction" to cancelledVisit.visitRestriction.name,
      "visitStart" to cancelledVisit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME),
      "visitEnd" to cancelledVisit.endTimestamp.format(DateTimeFormatter.ISO_DATE_TIME),
      "visitType" to cancelledVisit.visitType.name,
      "visitRoom" to cancelledVisit.visitRoom,
      "hasPhoneNumber" to ((cancelledVisit.visitContact.telephone != null).toString()),
      "hasEmail" to ((cancelledVisit.visitContact.email != null).toString()),
      "totalVisitors" to (cancelledVisit.visitors.size.toString()),
      "visitors" to objectMapper.writeValueAsString(visitors),
      "actionedBy" to actionedBy,
      "source" to eventAudit.actionedBy.userType.name,
      "applicationMethodType" to eventAudit.applicationMethodType.name,
      "outcomeStatus" to cancelledVisit.outcomeStatus!!.name,
    )

    verify(telemetryClient, times(1)).trackEvent(type.eventName, eventsMap, null)
  }

  fun assertVisitCancellation(
    cancelledVisit: VisitDto,
    expectedOutcomeStatus: OutcomeStatus = OutcomeStatus.CANCELLATION,
    cancelledBy: String,
    applicationMethodType: ApplicationMethodType? = ApplicationMethodType.PHONE,
    userType: UserType = STAFF,
    visitSubStatus: VisitSubStatus? = VisitSubStatus.CANCELLED,
  ) {
    val eventAudit = eventAuditRepository.findLastEventByBookingReference(cancelledVisit.reference)

    if (userType == STAFF) {
      Assertions.assertThat(eventAudit.actionedBy.userName).isEqualTo(cancelledBy)
    } else {
      Assertions.assertThat(eventAudit.actionedBy.bookerReference).isEqualTo(cancelledBy)
    }

    if (visitSubStatus == VisitSubStatus.WITHDRAWN) {
      Assertions.assertThat(eventAudit.type).isEqualTo(EventAuditType.REQUESTED_VISIT_WITHDRAWN)
    } else {
      Assertions.assertThat(eventAudit.type).isEqualTo(EventAuditType.CANCELLED_VISIT)
    }
    Assertions.assertThat(eventAudit.actionedBy.userType).isEqualTo(userType)
    Assertions.assertThat(eventAudit.applicationMethodType).isEqualTo(applicationMethodType)
    Assertions.assertThat(eventAudit.bookingReference).isEqualTo(cancelledVisit.reference)
    Assertions.assertThat(eventAudit.sessionTemplateReference).isEqualTo(cancelledVisit.sessionTemplateReference)
    Assertions.assertThat(eventAudit.applicationReference).isEqualTo(cancelledVisit.applicationReference)
    Assertions.assertThat(cancelledVisit.visitStatus).isEqualTo(VisitStatus.CANCELLED)
    Assertions.assertThat(cancelledVisit.visitSubStatus).isEqualTo(visitSubStatus)
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
    responseSpec.expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value())

    val validationErrorResponse = getApplicationValidationErrorResponse(responseSpec)
    Assertions.assertThat(validationErrorResponse.validationErrors).contains(
      APPLICATION_INVALID_NO_SLOT_CAPACITY,
    )
  }

  fun assertMigrateCancelVisitEventAudit(visitReference: String) {
    val eventAuditList = eventAuditRepository.findAllByBookingReference(visitReference)

    assertThat(eventAuditList).hasSize(1)
    assertThat(eventAuditList[0].actionedBy.userName).isEqualTo("user-2")
    assertThat(eventAuditList[0].type).isEqualTo(EventAuditType.CANCELLED_VISIT)
    assertThat(eventAuditList[0].actionedBy.userType).isEqualTo(STAFF)
  }

  fun assertPairedVisitsNotificationEventAudit(eventAuditType: EventAuditType, visitReference: String, text: String) {
    val eventAudit = this.eventAuditRepository.findLastEventByBookingReference(visitReference)

    assertThat(eventAudit.type).isEqualTo(eventAuditType)
    assertThat(eventAudit.actionedBy.userType).isEqualTo(UserType.SYSTEM)
    assertThat(eventAudit.applicationMethodType).isEqualTo(ApplicationMethodType.NOT_APPLICABLE)
    assertThat(eventAudit.bookingReference).isEqualTo(visitReference)
    assertThat(eventAudit.sessionTemplateReference).isNull()
    assertThat(eventAudit.applicationReference).isNull()
    assertThat(eventAudit.actionedBy.id).isNotNull()
    assertThat(eventAudit.text).isEqualTo(text)
  }

  fun assertCancelVisitTelemetryClientEvents(
    cancelledVisit: VisitDto,
    type: TelemetryVisitEvents,
  ) {
    val eventAudit = this.eventAuditRepository.findLastEventByBookingReference(cancelledVisit.reference)
    val visitors = cancelledVisit.visitors.map { visitor -> TelemetryClientService.VisitorDetails(visitor.nomisPersonId.toString(), null) }

    verify(telemetryClient).trackEvent(
      eq("visit-cancelled"),
      check {
        assertThat(it["reference"]).isEqualTo(cancelledVisit.reference)
        assertThat(it["applicationReference"]).isEqualTo(cancelledVisit.applicationReference)
        assertThat(it["prisonerId"]).isEqualTo(cancelledVisit.prisonerId)
        assertThat(it["prisonId"]).isEqualTo(cancelledVisit.prisonCode)
        assertThat(it["visitStatus"]).isEqualTo(cancelledVisit.visitStatus.name)
        assertThat(it["visitSubStatus"]).isEqualTo(cancelledVisit.visitSubStatus.name)
        assertThat(it["visitRestriction"]).isEqualTo(cancelledVisit.visitRestriction.name)
        assertThat(it["visitStart"]).isEqualTo(cancelledVisit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitEnd"]).isEqualTo(cancelledVisit.endTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitType"]).isEqualTo(cancelledVisit.visitType.name)
        assertThat(it["visitRoom"]).isEqualTo(cancelledVisit.visitRoom)
        assertThat(it["hasPhoneNumber"]).isEqualTo(((cancelledVisit.visitContact.telephone != null).toString()))
        assertThat(it["hasEmail"]).isEqualTo(((cancelledVisit.visitContact.email != null).toString()))
        assertThat(it["totalVisitors"]).isEqualTo(cancelledVisit.visitors.size.toString())
        assertThat(it["visitors"]).isEqualTo(objectMapper.writeValueAsString(visitors))

        eventAudit.actionedBy.userName?.let { value ->
          assertThat(it["actionedBy"]).isEqualTo(value)
        }
        assertThat(it["source"]).isEqualTo(eventAudit.actionedBy.userType.name)
        assertThat(it["applicationMethodType"]).isEqualTo(eventAudit.applicationMethodType.name)
        assertThat(it["outcomeStatus"]).isEqualTo(cancelledVisit.outcomeStatus?.name)
      },
      isNull(),
    )

    val actionedBy = if (eventAudit.actionedBy.userType == STAFF) eventAudit.actionedBy.userName else eventAudit.actionedBy.bookerReference
    val eventsMap = mutableMapOf(
      "reference" to cancelledVisit.reference,
      "applicationReference" to cancelledVisit.applicationReference,
      "prisonerId" to cancelledVisit.prisonerId,
      "prisonId" to cancelledVisit.prisonCode,
      "visitStatus" to cancelledVisit.visitStatus.name,
      "visitSubStatus" to cancelledVisit.visitSubStatus.name,
      "visitRestriction" to cancelledVisit.visitRestriction.name,
      "visitStart" to cancelledVisit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME),
      "visitEnd" to cancelledVisit.endTimestamp.format(DateTimeFormatter.ISO_DATE_TIME),
      "visitType" to cancelledVisit.visitType.name,
      "visitRoom" to cancelledVisit.visitRoom,
      "hasPhoneNumber" to ((cancelledVisit.visitContact.telephone != null).toString()),
      "hasEmail" to ((cancelledVisit.visitContact.email != null).toString()),
      "totalVisitors" to (cancelledVisit.visitors.size.toString()),
      "visitors" to objectMapper.writeValueAsString(visitors),
      "actionedBy" to actionedBy,
      "source" to eventAudit.actionedBy.userType.name,
      "applicationMethodType" to eventAudit.applicationMethodType.name,
      "outcomeStatus" to cancelledVisit.outcomeStatus!!.name,
    )

    verify(telemetryClient, times(1)).trackEvent(type.eventName, eventsMap, null)
  }

  fun assertCapacityError(
    responseSpec: ResponseSpec,
  ) {
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.developerMessage")
      .value<String> { it.contains(("Application can not be reserved because capacity has been exceeded for the slot")) }
  }

  private fun getApplicationValidationErrorResponse(responseSpec: ResponseSpec): ApplicationValidationErrorResponse = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, ApplicationValidationErrorResponse::class.java)
}
