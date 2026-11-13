package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.microsoft.applicationinsights.TelemetryClient
import jakarta.validation.ValidationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.CreateApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.builder.VisitDtoBuilder
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISON_VISITS_BLOCKED_FOR_DATE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonDateBlockedDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.ProcessVisitNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.VisitNotificationEventHelper
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.ApplicationService
import uk.gov.justice.digital.hmpps.visitscheduler.service.MigrateVisitService
import uk.gov.justice.digital.hmpps.visitscheduler.service.SlotCapacityService
import uk.gov.justice.digital.hmpps.visitscheduler.service.SnsService
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitEventAuditService
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitNotificationEventService
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitNotificationTransactionalService
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitService
import uk.gov.justice.digital.hmpps.visitscheduler.service.reporting.OverbookedSessionsReportService
import uk.gov.justice.digital.hmpps.visitscheduler.service.reporting.VisitCountsByDateReportService
import uk.gov.justice.digital.hmpps.visitscheduler.task.FlagVisitsTask
import java.time.LocalDate
import java.time.LocalTime

class SocialVideoVisitIsolationTest : IntegrationTestBase() {
  @Autowired
  private lateinit var visitRepository: VisitRepository

  @Autowired
  private lateinit var sessionTemplateRepository: SessionTemplateRepository

  @Autowired
  private lateinit var slotCapacityService: SlotCapacityService

  @Autowired
  private lateinit var visitService: VisitService

  @Autowired
  private lateinit var visitDtoBuilder: VisitDtoBuilder

  @Autowired
  private lateinit var visitEventAuditService: VisitEventAuditService

  @Autowired
  private lateinit var notificationRepository: VisitNotificationEventRepository

  @Autowired
  private lateinit var notificationHelper: VisitNotificationEventHelper

  @Autowired
  private lateinit var notificationService: VisitNotificationEventService

  @Autowired
  private lateinit var notificationTransactionalService: VisitNotificationTransactionalService

  @Autowired
  private lateinit var flagVisitsTask: FlagVisitsTask

  @Autowired
  private lateinit var visitCountsReportService: VisitCountsByDateReportService

  @Autowired
  private lateinit var overbookedSessionsReportService: OverbookedSessionsReportService

  @Autowired
  private lateinit var applicationService: ApplicationService

  @Autowired
  private lateinit var migrateVisitService: MigrateVisitService

  @MockitoSpyBean
  private lateinit var telemetryClient: TelemetryClient

  @MockitoSpyBean
  private lateinit var snsService: SnsService

  private val prisonerId = "A1234BC"
  private val visitorId = 1234L

  @Test
  fun `Normal searches and affected-visit queries return social visits only`() {
    createVisit(VisitType.SOCIAL_VIDEO)
    val social = createVisit(VisitType.SOCIAL)
    val date = social.sessionSlot.slotDate
    val start = date.atStartOfDay()
    val end = date.plusDays(1).atStartOfDay()
    val prisonCode = social.prison.code

    assertThat(visitService.findVisitsByFilterPageableDescending(VisitFilter(prisonCode = prisonCode, visitStatusList = emptyList())).content.map { it.reference }).containsExactly(social.reference)
    assertThat(visitService.getBookedVisitsForDate(prisonCode, date).map { it.reference }).containsExactly(social.reference)
    assertThat(visitService.getBookedVisitsBySessionForDate(sessionTemplateDefault.reference, date).map { it.reference }).containsExactly(social.reference)
    assertThat(visitService.getBookedVisits(prisonerId, prisonCode, date).map { it.reference }).containsExactly(social.reference)
    assertThat(visitService.getFutureVisitsBy(prisonerId, prisonCode, start, end).map { it.reference }).containsExactly(social.reference)
    assertThat(visitService.getFutureBookedVisits(prisonerId, prisonCode, start, end).map { it.reference }).containsExactly(social.reference)
    assertThat(visitService.getFutureBookedVisitsExcludingRequestVisits(prisonerId, prisonCode, start, end).map { it.reference }).containsExactly(social.reference)
    assertThat(visitService.getFutureVisitsByVisitorId(visitorId.toString(), prisonerId, start, end).map { it.reference }).containsExactly(social.reference)
    assertThat(visitService.getFutureBookedVisitsExcludingPrisonAndExcludingRequestVisits(prisonerId, "OTHER").map { it.reference }).containsExactly(social.reference)
    assertThat(visitService.getVisitsThatOverlapProvidedTimeWindow(prisonerId, prisonCode, start, end).map { it.reference }).containsExactly(social.reference)
    assertThat(visitRepository.findVisitsBySessionTemplateReference(sessionTemplateDefault.reference, date, date, null, null, prisonCode).map { it.reference }).containsExactly(social.reference)
    assertThat(visitRepository.findBookedVisitsBySessionTemplateReference(sessionTemplateDefault.reference, date, date).map { it.reference }).containsExactly(social.reference)
  }

