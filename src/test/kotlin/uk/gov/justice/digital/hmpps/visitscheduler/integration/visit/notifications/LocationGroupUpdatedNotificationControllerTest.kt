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
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_PRISONER_RELEASED_CHANGE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.PermittedSessionLocationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitevents.SessionLocationGroupUpdatedDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatLocationGroupHasUpdated
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_NOTIFICATION_PRISONER_RELEASED_CHANGE_PATH")
class LocationGroupUpdatedNotificationControllerTest : NotificationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit
  val prisoner1 = "prisoner-A"
  val prisoner2 = "prisoner-B"
  val prisoner3 = "prisoner-C"
  val prisoner4 = "prisoner-D"
  val prisonCode = "ABC"
  lateinit var prison1: Prison

  @BeforeEach
  internal fun setUp() {
    prison1 = prisonEntityHelper.create(prisonCode = prisonCode)
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when location group has updated but scope has been widened no visits are flagged`() {
    // Given
    // locationGroup was updated from A to A and B
    val locationGroupAfterUpdate = sessionLocationGroupHelper.create(
      prisonHierarchies = listOf(
        AllowedSessionLocationHierarchy(levelOneCode = "A"),
        AllowedSessionLocationHierarchy(levelOneCode = "B"),
      ),
    )

    // all prisoners are in A so are not affected
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner1, "$prisonCode-A-1-100-1")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner2, "$prisonCode-A-2-100-1")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner3, "$prisonCode-A-3-100-1")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner4, "$prisonCode-A-4-100-1")

    val sessionTemplate = sessionTemplateEntityHelper.create(prison = prison1, permittedSessionGroups = mutableListOf(locationGroupAfterUpdate), includeLocationGroupType = true)
    val sessionLocationGroupUpdatedDto = SessionLocationGroupUpdatedDto(
      prisonCode = prisonCode,
      locationGroupReference = locationGroupAfterUpdate.reference,
      oldLocations = listOf(PermittedSessionLocationDto(levelOneCode = "A")),
    )

    createApplicationAndVisit(
      prisonerId = prisoner1,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )

    createApplicationAndVisit(
      prisonerId = prisoner2,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )

    createApplicationAndVisit(
      prisonerId = prisoner3,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )

    createApplicationAndVisit(
      prisonerId = prisoner4,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = CANCELLED,
      sessionTemplate = sessionTemplate,
    )

    // When
    val responseSpec = callNotifyVSiPThatLocationGroupHasUpdated(webTestClient, roleVisitSchedulerHttpHeaders, sessionLocationGroupUpdatedDto)

    // Then
    responseSpec.expectStatus().isOk
    assertNotHandled()
  }

  @Test
  fun `when location group has updated but scope has been reduced visits are flagged`() {
    // Given
    // locationGroup was updated from A to A-1
    val locationGroupAfterUpdate = sessionLocationGroupHelper.create(
      prisonHierarchies = listOf(
        AllowedSessionLocationHierarchy(levelOneCode = "A", levelTwoCode = "1"),
      ),
    )

    // prisoners in A-2 and A-3 are flagged
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner1, "$prisonCode-A-1-100")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner2, "$prisonCode-A-2-100")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner3, "$prisonCode-A-3-100")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner4, "$prisonCode-A-4-100")

    val sessionTemplate = sessionTemplateEntityHelper.create(prison = prison1, permittedSessionGroups = mutableListOf(locationGroupAfterUpdate), includeLocationGroupType = true)
    val sessionLocationGroupUpdatedDto = SessionLocationGroupUpdatedDto(
      prisonCode = prisonCode,
      locationGroupReference = locationGroupAfterUpdate.reference,
      oldLocations = listOf(PermittedSessionLocationDto(levelOneCode = "A")),
    )

    val visit1 = createApplicationAndVisit(
      prisonerId = prisoner1,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit1)

    val visit2 = createApplicationAndVisit(
      prisonerId = prisoner2,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit2)

    val visit3 = createApplicationAndVisit(
      prisonerId = prisoner3,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit3)

    val visit4 = createApplicationAndVisit(
      prisonerId = prisoner4,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = CANCELLED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit4)

    // When
    val responseSpec = callNotifyVSiPThatLocationGroupHasUpdated(webTestClient, roleVisitSchedulerHttpHeaders, sessionLocationGroupUpdatedDto)

    // Then
    responseSpec.expectStatus().isOk

    assertFlaggedVisitEvent(listOf(visit2, visit3), NotificationEventType.LOCATION_GROUP_UPDATED_EVENT)
    verify(telemetryClient, times(2)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(2)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(2)
    assertThat(visitNotifications[0].bookingReference).isEqualTo(visit2.reference)
    assertThat(visitNotifications[1].bookingReference).isEqualTo(visit3.reference)

    val auditEvents = testEventAuditRepository.getAuditByType(EventAuditType.LOCATION_GROUP_UPDATED_EVENT)
    assertThat(auditEvents).hasSize(2)
    assertAuditEvent(auditEvents[0], visit2)
    assertAuditEvent(auditEvents[1], visit3)
  }

  @Test
  fun `when location group has updated but scope has been reduced - 2 levels - visits are flagged`() {
    // Given
    // locationGroup was updated from A-1 to A-1-100
    val locationGroupAfterUpdate = sessionLocationGroupHelper.create(
      prisonHierarchies = listOf(
        AllowedSessionLocationHierarchy(levelOneCode = "A", levelTwoCode = "1", levelThreeCode = "100"),
      ),
    )

    // prisoner2 and prisoner3 are in A-1-200 and A-1-300 are flagged
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner1, "$prisonCode-A-1-100")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner2, "$prisonCode-A-1-200")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner3, "$prisonCode-A-1-300")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner4, "$prisonCode-A-1-400")

    val sessionTemplate = sessionTemplateEntityHelper.create(prison = prison1, permittedSessionGroups = mutableListOf(locationGroupAfterUpdate), includeLocationGroupType = true)
    val sessionLocationGroupUpdatedDto = SessionLocationGroupUpdatedDto(
      prisonCode = prisonCode,
      locationGroupReference = locationGroupAfterUpdate.reference,
      oldLocations = listOf(PermittedSessionLocationDto(levelOneCode = "A", levelTwoCode = "1")),
    )

    val visit1 = createApplicationAndVisit(
      prisonerId = prisoner1,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit1)

    val visit2 = createApplicationAndVisit(
      prisonerId = prisoner2,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit2)

    val visit3 = createApplicationAndVisit(
      prisonerId = prisoner3,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit3)

    val visit4 = createApplicationAndVisit(
      prisonerId = prisoner4,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = CANCELLED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit4)

    // When
    val responseSpec = callNotifyVSiPThatLocationGroupHasUpdated(webTestClient, roleVisitSchedulerHttpHeaders, sessionLocationGroupUpdatedDto)

    // Then
    responseSpec.expectStatus().isOk

    assertFlaggedVisitEvent(listOf(visit2, visit3), NotificationEventType.LOCATION_GROUP_UPDATED_EVENT)
    verify(telemetryClient, times(2)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(2)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(2)
    assertThat(visitNotifications[0].bookingReference).isEqualTo(visit2.reference)
    assertThat(visitNotifications[1].bookingReference).isEqualTo(visit3.reference)

    val auditEvents = testEventAuditRepository.getAuditByType(EventAuditType.LOCATION_GROUP_UPDATED_EVENT)
    assertThat(auditEvents).hasSize(2)
    assertAuditEvent(auditEvents[0], visit2)
    assertAuditEvent(auditEvents[1], visit3)
  }

  @Test
  fun `when location group has updated but scope has been reduced - 3 levels - visits are flagged`() {
    // Given
    // locationGroup was updated from A-1-100 to A-1-100-1
    val locationGroupAfterUpdate = sessionLocationGroupHelper.create(
      prisonHierarchies = listOf(
        AllowedSessionLocationHierarchy(levelOneCode = "A", levelTwoCode = "1", levelThreeCode = "100", levelFourCode = "1"),
      ),
    )

    // prisoner2 and prisoner3 are in A-1-100-2 and A-1-100-3 are flagged
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner1, "$prisonCode-A-1-100-1")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner2, "$prisonCode-A-1-100-2")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner3, "$prisonCode-A-1-100-3")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner4, "$prisonCode-A-1-100-4")

    val sessionTemplate = sessionTemplateEntityHelper.create(prison = prison1, permittedSessionGroups = mutableListOf(locationGroupAfterUpdate), includeLocationGroupType = true)
    val sessionLocationGroupUpdatedDto = SessionLocationGroupUpdatedDto(
      prisonCode = prisonCode,
      locationGroupReference = locationGroupAfterUpdate.reference,
      oldLocations = listOf(PermittedSessionLocationDto(levelOneCode = "A", levelTwoCode = "1", levelThreeCode = "100")),
    )

    val visit1 = createApplicationAndVisit(
      prisonerId = prisoner1,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit1)

    val visit2 = createApplicationAndVisit(
      prisonerId = prisoner2,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit2)

    val visit3 = createApplicationAndVisit(
      prisonerId = prisoner3,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit3)

    val visit4 = createApplicationAndVisit(
      prisonerId = prisoner4,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = CANCELLED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit4)

    // When
    val responseSpec = callNotifyVSiPThatLocationGroupHasUpdated(webTestClient, roleVisitSchedulerHttpHeaders, sessionLocationGroupUpdatedDto)

    // Then
    responseSpec.expectStatus().isOk

    assertFlaggedVisitEvent(listOf(visit2, visit3), NotificationEventType.LOCATION_GROUP_UPDATED_EVENT)
    verify(telemetryClient, times(2)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(2)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(2)
    assertThat(visitNotifications[0].bookingReference).isEqualTo(visit2.reference)
    assertThat(visitNotifications[1].bookingReference).isEqualTo(visit3.reference)

    val auditEvents = testEventAuditRepository.getAuditByType(EventAuditType.LOCATION_GROUP_UPDATED_EVENT)
    assertThat(auditEvents).hasSize(2)
    assertAuditEvent(auditEvents[0], visit2)
    assertAuditEvent(auditEvents[1], visit3)
  }

  @Test
  fun `when location group has updated for include group visits are flagged`() {
    // Given
    // locationGroup was updated from A to B
    val locationGroupAfterUpdate = sessionLocationGroupHelper.create(
      prisonHierarchies = listOf(
        AllowedSessionLocationHierarchy(levelOneCode = "B"),
      ),
    )

    // prisoners in A-2 and A-3 are flagged
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner1, "$prisonCode-A-1-100")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner2, "$prisonCode-A-2-100")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner3, "$prisonCode-A-3-100")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner4, "$prisonCode-A-4-100")

    val sessionTemplate = sessionTemplateEntityHelper.create(prison = prison1, permittedSessionGroups = mutableListOf(locationGroupAfterUpdate), includeLocationGroupType = true)
    val sessionLocationGroupUpdatedDto = SessionLocationGroupUpdatedDto(
      prisonCode = prisonCode,
      locationGroupReference = locationGroupAfterUpdate.reference,
      oldLocations = listOf(PermittedSessionLocationDto(levelOneCode = "A")),
    )

    val visit1 = createApplicationAndVisit(
      prisonerId = prisoner1,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit1)

    val visit2 = createApplicationAndVisit(
      prisonerId = prisoner2,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit2)

    val visit3 = createApplicationAndVisit(
      prisonerId = prisoner3,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit3)

    val visit4 = createApplicationAndVisit(
      prisonerId = prisoner4,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = CANCELLED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit4)

    // When
    val responseSpec = callNotifyVSiPThatLocationGroupHasUpdated(webTestClient, roleVisitSchedulerHttpHeaders, sessionLocationGroupUpdatedDto)

    // Then
    responseSpec.expectStatus().isOk

    assertFlaggedVisitEvent(listOf(visit1, visit2, visit3), NotificationEventType.LOCATION_GROUP_UPDATED_EVENT)
    verify(telemetryClient, times(3)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(3)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(3)
    assertThat(visitNotifications[0].bookingReference).isEqualTo(visit1.reference)
    assertThat(visitNotifications[1].bookingReference).isEqualTo(visit2.reference)
    assertThat(visitNotifications[2].bookingReference).isEqualTo(visit3.reference)

    val auditEvents = testEventAuditRepository.getAuditByType(EventAuditType.LOCATION_GROUP_UPDATED_EVENT)
    assertThat(auditEvents).hasSize(3)
    assertAuditEvent(auditEvents[0], visit1)
    assertAuditEvent(auditEvents[1], visit2)
    assertAuditEvent(auditEvents[2], visit3)
  }

  @Test
  fun `when location group has updated but scope has been reduced for exclude no visits are flagged`() {
    // Given
    // locationGroup was updated from A and B to A
    val locationGroupAfterUpdate = sessionLocationGroupHelper.create(
      prisonHierarchies = listOf(
        AllowedSessionLocationHierarchy(levelOneCode = "A"),
      ),
    )

    // prisoners are in C wing and hence not flagged
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner1, "$prisonCode-C-1-100-1")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner2, "$prisonCode-C-2-100-1")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner3, "$prisonCode-C-3-100-1")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner4, "$prisonCode-C-4-100-1")

    // includeLocationGroupType is false
    val sessionTemplate = sessionTemplateEntityHelper.create(prison = prison1, permittedSessionGroups = mutableListOf(locationGroupAfterUpdate), includeLocationGroupType = false)
    val sessionLocationGroupUpdatedDto = SessionLocationGroupUpdatedDto(
      prisonCode = prisonCode,
      locationGroupReference = locationGroupAfterUpdate.reference,
      oldLocations = listOf(
        PermittedSessionLocationDto(levelOneCode = "A"),
        PermittedSessionLocationDto(levelOneCode = "B"),
      ),
    )

    createApplicationAndVisit(
      prisonerId = prisoner1,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )

    createApplicationAndVisit(
      prisonerId = prisoner2,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )

    createApplicationAndVisit(
      prisonerId = prisoner3,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )

    createApplicationAndVisit(
      prisonerId = prisoner4,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = CANCELLED,
      sessionTemplate = sessionTemplate,
    )

    // When
    // all prisoners are in C
    val responseSpec = callNotifyVSiPThatLocationGroupHasUpdated(webTestClient, roleVisitSchedulerHttpHeaders, sessionLocationGroupUpdatedDto)

    // Then
    responseSpec.expectStatus().isOk
    assertNotHandled()
  }

  @Test
  fun `when location group has updated but scope has been upped for exclude template - 4 levels - visits are flagged`() {
    // Given
    // locationGroup was updated from A-1-100-1 to A-1-100
    val locationGroupAfterUpdate = sessionLocationGroupHelper.create(
      prisonHierarchies = listOf(
        AllowedSessionLocationHierarchy(levelOneCode = "A", levelTwoCode = "1", levelThreeCode = "100"),
      ),
    )

    // prisoner2 and prisoner3 are in A-1-100 wing and hence excluded and flagged
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner1, "$prisonCode-A-1-200-1")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner2, "$prisonCode-A-1-100-3")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner3, "$prisonCode-A-1-100-4")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner4, "$prisonCode-A-1-100-5")

    // excluded location group
    val sessionTemplate = sessionTemplateEntityHelper.create(prison = prison1, permittedSessionGroups = mutableListOf(locationGroupAfterUpdate), includeLocationGroupType = false)
    val sessionLocationGroupUpdatedDto = SessionLocationGroupUpdatedDto(
      prisonCode = prisonCode,
      locationGroupReference = locationGroupAfterUpdate.reference,
      oldLocations = listOf(PermittedSessionLocationDto(levelOneCode = "A", levelTwoCode = "1", levelThreeCode = "100", levelFourCode = "1")),
    )

    val visit1 = createApplicationAndVisit(
      prisonerId = prisoner1,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit1)

    val visit2 = createApplicationAndVisit(
      prisonerId = prisoner2,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit2)

    val visit3 = createApplicationAndVisit(
      prisonerId = prisoner3,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit3)

    val visit4 = createApplicationAndVisit(
      prisonerId = prisoner4,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = CANCELLED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit4)

    // When
    val responseSpec = callNotifyVSiPThatLocationGroupHasUpdated(webTestClient, roleVisitSchedulerHttpHeaders, sessionLocationGroupUpdatedDto)

    // Then
    responseSpec.expectStatus().isOk

    assertFlaggedVisitEvent(listOf(visit2, visit3), NotificationEventType.LOCATION_GROUP_UPDATED_EVENT)
    verify(telemetryClient, times(2)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(2)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(2)
    assertThat(visitNotifications[0].bookingReference).isEqualTo(visit2.reference)
    assertThat(visitNotifications[1].bookingReference).isEqualTo(visit3.reference)

    val auditEvents = testEventAuditRepository.getAuditByType(EventAuditType.LOCATION_GROUP_UPDATED_EVENT)
    assertThat(auditEvents).hasSize(2)
    assertAuditEvent(auditEvents[0], visit2)
    assertAuditEvent(auditEvents[1], visit3)
  }

  @Test
  fun `when location group has updated but scope has been upped for exclude template - 3 levels - visits are flagged`() {
    // Given
    // locationGroup was updated from A-1-100 to A-1
    val locationGroupAfterUpdate = sessionLocationGroupHelper.create(
      prisonHierarchies = listOf(
        AllowedSessionLocationHierarchy(levelOneCode = "A", levelTwoCode = "1"),
      ),
    )
    // prisoner2 and prisoner3 are in A-1 wing and hence excluded and flagged
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner1, "$prisonCode-A-2-100-1")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner2, "$prisonCode-A-1-200-2")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner3, "$prisonCode-A-1-300-1")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner4, "$prisonCode-A-1-400-1")

    // excluded location group
    val sessionTemplate = sessionTemplateEntityHelper.create(prison = prison1, permittedSessionGroups = mutableListOf(locationGroupAfterUpdate), includeLocationGroupType = false)
    val sessionLocationGroupUpdatedDto = SessionLocationGroupUpdatedDto(
      prisonCode = prisonCode,
      locationGroupReference = locationGroupAfterUpdate.reference,
      oldLocations = listOf(PermittedSessionLocationDto(levelOneCode = "A", levelTwoCode = "1", levelThreeCode = "100")),
    )

    val visit1 = createApplicationAndVisit(
      prisonerId = prisoner1,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit1)

    val visit2 = createApplicationAndVisit(
      prisonerId = prisoner2,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit2)

    val visit3 = createApplicationAndVisit(
      prisonerId = prisoner3,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit3)

    val visit4 = createApplicationAndVisit(
      prisonerId = prisoner4,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = CANCELLED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit4)

    // When
    val responseSpec = callNotifyVSiPThatLocationGroupHasUpdated(webTestClient, roleVisitSchedulerHttpHeaders, sessionLocationGroupUpdatedDto)

    // Then
    responseSpec.expectStatus().isOk

    assertFlaggedVisitEvent(listOf(visit2, visit3), NotificationEventType.LOCATION_GROUP_UPDATED_EVENT)
    verify(telemetryClient, times(2)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(2)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(2)
    assertThat(visitNotifications[0].bookingReference).isEqualTo(visit2.reference)
    assertThat(visitNotifications[1].bookingReference).isEqualTo(visit3.reference)

    val auditEvents = testEventAuditRepository.getAuditByType(EventAuditType.LOCATION_GROUP_UPDATED_EVENT)
    assertThat(auditEvents).hasSize(2)
    assertAuditEvent(auditEvents[0], visit2)
    assertAuditEvent(auditEvents[1], visit3)
  }

  @Test
  fun `when location group has updated but scope has been upped for exclude template - 2 levels - visits are flagged`() {
    // Given
    // locationGroup was updated from A-1 to A
    val locationGroupAfterUpdate = sessionLocationGroupHelper.create(
      prisonHierarchies = listOf(
        AllowedSessionLocationHierarchy(levelOneCode = "A"),
      ),
    )

    // prisoner2 and prisoner3 are in A wing and hence excluded and flagged
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner1, "$prisonCode-B-2-100-1")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner2, "$prisonCode-A-1-200-2")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner3, "$prisonCode-A-4-300-1")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner4, "$prisonCode-A-1-400-1")

    // excluded location group
    val sessionTemplate = sessionTemplateEntityHelper.create(prison = prison1, permittedSessionGroups = mutableListOf(locationGroupAfterUpdate), includeLocationGroupType = false)
    val sessionLocationGroupUpdatedDto = SessionLocationGroupUpdatedDto(
      prisonCode = prisonCode,
      locationGroupReference = locationGroupAfterUpdate.reference,
      oldLocations = listOf(PermittedSessionLocationDto(levelOneCode = "A", levelTwoCode = "1")),
    )

    val visit1 = createApplicationAndVisit(
      prisonerId = prisoner1,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit1)

    val visit2 = createApplicationAndVisit(
      prisonerId = prisoner2,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit2)

    val visit3 = createApplicationAndVisit(
      prisonerId = prisoner3,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit3)

    val visit4 = createApplicationAndVisit(
      prisonerId = prisoner4,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = CANCELLED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit4)

    // When
    val responseSpec = callNotifyVSiPThatLocationGroupHasUpdated(webTestClient, roleVisitSchedulerHttpHeaders, sessionLocationGroupUpdatedDto)

    // Then
    responseSpec.expectStatus().isOk

    assertFlaggedVisitEvent(listOf(visit2, visit3), NotificationEventType.LOCATION_GROUP_UPDATED_EVENT)
    verify(telemetryClient, times(2)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(2)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(2)
    assertThat(visitNotifications[0].bookingReference).isEqualTo(visit2.reference)
    assertThat(visitNotifications[1].bookingReference).isEqualTo(visit3.reference)

    val auditEvents = testEventAuditRepository.getAuditByType(EventAuditType.LOCATION_GROUP_UPDATED_EVENT)
    assertThat(auditEvents).hasSize(2)
    assertAuditEvent(auditEvents[0], visit2)
    assertAuditEvent(auditEvents[1], visit3)
  }

  @Test
  fun `when location group has updated for exclude group only affected visits from new location are flagged`() {
    // Given
    // locationGroup was updated from A to B
    val locationGroupAfterUpdate = sessionLocationGroupHelper.create(
      prisonHierarchies = listOf(
        AllowedSessionLocationHierarchy(levelOneCode = "B"),
      ),
    )

    // prisoners in A-2 and A-3 are flagged
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner1, "$prisonCode-A-1-100")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner2, "$prisonCode-A-2-100")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner3, "$prisonCode-B-3-100")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner4, "$prisonCode-A-4-100")

    val sessionTemplate = sessionTemplateEntityHelper.create(prison = prison1, permittedSessionGroups = mutableListOf(locationGroupAfterUpdate), includeLocationGroupType = false)
    val sessionLocationGroupUpdatedDto = SessionLocationGroupUpdatedDto(
      prisonCode = prisonCode,
      locationGroupReference = locationGroupAfterUpdate.reference,
      oldLocations = listOf(PermittedSessionLocationDto(levelOneCode = "A")),
    )

    val visit1 = createApplicationAndVisit(
      prisonerId = prisoner1,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit1)

    val visit2 = createApplicationAndVisit(
      prisonerId = prisoner2,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit2)

    val visit3 = createApplicationAndVisit(
      prisonerId = prisoner3,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit3)

    val visit4 = createApplicationAndVisit(
      prisonerId = prisoner4,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = CANCELLED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit4)

    // When
    val responseSpec = callNotifyVSiPThatLocationGroupHasUpdated(webTestClient, roleVisitSchedulerHttpHeaders, sessionLocationGroupUpdatedDto)

    // Then
    responseSpec.expectStatus().isOk

    assertFlaggedVisitEvent(listOf(visit3), NotificationEventType.LOCATION_GROUP_UPDATED_EVENT)
    verify(telemetryClient, times(1)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(1)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(1)
    assertThat(visitNotifications[0].bookingReference).isEqualTo(visit3.reference)

    val auditEvents = testEventAuditRepository.getAuditByType(EventAuditType.LOCATION_GROUP_UPDATED_EVENT)
    assertThat(auditEvents).hasSize(1)
    assertAuditEvent(auditEvents[0], visit3)
  }

  @Test
  fun `when location group has updated and multiple session templates are affected visits are flagged`() {
    // Given
    // locationGroup was updated from A and B and C to A and D
    val locationGroupAfterUpdate = sessionLocationGroupHelper.create(
      prisonHierarchies = listOf(
        AllowedSessionLocationHierarchy(levelOneCode = "A"),
        AllowedSessionLocationHierarchy(levelOneCode = "D"),
      ),
    )

    // prisoner2 and 3 are in B and C wings affected and hence flagged
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner1, "$prisonCode-A-1-100-1")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner2, "$prisonCode-B-2-100-1")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner3, "$prisonCode-C-3-100-1")
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisoner4, "$prisonCode-D-4-100-1")

    // includeLocationGroupType is true
    val sessionTemplate = sessionTemplateEntityHelper.create(prison = prison1, permittedSessionGroups = mutableListOf(locationGroupAfterUpdate), includeLocationGroupType = true)

    // includeLocationGroupType is false
    val sessionTemplate2 = sessionTemplateEntityHelper.create(prison = prison1, permittedSessionGroups = mutableListOf(locationGroupAfterUpdate), includeLocationGroupType = false)

    val sessionLocationGroupUpdatedDto = SessionLocationGroupUpdatedDto(
      prisonCode = prisonCode,
      locationGroupReference = locationGroupAfterUpdate.reference,
      oldLocations = listOf(
        PermittedSessionLocationDto(levelOneCode = "A"),
        PermittedSessionLocationDto(levelOneCode = "B"),
        PermittedSessionLocationDto(levelOneCode = "C"),
      ),
    )

    val visit1 = createApplicationAndVisit(
      prisonerId = prisoner1,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit1)

    val visit2 = createApplicationAndVisit(
      prisonerId = prisoner2,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit2)

    val visit3 = createApplicationAndVisit(
      prisonerId = prisoner3,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit3)

    val visit4 = createApplicationAndVisit(
      prisonerId = prisoner4,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = CANCELLED,
      sessionTemplate = sessionTemplate,
    )
    eventAuditEntityHelper.create(visit4)

    val visit5 = createApplicationAndVisit(
      prisonerId = prisoner2,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate2,
    )
    eventAuditEntityHelper.create(visit5)

    val visit6 = createApplicationAndVisit(
      prisonerId = prisoner4,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate2,
    )
    eventAuditEntityHelper.create(visit6)

    // When
    val responseSpec = callNotifyVSiPThatLocationGroupHasUpdated(webTestClient, roleVisitSchedulerHttpHeaders, sessionLocationGroupUpdatedDto)

    // Then
    responseSpec.expectStatus().isOk

    assertFlaggedVisitEvent(listOf(visit2, visit3, visit6), NotificationEventType.LOCATION_GROUP_UPDATED_EVENT)
    verify(telemetryClient, times(3)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(3)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(3)
    assertThat(visitNotifications[0].bookingReference).isEqualTo(visit2.reference)
    assertThat(visitNotifications[1].bookingReference).isEqualTo(visit3.reference)
    assertThat(visitNotifications[2].bookingReference).isEqualTo(visit6.reference)

    val auditEvents = testEventAuditRepository.getAuditByType(EventAuditType.LOCATION_GROUP_UPDATED_EVENT)
    assertThat(auditEvents).hasSize(3)
    assertAuditEvent(auditEvents[0], visit2)
    assertAuditEvent(auditEvents[1], visit3)
    assertAuditEvent(auditEvents[2], visit6)
  }

  private fun assertNotHandled() {
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
    assertThat(testEventAuditRepository.getAuditCount(EventAuditType.LOCATION_GROUP_UPDATED_EVENT)).isEqualTo(0)
  }

  private fun assertAuditEvent(eventAudit: EventAudit, visit: Visit) {
    with(eventAudit) {
      assertThat(actionedBy.userName).isNull()
      assertThat(bookingReference).isEqualTo(visit.reference)
      assertThat(applicationReference).isEqualTo(visit.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(visit.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(EventAuditType.LOCATION_GROUP_UPDATED_EVENT)
      assertThat(applicationMethodType).isEqualTo(ApplicationMethodType.NOT_KNOWN)
      assertThat(actionedBy.userType).isEqualTo(UserType.SYSTEM)
    }
  }
}
