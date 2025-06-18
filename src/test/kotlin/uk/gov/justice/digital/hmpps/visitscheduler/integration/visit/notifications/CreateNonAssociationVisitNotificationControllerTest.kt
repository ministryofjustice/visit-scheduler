package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.notifications

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.http.HttpHeaders
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_NON_ASSOCIATION_CHANGE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.NON_ASSOCIATION_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NonAssociationDomainEventType.NON_ASSOCIATION_CREATED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventAttributeType.PAIRED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NonAssociationChangedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.VisitNotificationEventAttributeDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatNonAssociationHasChanged
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitNotificationEventService
import java.time.LocalDate
import java.time.LocalTime

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_NOTIFICATION_NON_ASSOCIATION_CHANGE_PATH NON_ASSOCIATION_CREATED")
class CreateNonAssociationVisitNotificationControllerTest : NotificationTestBase() {

  private val nonAssociationDomainEventType = NON_ASSOCIATION_CREATED

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val primaryPrisonerId = "AA11BCC"
  val secondaryPrisonerId = "XX11YZZ"
  val prisonCode = "ABC"

  lateinit var prison1: Prison
  lateinit var sessionTemplate1: SessionTemplate

  @MockitoSpyBean
  lateinit var visitNotificationEventServiceSpy: VisitNotificationEventService

  @BeforeEach
  internal fun setUp() {
    prison1 = prisonEntityHelper.create(prisonCode = prisonCode)
    sessionTemplate1 = sessionTemplateEntityHelper.create(prison = prison1)
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
    prisonOffenderSearchMockServer.stubGetPrisonerByString(primaryPrisonerId, prisonCode, IncentiveLevel.ENHANCED)
  }

  @Test
  fun `when prisoners have two overlapped visits then visits with same date and prison are flagged and saved`() {
    // Given
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)

    val primaryVisit = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    eventAuditEntityHelper.create(primaryVisit)