  @Test
  fun `Ad hoc video visits are excluded from no-template searches and pagination totals`() {
    createAdHocVisit(VisitType.SOCIAL_VIDEO)
    val social = createAdHocVisit(VisitType.SOCIAL)
    val date = social.sessionSlot.slotDate

    assertThat(visitRepository.findVisitsWithNoSessionTemplateReference(date, date, null, null, social.prison.code).map { it.reference }).containsExactly(social.reference)
    val page = visitRepository.findVisitsOrderByDateAndTime(prisonerId, social.prison.code, null, date, date, PageRequest.of(0, 1))
    assertThat(page.totalElements).isEqualTo(1)
    assertThat(page.content.map { it.reference }).containsExactly(social.reference)
  }

  @Test
  fun `Video booking audits do not affect last approved visitor dates`() {
    val video = createVisit(VisitType.SOCIAL_VIDEO)
    visitEventAuditService.saveBookingEventAudit(prisonerId, visitDtoBuilder.build(video), EventAuditType.BOOKED_VISIT, ApplicationMethodType.BY_PRISONER, UserType.PRISONER)
    assertThat(visitRepository.getLastApprovedVisitDatesByVisitor(prisonerId, listOf(visitorId))).isEmpty()

    val social = createVisit(VisitType.SOCIAL)
    visitEventAuditService.saveBookingEventAudit(prisonerId, visitDtoBuilder.build(social), EventAuditType.BOOKED_VISIT, ApplicationMethodType.BY_PRISONER, UserType.PRISONER)
    assertThat(visitRepository.getLastApprovedVisitDatesByVisitor(prisonerId, listOf(visitorId)).single().lastApprovedDate).isEqualTo(LocalDate.now())
  }

  @Test
  fun `Public booking lists exclude video visits regardless of their source`() {
    val booker = "test-booker"
    val video = createVisit(VisitType.SOCIAL_VIDEO, userType = UserType.PUBLIC)
    val social = createVisit(VisitType.SOCIAL, userType = UserType.PUBLIC)
    listOf(video, social).forEach {
      visitEventAuditService.saveBookingEventAudit(booker, visitDtoBuilder.build(it), EventAuditType.BOOKED_VISIT, ApplicationMethodType.WEBSITE, UserType.PUBLIC)
    }
    assertThat(visitRepository.getPublicFutureBookingsByBookerReference(booker).map { it.reference }).containsExactly(social.reference)
    assertThat(visitRepository.getPublicPastBookingsByBookerReference(booker)).isEmpty()

    listOf(video, social).forEach {
      it.visitStatus = VisitStatus.CANCELLED
      it.visitSubStatus = VisitSubStatus.CANCELLED
      visitEntityHelper.save(it)
    }
    assertThat(visitRepository.getPublicCanceledVisitsByBookerReference(booker).map { it.reference }).containsExactly(social.reference)
  }

