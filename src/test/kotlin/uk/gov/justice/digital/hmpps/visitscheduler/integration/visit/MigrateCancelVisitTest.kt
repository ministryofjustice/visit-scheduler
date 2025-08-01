package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.controller.migration.MIGRATE_CANCEL
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigratedCancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus.ACCEPTED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus.PRISONER_CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason.VISIT_CANCELLED_ON_NOMIS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.helper.VisitNotificationEventHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callMigrateCancelVisit
import uk.gov.justice.digital.hmpps.visitscheduler.helper.getMigrateCancelVisitUrl
import uk.gov.justice.digital.hmpps.visitscheduler.integration.migration.MigrationIntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitNotificationEventRepository
import java.time.LocalDate

@DisplayName("Tests for visit cancellations on NOMIS - POST $MIGRATE_CANCEL")
class MigrateCancelVisitTest : MigrationIntegrationTestBase() {
  @Autowired
  protected lateinit var visitNotificationEventHelper: VisitNotificationEventHelper

  @MockitoSpyBean
  private lateinit var visitNotificationEventRepositorySpy: VisitNotificationEventRepository

  @BeforeEach
  internal fun setUp() {
    prisonEntityHelper.create()
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS"))
  }

  @Test
  fun `Access forbidden when no role`() {
    // Given
    val authHttpHeaders = setAuthorisation(roles = listOf())
    val cancelVisitDto = MigratedCancelVisitDto(
      OutcomeDto(
        PRISONER_CANCELLED,
        "Prisoner got covid",
      ),
      actionedBy = "user-2",
    )

    // When
    val responseSpec = callMigrateCancelVisit(webTestClient, authHttpHeaders, "dummy-reference", cancelVisitDto)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `Unauthorised when no token`() {
    // Given
    val jsonBody = BodyInserters.fromValue(createMigrateVisitRequestDto())

    // When
    val responseSpec = webTestClient.put().uri(getMigrateCancelVisitUrl("dummy-reference"))
      .body(jsonBody)
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  @Test
  fun `cancel visit migrated by reference -  with outcome and outcome text`() {
    // Given

    val application = createApplicationAndSave(applicationStatus = ACCEPTED, sessionTemplate = sessionTemplateDefault)
    val visit = createVisitAndSave(visitStatus = BOOKED, visitSubStatus = VisitSubStatus.AUTO_APPROVED, application, sessionTemplateDefault)

    val cancelVisitDto = MigratedCancelVisitDto(
      OutcomeDto(
        PRISONER_CANCELLED,
        "Prisoner got covid",
      ),
      actionedBy = "user-2",
    )
    val reference = visit.reference

    // When
    val responseSpec = callMigrateCancelVisit(webTestClient, roleVisitSchedulerHttpHeaders, reference, cancelVisitDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertThat(visitCancelled.visitNotes.size).isEqualTo(1)
    assertThat(visitCancelled.visitNotes[0].text).isEqualTo("Prisoner got covid")
    assertHelper.assertVisitCancellation(visitCancelled, cancelledBy = cancelVisitDto.actionedBy, applicationMethodType = NOT_KNOWN, expectedOutcomeStatus = PRISONER_CANCELLED)

    assertTelemetryClientEvents(visitCancelled, TelemetryVisitEvents.CANCELLED_VISIT_MIGRATED_EVENT)
    assertCancelledDomainEvent(visitCancelled)

    val eventAuditList = eventAuditRepository.findAllByBookingReference(visit.reference)
    assertThat(eventAuditList).hasSize(1)
    assertThat(eventAuditList[0].actionedBy.userName).isEqualTo("user-2")
    assertThat(eventAuditList[0].type).isEqualTo(EventAuditType.CANCELLED_VISIT)
    assertThat(eventAuditList[0].actionedBy.userType).isEqualTo(STAFF)
  }

  @Test
  fun `when visit cancelled that has notification events then notification events are deleted and an unflag event is sent`() {
    // Given
    val application = createApplicationAndSave(applicationStatus = ACCEPTED, sessionTemplate = sessionTemplateDefault)
    val visit = createVisitAndSave(visitStatus = BOOKED, visitSubStatus = VisitSubStatus.AUTO_APPROVED, application, sessionTemplateDefault)
    visitNotificationEventHelper.create(visit, NotificationEventType.NON_ASSOCIATION_EVENT)
    visitNotificationEventHelper.create(visit, NotificationEventType.PRISONER_RESTRICTION_CHANGE_EVENT)
    visitNotificationEventHelper.create(visit, NotificationEventType.PRISONER_RELEASED_EVENT)

    var visitNotifications = visitNotificationEventHelper.getVisitNotifications(visit.reference)
    assertThat(visitNotifications.size).isEqualTo(3)

    val cancelVisitDto = MigratedCancelVisitDto(
      OutcomeDto(
        PRISONER_CANCELLED,
        "Prisoner got covid",
      ),
      actionedBy = "user-2",
    )
    val reference = visit.reference

    // When
    val responseSpec = callMigrateCancelVisit(webTestClient, roleVisitSchedulerHttpHeaders, reference, cancelVisitDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertThat(visitCancelled.visitNotes.size).isEqualTo(1)
    assertThat(visitCancelled.visitNotes[0].text).isEqualTo("Prisoner got covid")
    assertHelper.assertVisitCancellation(visitCancelled, cancelledBy = cancelVisitDto.actionedBy, applicationMethodType = NOT_KNOWN, expectedOutcomeStatus = PRISONER_CANCELLED)

    assertTelemetryClientEvents(visitCancelled, TelemetryVisitEvents.CANCELLED_VISIT_MIGRATED_EVENT)
    assertCancelledDomainEvent(visitCancelled)

    val eventAuditList = eventAuditRepository.findAllByBookingReference(visit.reference)
    assertThat(eventAuditList).hasSize(1)
    assertThat(eventAuditList[0].actionedBy.userName).isEqualTo("user-2")
    assertThat(eventAuditList[0].type).isEqualTo(EventAuditType.CANCELLED_VISIT)
    assertThat(eventAuditList[0].actionedBy.userType).isEqualTo(STAFF)

    visitNotifications = visitNotificationEventHelper.getVisitNotifications(visit.reference)
    verify(visitNotificationEventRepositorySpy, times(1)).deleteVisitNotificationEventByVisitReference(eq(visit.reference))

    assertThat(visitNotifications.size).isEqualTo(0)
    assertUnFlagEvent(visitCancelled)
  }

  @Test
  fun `when visit cancelled after visit start that has notification events then notification events are deleted and an unflag event is sent`() {
    // Given
    // past dated visit - for yesterday
    val application = createApplicationAndSave(applicationStatus = ACCEPTED, sessionTemplate = sessionTemplateDefault, slotDate = LocalDate.now().minusDays(1))
    val visit = createVisitAndSave(visitStatus = BOOKED, visitSubStatus = VisitSubStatus.AUTO_APPROVED, application, sessionTemplateDefault)
    visitNotificationEventHelper.create(visit, NotificationEventType.NON_ASSOCIATION_EVENT)
    visitNotificationEventHelper.create(visit, NotificationEventType.PRISONER_RESTRICTION_CHANGE_EVENT)
    visitNotificationEventHelper.create(visit, NotificationEventType.PRISONER_RELEASED_EVENT)

    var visitNotifications = visitNotificationEventHelper.getVisitNotifications(visit.reference)
    assertThat(visitNotifications.size).isEqualTo(3)

    // visit cancelled today
    val cancelVisitDto = MigratedCancelVisitDto(
      OutcomeDto(
        PRISONER_CANCELLED,
        "Prisoner got covid",
      ),
      actionedBy = "user-2",
    )
    val reference = visit.reference

    // When
    val responseSpec = callMigrateCancelVisit(webTestClient, roleVisitSchedulerHttpHeaders, reference, cancelVisitDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertThat(visitCancelled.visitNotes.size).isEqualTo(1)
    assertThat(visitCancelled.visitNotes[0].text).isEqualTo("Prisoner got covid")
    assertHelper.assertVisitCancellation(visitCancelled, cancelledBy = cancelVisitDto.actionedBy, applicationMethodType = NOT_KNOWN, expectedOutcomeStatus = PRISONER_CANCELLED)

    assertTelemetryClientEvents(visitCancelled, TelemetryVisitEvents.CANCELLED_VISIT_MIGRATED_EVENT)
    assertCancelledDomainEvent(visitCancelled)

    val eventAuditList = eventAuditRepository.findAllByBookingReference(visit.reference)
    assertThat(eventAuditList).hasSize(1)
    assertThat(eventAuditList[0].actionedBy.userName).isEqualTo("user-2")
    assertThat(eventAuditList[0].type).isEqualTo(EventAuditType.CANCELLED_VISIT)
    assertThat(eventAuditList[0].actionedBy.userType).isEqualTo(STAFF)

    visitNotifications = visitNotificationEventHelper.getVisitNotifications(visit.reference)
    verify(visitNotificationEventRepositorySpy, times(1)).deleteVisitNotificationEventByVisitReference(eq(visit.reference))

    assertThat(visitNotifications.size).isEqualTo(0)

    // assert that the visit is still unflagged.
    assertUnFlagEvent(visitCancelled)
  }

  private fun assertCancelledDomainEvent(
    cancelledVisit: VisitDto,
  ) {
    verify(telemetryClient).trackEvent(
      eq("prison-visit.cancelled-domain-event"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(cancelledVisit.reference)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("prison-visit.cancelled-domain-event"), any(), isNull())
  }

  private fun assertUnFlagEvent(
    cancelledVisit: VisitDto,
  ) {
    verify(telemetryClient).trackEvent(
      eq("unflagged-visit-event"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(cancelledVisit.reference)
        assertThat(it["reason"]).isEqualTo(VISIT_CANCELLED_ON_NOMIS.desc)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
  }
}