    val secondaryVisit = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(secondaryVisit)

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    assertFlaggedVisitEvent(listOf(primaryVisit, secondaryVisit), NotificationEventType.NON_ASSOCIATION_EVENT)
    verify(telemetryClient, times(2)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(2)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(2)
    assertNotificationEvent(visitNotifications[0], primaryVisit.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, secondaryVisit.reference)))
    assertNotificationEvent(visitNotifications[1], secondaryVisit.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, primaryVisit.reference)))

    val auditEvents = testEventAuditRepository.getAuditByType(NON_ASSOCIATION_EVENT)
    assertThat(auditEvents).hasSize(2)
    with(auditEvents[0]) {
      assertThat(actionedBy.userName).isNull()
      assertThat(bookingReference).isEqualTo(primaryVisit.reference)
      assertThat(applicationReference).isEqualTo(primaryVisit.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(primaryVisit.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(NON_ASSOCIATION_EVENT)
      assertThat(applicationMethodType).isEqualTo(NOT_KNOWN)
      assertThat(actionedBy.userType).isEqualTo(UserType.SYSTEM)
    }
    with(auditEvents[1]) {
      assertThat(actionedBy.userName).isNull()
      assertThat(bookingReference).isEqualTo(secondaryVisit.reference)
      assertThat(applicationReference).isEqualTo(secondaryVisit.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(secondaryVisit.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(NON_ASSOCIATION_EVENT)
      assertThat(applicationMethodType).isEqualTo(NOT_KNOWN)
      assertThat(actionedBy.userType).isEqualTo(UserType.SYSTEM)
    }
  }

  @Test
  fun `when one prisoner has two visits the same day that overlap's with another then visits with same prisoner id are not grouped`() {
    // Given
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)

    val primaryVisit = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(primaryVisit)

    val primaryVisit2 = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(primaryVisit2)

    val secondaryVisit = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(secondaryVisit)

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk

    assertFlaggedVisitEvent(listOf(primaryVisit, primaryVisit2, secondaryVisit), NotificationEventType.NON_ASSOCIATION_EVENT)
    verify(telemetryClient, times(3)).trackEvent(eq("flagged-visit-event"), any(), isNull())

    verify(visitNotificationEventRepository, times(4)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(4)
    assertNotificationEvent(visitNotifications[0], primaryVisit.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, secondaryVisit.reference)))
    assertNotificationEvent(visitNotifications[1], secondaryVisit.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, primaryVisit.reference)))
    assertNotificationEvent(visitNotifications[2], primaryVisit2.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, secondaryVisit.reference)))
    assertNotificationEvent(visitNotifications[3], secondaryVisit.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, primaryVisit2.reference)))
  }

  @Test
  fun `when prisoners have more than two overlapped visits then visits with same date and prison are flagged and saved`() {
    // Given
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)

    val primaryVisit1 = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(primaryVisit1)

    val primaryVisit2 = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(primaryVisit2)

    // visit does not overlap
    createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(3),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    val secondaryVisit1 = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(secondaryVisit1)

    val secondaryVisit2 = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(secondaryVisit2)

    // visit does not overlap
    createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(4),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    assertFlaggedVisitEvent(listOf(primaryVisit1, primaryVisit2, secondaryVisit1, secondaryVisit2), NotificationEventType.NON_ASSOCIATION_EVENT)
    verify(telemetryClient, times(4)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(4)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(4)
    assertNotificationEvent(visitNotifications[0], primaryVisit1.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, secondaryVisit1.reference)))
    assertNotificationEvent(visitNotifications[1], secondaryVisit1.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, primaryVisit1.reference)))
    assertNotificationEvent(visitNotifications[2], primaryVisit2.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, secondaryVisit2.reference)))
    assertNotificationEvent(visitNotifications[3], secondaryVisit2.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, primaryVisit2.reference)))
    assertThat(testEventAuditRepository.getAuditCount(NON_ASSOCIATION_EVENT)).isEqualTo(4)
  }

  @Test
  fun `when one prisoner has two visits on the same day then visits with same date and prison are flagged and saved`() {
    // Given
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)
    val sessionTemplate2 = sessionTemplateEntityHelper.create(prison = prison1, startTime = LocalTime.of(16, 0), endTime = LocalTime.of(17, 0))

    val primaryVisit1 = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(primaryVisit1)

    val primaryVisit2 = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(primaryVisit2)

    // visit does not overlap
    createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(3),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    val secondaryVisit1 = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(secondaryVisit1)

    val secondaryVisit2 = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(secondaryVisit2)

    // prisoner 2 has a second visit on the day
    val secondaryVisit3 = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate2,
    )
    eventAuditEntityHelper.create(secondaryVisit3)

    // visit does not overlap
    createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(4),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    assertFlaggedVisitEvent(listOf(primaryVisit1, primaryVisit2, secondaryVisit1, secondaryVisit2, secondaryVisit3), NotificationEventType.NON_ASSOCIATION_EVENT)
    verify(telemetryClient, times(5)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(6)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(6)
    assertNotificationEvent(visitNotifications[0], primaryVisit1.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, secondaryVisit1.reference)))
    assertNotificationEvent(visitNotifications[1], secondaryVisit1.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, primaryVisit1.reference)))
    assertNotificationEvent(visitNotifications[2], primaryVisit2.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, secondaryVisit2.reference)))
    assertNotificationEvent(visitNotifications[3], secondaryVisit2.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, primaryVisit2.reference)))
    assertNotificationEvent(visitNotifications[4], primaryVisit2.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, secondaryVisit3.reference)))
    assertNotificationEvent(visitNotifications[5], secondaryVisit3.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, primaryVisit2.reference)))
    assertThat(testEventAuditRepository.getAuditCount(NON_ASSOCIATION_EVENT)).isEqualTo(6)
  }

  @Test
  fun `when both prisoners have two visits on the same day then visits with same date and prison are flagged and saved`() {
    // Given
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)
    val sessionTemplate2 = sessionTemplateEntityHelper.create(prison = prison1, startTime = LocalTime.of(16, 0), endTime = LocalTime.of(17, 0))

    val primaryVisit1 = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(primaryVisit1)

    val primaryVisit2 = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(primaryVisit2)

    // prisoner 2 has a second visit on the day
    val primaryVisit3 = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate2,
    )
    eventAuditEntityHelper.create(primaryVisit3)

    // visit does not overlap
    createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(3),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    val secondaryVisit1 = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(secondaryVisit1)

    val secondaryVisit2 = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(secondaryVisit2)

    // prisoner 2 has a second visit on the day
    val secondaryVisit3 = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate2,
    )
    eventAuditEntityHelper.create(secondaryVisit3)

    // visit does not overlap
    createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(4),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    assertFlaggedVisitEvent(listOf(primaryVisit1, primaryVisit2, primaryVisit3, secondaryVisit1, secondaryVisit2, secondaryVisit3), NotificationEventType.NON_ASSOCIATION_EVENT)
    verify(telemetryClient, times(6)).trackEvent(eq("flagged-visit-event"), any(), isNull())

    // primaryVisit1 - 1 event, secondaryVisit1 - 1 event, primaryVisit2, primaryVisit3, secondaryVisit2, secondaryVisit3 - 2 events each
    verify(visitNotificationEventRepository, times(10)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(10)
    assertNotificationEvent(visitNotifications[0], primaryVisit1.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, secondaryVisit1.reference)))
    assertNotificationEvent(visitNotifications[1], secondaryVisit1.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, primaryVisit1.reference)))
    assertNotificationEvent(visitNotifications[2], primaryVisit2.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, secondaryVisit2.reference)))
    assertNotificationEvent(visitNotifications[3], secondaryVisit2.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, primaryVisit2.reference)))
    assertNotificationEvent(visitNotifications[4], primaryVisit2.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, secondaryVisit3.reference)))
    assertNotificationEvent(visitNotifications[5], secondaryVisit3.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, primaryVisit2.reference)))
    assertNotificationEvent(visitNotifications[6], primaryVisit3.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, secondaryVisit2.reference)))
    assertNotificationEvent(visitNotifications[7], secondaryVisit2.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, primaryVisit3.reference)))
    assertNotificationEvent(visitNotifications[8], primaryVisit3.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, secondaryVisit3.reference)))
    assertNotificationEvent(visitNotifications[9], secondaryVisit3.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, primaryVisit3.reference)))
    assertThat(testEventAuditRepository.getAuditCount(NON_ASSOCIATION_EVENT)).isEqualTo(10)
  }

  @Test
  fun `when two events are consumed they have different references associated for each event`() {
    // Given
    val nonAssociationChangedNotification1 = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)

    val primaryPrisonerId2 = primaryPrisonerId + "Extp"
    val secondaryPrisonerId2 = secondaryPrisonerId + "Exts"

    prisonOffenderSearchMockServer.stubGetPrisonerByString(primaryPrisonerId2, prisonCode, IncentiveLevel.ENHANCED)
    val nonAssociationChangedNotification2 = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId2, secondaryPrisonerId2)
    val primaryVisit1 = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(primaryVisit1)

    val primaryVisit2 = createApplicationAndVisit(
      prisonerId = primaryPrisonerId2,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(primaryVisit2)

    val secondaryVisit1 = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(secondaryVisit1)

    val secondaryVisit2 = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId2,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(secondaryVisit2)

    // When
    callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification1)
    callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification2)

    // Then
    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(4)
    assertNotificationEvent(visitNotifications[0], primaryVisit1.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, secondaryVisit1.reference)))
    assertNotificationEvent(visitNotifications[1], secondaryVisit1.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, primaryVisit1.reference)))
    assertNotificationEvent(visitNotifications[2], primaryVisit2.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, secondaryVisit2.reference)))
    assertNotificationEvent(visitNotifications[3], secondaryVisit2.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, primaryVisit2.reference)))
    assertThat(testEventAuditRepository.getAuditCount(NON_ASSOCIATION_EVENT)).isEqualTo(4)
  }

  @Test
  fun `when same request events is submitted twice they have are only processed once`() {
    // Given
    // first notification event with prisoner1 and prisoner2
    val nonAssociationChangedNotification1 = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)

    val primaryVisit1 = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(primaryVisit1)

    val secondaryVisit1 = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(secondaryVisit1)

    // When
    callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification1)

    // same event sent again
    callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification1)

    // Then
    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(2)
    assertNotificationEvent(visitNotifications[0], primaryVisit1.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, secondaryVisit1.reference)))
    assertNotificationEvent(visitNotifications[1], secondaryVisit1.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, primaryVisit1.reference)))
    assertThat(testEventAuditRepository.getAuditCount(NON_ASSOCIATION_EVENT)).isEqualTo(2)
    verify(visitNotificationEventServiceSpy, times(2)).handleNonAssociations(any())

    // this should be twice even though the request was fired more than once
    verify(visitNotificationEventRepository, times(2)).saveAndFlush(any<VisitNotificationEvent>())
  }

  @Test
  fun `when multiple events are submitted for the same non-association they have are only processed once`() {
    // Given
    // first notification event with prisoner1 and prisoner2
    val nonAssociationChangedNotification1 = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)
    // second notification event with prisoner2 and prisoner1
    val nonAssociationChangedNotification2 = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, secondaryPrisonerId, primaryPrisonerId)

    val primaryVisit1 = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(primaryVisit1)

    val secondaryVisit1 = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(secondaryVisit1)

    // When
    callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification1)

    // called again with same prisoner Ids
    callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification2)

    // Then
    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(2)
    assertNotificationEvent(visitNotifications[0], primaryVisit1.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, secondaryVisit1.reference)))
    assertNotificationEvent(visitNotifications[1], secondaryVisit1.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, primaryVisit1.reference)))
    assertThat(testEventAuditRepository.getAuditCount(NON_ASSOCIATION_EVENT)).isEqualTo(2)
    verify(visitNotificationEventServiceSpy, times(2)).handleNonAssociations(any())

    // this should be twice even though the request was fired more than once
    verify(visitNotificationEventRepository, times(2)).saveAndFlush(any<VisitNotificationEvent>())
  }

  @Test
  fun `when prisoner with non associations visits has duplicate notification then they are not flagged or saved`() {
    // Given
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)

    val primaryVisit = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    val secondaryVisit = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    visitNotificationEventHelper.create(
      visit = primaryVisit,
      notificationEventType = NotificationEventType.NON_ASSOCIATION_EVENT,
      notificationAttributes = mapOf(Pair(PAIRED_VISIT, secondaryVisit.reference)),
    )

    visitNotificationEventHelper.create(
      visit = secondaryVisit,
      notificationEventType = NotificationEventType.NON_ASSOCIATION_EVENT,
      notificationAttributes = mapOf(Pair(PAIRED_VISIT, primaryVisit.reference)),
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    assertNotHandle()
  }

  @Test
  fun `when both prisoners have no overlapping visits then no visits are flagged or saved`() {
    // Given
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)

    // no visits overlap
    // visits for primary prisoners are for today + 1, +2 & +3
    createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(3),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    // visits for secondary prisoners are for today + 4, +5 & +6
    createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(4),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(5),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(6),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    assertNotHandle()
  }

  @Test
  fun `when primary prisoner has no future visits then no visits are flagged or saved`() {
    // Given
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)

    // no visits overlap
    // visits for primary prisoners are for today + 1, +2 & +3
    createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().minusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    // visits for secondary prisoners are for today + 4, +5 & +6
    createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now(),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now(),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(6),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    assertNotHandle()
  }

  @Test
  fun `when secondary prisoner has no future visits then no visits are flagged or saved`() {
    // Given
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)

    // no visits overlap
    // visits for primary prisoners are for today + 1, +2 & +3
    createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(3),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    // no visits for secondary prisoners
    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    assertNotHandle()
  }

  @Test
  fun `when both prisoners have overlapping visits only in the past then no visits are flagged or saved`() {
    // Given
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)

    val visitStart = LocalDate.now()

    // no visits overlap
    createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = visitStart.minusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = visitStart.minusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = visitStart.minusDays(3),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = visitStart.minusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = visitStart.minusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = visitStart.minusDays(3),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    assertNotHandle()
  }

  @Test
  fun `when future visits overlap but in different prisons then no visits are flagged or saved`() {
    // Given
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)

    // no visits overlap
    // visits for primary prisoner is for tomorrow at ABC prison
    createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    // visits for secondary prisoner is for tomorrow at DEF prison
    createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    assertNotHandle()
  }

  @Test
  fun `when future visits overlap but one of the visit is cancelled then no visits are flagged or saved`() {
    // Given
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)

    // cancelled visit
    val primaryVisit = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = CANCELLED,
      visitSubStatus = VisitSubStatus.CANCELLED,
      sessionTemplate = sessionTemplate1,
    )

    eventAuditEntityHelper.create(primaryVisit)

    val secondaryVisit = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(secondaryVisit)

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    assertNotHandle()
  }

  @Test
  fun `when prisoners are from a prison that is not supported then notifications are not removed`() {
    // Given
    prisonOffenderSearchMockServer.stubGetPrisonerByString(primaryPrisonerId, "AAA", IncentiveLevel.ENHANCED)
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)
    // Then

    responseSpec.expectStatus().isOk
    assertNotHandle()
  }

  private fun assertNotHandle() {
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
    assertThat(testEventAuditRepository.getAuditCount(NON_ASSOCIATION_EVENT)).isEqualTo(0)
  }

  private fun assertNotificationEvent(visitNotificationEvent: VisitNotificationEvent, expectedVisitReference: String, expectedNotificationEventAttributes: List<VisitNotificationEventAttributeDto>?) {
    assertThat(visitNotificationEvent.visit.reference).isEqualTo(expectedVisitReference)
    if (expectedNotificationEventAttributes != null) {
      assertThat(visitNotificationEvent.visitNotificationEventAttributes.size).isEqualTo(expectedNotificationEventAttributes.size)
      for (i in expectedNotificationEventAttributes.indices) {
        assertThat(visitNotificationEvent.visitNotificationEventAttributes[i].attributeName).isEqualTo(expectedNotificationEventAttributes[i].attributeName)
        assertThat(visitNotificationEvent.visitNotificationEventAttributes[i].attributeValue).isEqualTo(expectedNotificationEventAttributes[i].attributeValue)
      }
    } else {
      assertThat(visitNotificationEvent.visitNotificationEventAttributes).isNull()
    }
  }
}