  @Test
  fun `Video visits do not cause double booking non association or remand limit failures`() {
    val video = createVisit(VisitType.SOCIAL_VIDEO)
    val date = video.sessionSlot.slotDate
    val prison = video.prison
    val slotId = video.sessionSlotId

    assertThat(visitRepository.hasActiveVisitsForDate(listOf(prisonerId), date, prison.id)).isFalse()
    assertThat(visitRepository.getBookedVisitsForPrisonersAndDates(listOf(prisonerId), listOf(date), prison.id)).isEmpty()
    assertThat(visitRepository.hasActiveVisitForSessionSlot(prisonerId, slotId)).isFalse()
    assertThat(visitRepository.getActiveVisitForSessionSlot(prisonerId, slotId)).isNull()
    assertThat(visitRepository.getActiveVisitsForSessionSlots(prisonerId, listOf(slotId))).isEmpty()
    assertThat(visitRepository.getBookedVisitsThatCountTowardsRemandLimit(prisonerId, prison.code, date.atStartOfDay(), date.plusDays(1).atStartOfDay())).isEmpty()
    assertThat(visitRepository.getCountOfBookedVisits(prisonerId, prison.code, date, date, null)).isZero()
    assertThat(visitRepository.hasVisitsForSessionTemplate(sessionTemplateDefault.reference, date)).isFalse()
    assertThat(visitRepository.hasBookedVisitsForSessionTemplate(sessionTemplateDefault.reference, date)).isFalse()

    val social = createVisit(VisitType.SOCIAL)
    assertThat(visitRepository.hasActiveVisitsForDate(listOf(prisonerId), date, prison.id)).isTrue()
    assertThat(visitRepository.getBookedVisitsForPrisonersAndDates(listOf(prisonerId), listOf(date), prison.id).map { it.reference }).containsExactly(social.reference)
    assertThat(visitRepository.hasActiveVisitForSessionSlot(prisonerId, slotId)).isTrue()
    assertThat(visitRepository.getActiveVisitForSessionSlot(prisonerId, slotId)?.reference).isEqualTo(social.reference)
    assertThat(visitRepository.getActiveVisitsForSessionSlots(prisonerId, listOf(slotId)).map { it.reference }).containsExactly(social.reference)
    assertThat(visitRepository.getBookedVisitsThatCountTowardsRemandLimit(prisonerId, prison.code, date.atStartOfDay(), date.plusDays(1).atStartOfDay()).map { it.reference }).containsExactly(social.reference)
    assertThat(visitRepository.getCountOfBookedVisits(prisonerId, prison.code, date, date, null)).isEqualTo(1)
  }

  @ParameterizedTest
  @EnumSource(VisitRestriction::class, names = ["OPEN", "CLOSED"])
  fun `Video visits do not contribute to capacity or session visit and visitor counts`(restriction: VisitRestriction) {
    val video = createVisit(VisitType.SOCIAL_VIDEO, restriction = restriction)
    val slotId = video.sessionSlotId
    assertThat(visitService.getBookCountForSlot(slotId, restriction)).isZero()
    assertThat(visitRepository.getCountOfBookedSessionVisitsForOpenOrClosedRestriction(slotId)).isEmpty()
    assertThat(visitRepository.getVisitCountsBySession(slotId)).isEmpty()
    val date = video.sessionSlot.slotDate
    assertThat(sessionTemplateRepository.getVisitCountsByDate(sessionTemplateDefault.reference, date, date, VisitStatus.BOOKED.name)).isEmpty()

    createVisit(VisitType.SOCIAL, restriction = restriction)
    assertThat(visitService.getBookCountForSlot(slotId, restriction)).isEqualTo(1)
    assertThat(visitRepository.getCountOfBookedSessionVisitsForOpenOrClosedRestriction(slotId).single().count).isEqualTo(1)
    val counts = visitRepository.getVisitCountsBySession(slotId).single()
    assertThat(counts.visitCount).isEqualTo(1)
    assertThat(counts.visitorCount).isEqualTo(1)
    assertThat(sessionTemplateRepository.getVisitCountsByDate(sessionTemplateDefault.reference, date, date, VisitStatus.BOOKED.name).single().visitCount).isEqualTo(1)
    val minCapacity = sessionTemplateRepository.findSessionTemplateMinCapacityBy(sessionTemplateDefault.reference, date, date.plusDays(1))
    val capacityColumn = if (restriction == VisitRestriction.OPEN) 0 else 1
    assertThat((minCapacity.get(capacityColumn) as Number).toLong()).isEqualTo(1)

    video.visitStatus = VisitStatus.CANCELLED
    video.visitSubStatus = VisitSubStatus.CANCELLED
    visitEntityHelper.save(video)
    assertThat(visitRepository.getCountOfCancelledSessionVisitsForOpenOrClosedRestriction(slotId)).isEmpty()
    assertThat(visitRepository.getVisitCountsBySession(slotId).map { it.visitStatus }).containsExactly(VisitStatus.BOOKED)
  }

  @Test
  fun `Notification flagging ignores video visits and still flags social visits`() {
    val video = createVisit(VisitType.SOCIAL_VIDEO)
    val social = createVisit(VisitType.SOCIAL)
    notificationService.handleAddPrisonVisitBlockDate(PrisonDateBlockedDto(social.prison.code, social.sessionSlot.slotDate))

    assertThat(notificationHelper.getVisitNotifications(video.reference)).isEmpty()
    assertThat(notificationHelper.getVisitNotifications(social.reference).map { it.type }).containsExactly(PRISON_VISITS_BLOCKED_FOR_DATE)
  }

  @Test
  fun `Notification processing filters video visits even when explicitly passed`() {
    val video = createVisit(VisitType.SOCIAL_VIDEO)
    val social = createVisit(VisitType.SOCIAL)
    notificationTransactionalService.processVisitsWithNotifications(
      ProcessVisitNotificationDto(listOf(visitDtoBuilder.build(video), visitDtoBuilder.build(social)), PRISON_VISITS_BLOCKED_FOR_DATE, null),
    )
    assertThat(notificationHelper.getVisitNotifications(video.reference)).isEmpty()
    assertThat(notificationHelper.getVisitNotifications(social.reference)).hasSize(1)
  }

  @Test
  fun `Notification counts and lists do not expose video notifications`() {
    val video = createVisit(VisitType.SOCIAL_VIDEO)
    val social = createVisit(VisitType.SOCIAL)
    notificationHelper.create(video, PRISON_VISITS_BLOCKED_FOR_DATE)
    val socialNotification = notificationHelper.create(social, PRISON_VISITS_BLOCKED_FOR_DATE)
    val prisonCode = social.prison.code
    val types = listOf(PRISON_VISITS_BLOCKED_FOR_DATE.name)

    assertThat(notificationRepository.getNotificationGroupsCountByPrisonCode(prisonCode)).isEqualTo(1)
    assertThat(notificationRepository.getNotificationGroupsCountByPrisonCode(prisonCode, types)).isEqualTo(1)
    assertThat(notificationRepository.getFutureVisitNotificationEvents(prisonCode).map { it.reference }).containsExactly(socialNotification.reference)
    assertThat(notificationRepository.getFutureVisitNotificationEvents(prisonCode, types).map { it.reference }).containsExactly(socialNotification.reference)
    assertThat(notificationRepository.getEventsBy(prisonerId, prisonCode, PRISON_VISITS_BLOCKED_FOR_DATE).map { it.reference }).containsExactly(socialNotification.reference)
    assertThat(notificationRepository.getEventsByVisitDate(prisonCode, social.sessionSlot.slotDate, PRISON_VISITS_BLOCKED_FOR_DATE).map { it.reference }).containsExactly(socialNotification.reference)
    assertThat(notificationRepository.getEventsBySessionTemplateReferenceAndVisitDate(sessionTemplateDefault.reference, social.sessionSlot.slotDate, PRISON_VISITS_BLOCKED_FOR_DATE).map { it.reference }).containsExactly(socialNotification.reference)
    assertThat(notificationRepository.getNotificationsTypesForBookingReference(video.reference)).isEmpty()
    assertThat(notificationRepository.findVisitNotificationEventByVisitReference(video.reference)).isEmpty()
  }

  @Test
  fun `Scheduled flagging ignores an ad hoc video visit`() {
    createAdHocVisit(VisitType.SOCIAL_VIDEO)
    flagVisitsTask.flagVisits()
    verify(telemetryClient, never()).trackEvent(eq("flagged-visit-event"), any(), isNull())
    assertThat(notificationHelper.getAllVisitNotifications()).isEmpty()
  }

  @Test
  fun `Operational reports exclude video visits and their visitors`() {
    val template = sessionTemplateEntityHelper.create(openCapacity = 1, closedCapacity = 1)
    createVisit(VisitType.SOCIAL_VIDEO, template)
    val social = createVisit(VisitType.SOCIAL, template)
    val report = visitCountsReportService.getVisitCountsBySession(social.sessionSlot.slotDate)
      .single { it.visitCountBySession?.sessionReference == template.reference }.visitCountBySession!!

    assertThat(report.openBookedCount).isEqualTo(1)
    assertThat(report.openBookedVisitorsCount).isEqualTo(1)
    assertThat(overbookedSessionsReportService.getOverbookedSessions(social.sessionSlot.slotDate)).isEmpty()
  }

  @ParameterizedTest
  @EnumSource(VisitRestriction::class, names = ["OPEN", "CLOSED"])
  fun `A video booking does not prevent a social application reserving the last space`(restriction: VisitRestriction) {
    val template = sessionTemplateEntityHelper.create(openCapacity = 1, closedCapacity = 1)
    val video = createVisit(VisitType.SOCIAL_VIDEO, template, restriction)

    slotCapacityService.checkCapacityForApplicationReservation(video.sessionSlot.reference, restriction, true)
    slotCapacityService.checkCapacityForBooking(video.sessionSlot.reference, restriction, true)
  }

  @Test
  fun `Video visits cannot enter the normal application amendment flow`() {
    val video = createVisit(VisitType.SOCIAL_VIDEO)
    assertThatThrownBy {
      applicationService.createApplicationForAnExistingVisit(
        video.reference,
        CreateApplicationDto(
          prisonerId = prisonerId,
          sessionTemplateReference = sessionTemplateDefault.reference,
          sessionDate = video.sessionSlot.slotDate,
          applicationRestriction = SessionRestriction.OPEN,
          visitContact = ContactDto("Test contact", "01234567890", null),
          visitors = emptySet(),
          actionedBy = "test-staff",
          userType = UserType.STAFF,
        ),
      )
    }.isInstanceOf(ValidationException::class.java)
      .hasMessageContaining("external-system")
  }

  @Test
  fun `Video visits cannot be created through migration`() {
    assertThatThrownBy {
      migrateVisitService.migrateVisit(
        MigrateVisitRequestDto(
          prisonerId = prisonerId,
          prisonCode = sessionTemplateDefault.prison.code,
          visitRoom = "Video room",
          visitType = VisitType.SOCIAL_VIDEO,
          visitStatus = VisitStatus.BOOKED,
          visitRestriction = VisitRestriction.OPEN,
          startTimestamp = startDate.atTime(9, 0),
          endTimestamp = startDate.atTime(10, 0),
        ),
      )
    }.isInstanceOf(ValidationException::class.java)
      .hasMessageContaining("external-system")
    assertThat(visitRepository.count()).isZero()
  }

  @ParameterizedTest
  @EnumSource(VisitType::class)
  fun `Cancelling a visit publishes SNS and telemetry with its visit type`(visitType: VisitType) {
    val visit = createAdHocVisit(visitType)
    val cancelled = visitService.cancelVisit(
      visit.reference,
      CancelVisitDto(OutcomeDto(OutcomeStatus.VISITOR_CANCELLED), "test-staff", UserType.STAFF, ApplicationMethodType.NOT_KNOWN),
    )
    assertThat(cancelled.visitStatus).isEqualTo(VisitStatus.CANCELLED)
    verify(snsService).sendVisitCancelledEvent(
      org.mockito.kotlin.check {
        assertThat(it.reference).isEqualTo(cancelled.reference)
        assertThat(it.visitType).isEqualTo(visitType)
      },
    )
    verify(telemetryClient).trackEvent(
      eq("visit-cancelled"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(cancelled.reference)
        assertThat(it["visitType"]).isEqualTo(visitType.name)
        assertThat(it["visitStatus"]).isEqualTo(VisitStatus.CANCELLED.name)
        assertThat(it["outcomeStatus"]).isEqualTo(OutcomeStatus.VISITOR_CANCELLED.name)
      },
      isNull(),
    )
  }

  private fun createVisit(
    type: VisitType,
    template: SessionTemplate = sessionTemplateDefault,
    restriction: VisitRestriction = VisitRestriction.OPEN,
    userType: UserType = UserType.STAFF,
  ): Visit {
    val visit = visitEntityHelper.create(
      sessionTemplate = template,
      slotDate = sessionDatesUtil.getFirstBookableSessionDay(template),
      prisonerId = prisonerId,
      visitType = type,
      visitRestriction = restriction,
      userType = userType,
      createApplication = false,
      visitContact = ContactDto("Test contact", "01234567890", null),
    )
    visitEntityHelper.createVisitor(visit, visitorId, true)
    return visitEntityHelper.save(visit)
  }

  private fun createAdHocVisit(type: VisitType): Visit = visitEntityHelper.create(
    prisonerId = prisonerId,
    prisonCode = sessionTemplateDefault.prison.code,
    visitRoom = "External room",
    slotDate = LocalDate.now().plusDays(1),
    visitStart = LocalTime.of(9, 0),
    visitEnd = LocalTime.of(10, 0),
    visitType = type,
    activePrison = true,
    createContact = true,
    createApplication = false,
  )
}
