package uk.gov.justice.digital.hmpps.visitscheduler.helper

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation.REQUIRED
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdatePrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VSIPReport
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VSIPReporting
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitSupport
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionCategoryGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionPrisonerCategory
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.SessionIncentiveLevelGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.SessionPrisonerIncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.PermittedSessionLocation
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonExcludeDateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionCategoryGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionIncentiveLevelGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionLocationGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestApplicationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestEventAuditRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestPermittedSessionLocationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestPrisonRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionSlotRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VSIPReportingRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.jvm.optionals.getOrNull

@Component
@Transactional
class PrisonEntityHelper(
  private val prisonRepository: TestPrisonRepository,
) {

  companion object {
    fun createPrison(
      prisonCode: String = "MDI",
      activePrison: Boolean = true,
      policyNoticeDaysMin: Int = 2,
      policyNoticeDaysMax: Int = 28,
    ): Prison {
      return Prison(code = prisonCode, active = activePrison, policyNoticeDaysMin, policyNoticeDaysMax)
    }

    fun createPrisonDto(
      prisonCode: String = "AWE",
      activePrison: Boolean = true,
      excludeDates: Set<LocalDate> = sortedSetOf(),
      policyNoticeDaysMin: Int = 2,
      policyNoticeDaysMax: Int = 28,
    ): PrisonDto {
      return PrisonDto(code = prisonCode, active = activePrison, policyNoticeDaysMin, policyNoticeDaysMax, excludeDates = excludeDates)
    }

    fun updatePrisonDto(
      policyNoticeDaysMin: Int = 10,
      policyNoticeDaysMax: Int = 20,
    ): UpdatePrisonDto {
      return UpdatePrisonDto(policyNoticeDaysMin, policyNoticeDaysMax)
    }
  }

  @Transactional(propagation = REQUIRED)
  fun create(
    prisonCode: String = "MDI",
    activePrison: Boolean = true,
    excludeDates: List<LocalDate> = listOf(),
    policyNoticeDaysMin: Int = 2,
    policyNoticeDaysMax: Int = 28,
  ): Prison {
    var prison = prisonRepository.findByCode(prisonCode)
    if (prison == null) {
      prison = prisonRepository.saveAndFlush(
        createPrison(
          prisonCode = prisonCode,
          activePrison = activePrison,
          policyNoticeDaysMin = policyNoticeDaysMin,
          policyNoticeDaysMax = policyNoticeDaysMax,
        ),
      )
    } else {
      prison.active = activePrison
    }
    prison?.let {
      prison.excludeDates.addAll(excludeDates.map { PrisonExcludeDate(prisonId = prison.id, prison = prison, it) })
    }
    return prison!!
  }
}

@Component
@Transactional
class VisitEntityHelper(
  private val visitRepository: VisitRepository,
  private val prisonEntityHelper: PrisonEntityHelper,
  private val sessionSlotEntityHelper: SessionSlotEntityHelper,
  private val applicationEntityHelper: ApplicationEntityHelper,
) {

  fun createFromApplication(
    application: Application,
    visitStatus: VisitStatus = BOOKED,
    sessionTemplate: SessionTemplate,
    outcomeStatus: OutcomeStatus? = null,
  ): Visit {
    val visit = create(
      visitStatus = visitStatus,
      sessionTemplate = sessionTemplate,
      prisonerId = application.prisonerId,
      slotDate = application.sessionSlot.slotDate,
      visitStart = application.sessionSlot.slotStart.toLocalTime(),
      visitEnd = application.sessionSlot.slotEnd.toLocalTime(),
      visitType = application.visitType,
      visitRestriction = application.restriction,
      outcomeStatus = outcomeStatus,
      createApplication = false,
      prisonCode = application.prison.code,
    )

    visit.addApplication(application)

    with(application.visitContact!!) {
      visit.visitContact = VisitContact(visit = visit, visitId = visit.id, name = name, telephone = telephone)
    }

    application.support.let {
      application.support.map { applicationSupport ->
        with(applicationSupport) {
          visit.support.add(VisitSupport(visit = visit, visitId = visit.id, type = type, text = text))
        }
      }
    }

    application.visitors.let {
      it.map { applicationVisitor ->
        with(applicationVisitor) {
          visit.visitors.add(VisitVisitor(visit = visit, visitId = visit.id, nomisPersonId = nomisPersonId, visitContact = contact))
        }
      }
    }

    return save(visit)
  }

  @Transactional
  fun create(
    visitStatus: VisitStatus = BOOKED,
    sessionTemplate: SessionTemplate,
    prisonerId: String = "FF0000AA",
    prisonCode: String = sessionTemplate.prison.code,
    visitRoom: String = sessionTemplate.visitRoom,
    slotDate: LocalDate = sessionTemplate.validFromDate,
    visitStart: LocalTime = sessionTemplate.startTime,
    visitEnd: LocalTime = sessionTemplate.endTime,
    visitType: VisitType = sessionTemplate.visitType,
    visitRestriction: VisitRestriction = VisitRestriction.OPEN,
    activePrison: Boolean = sessionTemplate.prison.active,
    outcomeStatus: OutcomeStatus? = null,
    createApplication: Boolean = true,
    createContact: Boolean = false,
  ): Visit {
    val prison = prisonEntityHelper.create(prisonCode, activePrison)
    val sessionSlot = sessionSlotEntityHelper.create(sessionTemplate.reference, prison.id, slotDate, visitStart, visitEnd)

    val notSaved = Visit(
      visitStatus = visitStatus,
      prisonerId = prisonerId,
      prisonId = prison.id,
      prison = prison,
      visitRoom = visitRoom,
      sessionSlotId = sessionSlot.id,
      sessionSlot = sessionSlot,
      visitType = visitType,
      visitRestriction = visitRestriction,
    )

    notSaved.outcomeStatus = outcomeStatus

    val savedVisit = visitRepository.saveAndFlush(notSaved)

    if (createContact) {
      createContact(visit = savedVisit)
    }

    return if (createApplication) {
      savedVisit.addApplication(applicationEntityHelper.create(savedVisit))
      savedVisit
    } else {
      savedVisit
    }
  }

  fun createContact(
    visit: Visit,
    name: String = "bob",
    phone: String = "0123456789",
  ) {
    visit.visitContact = VisitContact(
      visitId = visit.id,
      name = name,
      telephone = phone,
      visit = visit,
    )
  }

  fun createVisitor(
    visit: Visit,
    nomisPersonId: Long,
    visitContact: Boolean?,
  ) {
    visit.visitors.add(
      VisitVisitor(
        nomisPersonId = nomisPersonId,
        visitId = visit.id,
        visit = visit,
        visitContact = visitContact,
      ),
    )
  }

  fun createSupport(
    visit: Visit,
    name: String,
    details: String?,
  ) {
    visit.support.add(
      VisitSupport(
        type = name,
        visitId = visit.id,
        text = details,
        visit = visit,
      ),
    )
  }

  fun createNote(
    visit: Visit,
    text: String,
    type: VisitNoteType,
  ) {
    visit.visitNotes.add(
      VisitNote(
        visitId = visit.id,
        type = type,
        text = text,
        visit = visit,
      ),
    )
  }

  fun save(visit: Visit): Visit {
    return visitRepository.saveAndFlush(visit)
  }

  fun getBookedVisit(reference: String): Visit? {
    return visitRepository.findBookedVisit(reference)
  }
}

@Component
@Transactional
class EventAuditEntityHelper(
  private val eventAuditRepository: TestEventAuditRepository,
) {

  fun create(
    visit: Visit,
    actionedBy: String = "ACTIONED_BY",
    applicationMethodType: ApplicationMethodType = ApplicationMethodType.PHONE,
    type: EventAuditType = EventAuditType.BOOKED_VISIT,
  ): EventAudit {
    return create(
      reference = visit.reference,
      applicationReference = visit.getLastApplication()?.reference ?: "",
      sessionTemplateReference = visit.sessionSlot.sessionTemplateReference,
      actionedBy = actionedBy,
      type = type,
      applicationMethodType = applicationMethodType,
    )
  }

  fun create(
    application: Application,
    actionedBy: String = "ACTIONED_BY",
    applicationMethodType: ApplicationMethodType = ApplicationMethodType.PHONE,
    type: EventAuditType = EventAuditType.BOOKED_VISIT,
  ): EventAudit {
    return create(
      applicationReference = application.reference,
      sessionTemplateReference = application.sessionSlot.sessionTemplateReference,
      actionedBy = actionedBy,
      type = type,
      applicationMethodType = applicationMethodType,
    )
  }

  fun create(
    reference: String = "",
    applicationReference: String = "",
    actionedBy: String = "ACTIONED_BY",
    sessionTemplateReference: String? = "sessionTemplateReference",
    applicationMethodType: ApplicationMethodType = ApplicationMethodType.PHONE,
    type: EventAuditType = EventAuditType.BOOKED_VISIT,
  ): EventAudit {
    return save(
      EventAudit(
        actionedBy = actionedBy,
        bookingReference = reference,
        applicationReference = applicationReference,
        sessionTemplateReference = sessionTemplateReference,
        type = type,
        applicationMethodType = applicationMethodType,
      ),
    )
  }

  fun save(event: EventAudit): EventAudit {
    return eventAuditRepository.saveAndFlush(event)
  }
}

@Component
@Transactional
class SessionLocationGroupHelper(
  private val sessionLocationGroupRepository: SessionLocationGroupRepository,
  private val prisonEntityHelper: PrisonEntityHelper,
) {

  fun create(name: String? = "Group A", prisonCode: String = "MDI"): SessionLocationGroup {
    val sessionLocations = mutableListOf(
      AllowedSessionLocationHierarchy(
        levelOneCode = "A",
        levelTwoCode = "1",
        levelThreeCode = "W",
        levelFourCode = "001",
      ),
    )
    return create(name = name, prisonCode = prisonCode, sessionLocations)
  }

  fun create(name: String? = "Group A", prisonCode: String = "MDI", prisonHierarchies: List<AllowedSessionLocationHierarchy>): SessionLocationGroup {
    val prison = prisonEntityHelper.create(prisonCode, true)

    val group = sessionLocationGroupRepository.saveAndFlush(
      SessionLocationGroup(
        prison = prison,
        prisonId = prison.id,
        name = name!!,
      ),
    )

    val permittedGroupLocations = mutableListOf<PermittedSessionLocation>()

    for (prisonHierarchy in prisonHierarchies) {
      val permittedSessionLocation =
        PermittedSessionLocation(
          groupId = group.id,
          sessionLocationGroup = group,
          levelOneCode = prisonHierarchy.levelOneCode,
          levelTwoCode = prisonHierarchy.levelTwoCode,
          levelThreeCode = prisonHierarchy.levelThreeCode,
          levelFourCode = prisonHierarchy.levelFourCode,
        )
      permittedGroupLocations.add(permittedSessionLocation)
    }

    group.sessionLocations.addAll(permittedGroupLocations)

    return group
  }
}

@Component
@Transactional
class SessionTemplateEntityHelper(
  private val sessionRepository: TestSessionTemplateRepository,
  private val prisonEntityHelper: PrisonEntityHelper,
) {

  fun create(
    name: String = "sessionTemplate_",
    validFromDate: LocalDate = LocalDate.now(),
    validToDate: LocalDate? = null,
    closedCapacity: Int = 5,
    openCapacity: Int = 10,
    prisonCode: String = "MDI",
    visitRoom: String = "A1",
    visitType: VisitType = VisitType.SOCIAL,
    startTime: LocalTime = LocalTime.parse("09:00"),
    endTime: LocalTime = LocalTime.parse("10:00"),
    dayOfWeek: DayOfWeek = DayOfWeek.FRIDAY,
    activePrison: Boolean = true,
    permittedLocationGroups: MutableList<SessionLocationGroup> = mutableListOf(),
    weeklyFrequency: Int = 1,
    permittedCategories: MutableList<SessionCategoryGroup> = mutableListOf(),
    permittedIncentiveLevels: MutableList<SessionIncentiveLevelGroup> = mutableListOf(),
    isActive: Boolean = true,
  ): SessionTemplate {
    val prison = prisonEntityHelper.create(prisonCode, activePrison)

    return create(
      name = name + dayOfWeek,
      validFromDate = validFromDate,
      validToDate = validToDate,
      closedCapacity = closedCapacity,
      openCapacity = openCapacity,
      prison = prison,
      visitRoom = visitRoom,
      visitType = visitType,
      startTime = startTime,
      endTime = endTime,
      dayOfWeek = dayOfWeek,
      permittedSessionGroups = permittedLocationGroups,
      weeklyFrequency = weeklyFrequency,
      permittedCategories = permittedCategories,
      permittedIncentiveLevels = permittedIncentiveLevels,
      isActive = isActive,
    )
  }

  fun create(
    name: String = "sessionTemplate_",
    validFromDate: LocalDate = LocalDate.of(2021, 10, 23),
    validToDate: LocalDate? = null,
    closedCapacity: Int = 5,
    openCapacity: Int = 10,
    prison: Prison,
    visitRoom: String = "visitRoom",
    visitType: VisitType = VisitType.SOCIAL,
    startTime: LocalTime = LocalTime.parse("09:00"),
    endTime: LocalTime = LocalTime.parse("10:00"),
    dayOfWeek: DayOfWeek = DayOfWeek.FRIDAY,
    activePrison: Boolean = true,
    weeklyFrequency: Int = 1,
    isActive: Boolean = true,
    permittedSessionGroups: MutableList<SessionLocationGroup> = mutableListOf(),
    permittedCategories: MutableList<SessionCategoryGroup> = mutableListOf(),
    permittedIncentiveLevels: MutableList<SessionIncentiveLevelGroup> = mutableListOf(),
  ): SessionTemplate {
    return sessionRepository.saveAndFlush(
      SessionTemplate(
        name = name + dayOfWeek,
        validFromDate = validFromDate,
        validToDate = validToDate,
        closedCapacity = closedCapacity,
        openCapacity = openCapacity,
        prisonId = prison.id,
        prison = prison,
        visitRoom = visitRoom,
        visitType = visitType,
        startTime = startTime,
        endTime = endTime,
        dayOfWeek = dayOfWeek,
        weeklyFrequency = weeklyFrequency,
        active = isActive,
        permittedSessionLocationGroups = permittedSessionGroups,
        permittedSessionCategoryGroups = permittedCategories,
        permittedSessionIncentiveLevelGroups = permittedIncentiveLevels,
      ),
    )
  }
}

@Transactional
@Component
class DeleteEntityHelper(
  private val visitRepository: VisitRepository,
  private val prisonRepository: PrisonRepository,
  private val prisonExcludeDateRepository: PrisonExcludeDateRepository,
  private val sessionRepository: TestSessionTemplateRepository,
  private val permittedSessionLocationRepository: TestPermittedSessionLocationRepository,
  private val sessionLocationGroupRepository: SessionLocationGroupRepository,
  private val sessionCategoryGroupRepository: SessionCategoryGroupRepository,
  private val eventAuditRepository: TestEventAuditRepository,
  private val visitNotificationEventRepository: VisitNotificationEventRepository,
  private val vsipReportingRepository: VSIPReportingRepository,
  private val testApplicationRepository: TestApplicationRepository,
  private val testSessionSlotRepository: TestSessionSlotRepository,

) {

  fun deleteAll() {
    println("Delete all")
    sessionRepository.deleteAll()
    sessionRepository.flush()
    sessionLocationGroupRepository.deleteAll()
    sessionLocationGroupRepository.flush()
    permittedSessionLocationRepository.deleteAll()
    permittedSessionLocationRepository.flush()
    visitRepository.deleteAllInBatch()
    visitRepository.flush()
    permittedSessionLocationRepository.deleteAll()
    permittedSessionLocationRepository.flush()
    prisonRepository.deleteAll()
    prisonRepository.flush()
    prisonExcludeDateRepository.deleteAll()
    prisonExcludeDateRepository.flush()
    sessionCategoryGroupRepository.deleteAll()
    sessionCategoryGroupRepository.flush()
    eventAuditRepository.deleteAll()
    eventAuditRepository.flush()
    visitNotificationEventRepository.deleteAll()
    visitNotificationEventRepository.flush()
    vsipReportingRepository.deleteAll()
    vsipReportingRepository.flush()
    testApplicationRepository.deleteAll()
    testApplicationRepository.flush()
    testSessionSlotRepository.deleteAll()
    testSessionSlotRepository.flush()
    println("Delete all end")
  }
}

@Component
@Transactional
class SessionPrisonerCategoryHelper(
  private val sessionCategoryGroupRepository: SessionCategoryGroupRepository,
  private val prisonEntityHelper: PrisonEntityHelper,
) {
  fun create(name: String? = "Group A", prisonCode: String = "MDI"): SessionCategoryGroup {
    val sessionPrisonerCategories = mutableListOf(
      PrisonerCategoryType.A_PROVISIONAL,
      PrisonerCategoryType.A_STANDARD,
      PrisonerCategoryType.A_HIGH,
      PrisonerCategoryType.A_EXCEPTIONAL,
    )
    return create(name = name, prisonCode = prisonCode, sessionPrisonerCategories)
  }

  fun create(name: String? = "Group A", prisonCode: String = "MDI", prisonerCategories: List<PrisonerCategoryType>): SessionCategoryGroup {
    val prison = prisonEntityHelper.create(prisonCode, true)

    val group = sessionCategoryGroupRepository.saveAndFlush(
      SessionCategoryGroup(
        prison = prison,
        prisonId = prison.id,
        name = name!!,
      ),
    )

    val permittedCategoryGroups = mutableListOf<SessionPrisonerCategory>()

    for (prisonerCategory in prisonerCategories) {
      val permittedCategoryGroup =
        SessionPrisonerCategory(
          sessionCategoryGroupId = group.id,
          sessionCategoryGroup = group,
          prisonerCategoryType = prisonerCategory,
        )
      permittedCategoryGroups.add(permittedCategoryGroup)
    }

    group.sessionCategories.addAll(permittedCategoryGroups)

    return group
  }
}

@Component
@Transactional
class SessionPrisonerIncentiveLevelHelper(
  private val sessionIncentiveLevelGroupRepository: SessionIncentiveLevelGroupRepository,
  private val prisonEntityHelper: PrisonEntityHelper,
) {
  fun create(name: String? = "Group A", prisonCode: String = "MDI", incentiveLevelList: List<IncentiveLevel>): SessionIncentiveLevelGroup {
    val prison = prisonEntityHelper.create(prisonCode, true)

    val group = sessionIncentiveLevelGroupRepository.saveAndFlush(
      SessionIncentiveLevelGroup(
        prison = prison,
        prisonId = prison.id,
        name = name!!,
      ),
    )

    val permittedIncentiveLevelGroups = mutableListOf<SessionPrisonerIncentiveLevel>()

    for (prisonerIncentiveLevel in incentiveLevelList) {
      val permittedIncentiveLevelGroup =
        SessionPrisonerIncentiveLevel(
          sessionIncentiveGroupId = group.id,
          sessionIncentiveLevelGroup = group,
          prisonerIncentiveLevel = prisonerIncentiveLevel,
        )
      permittedIncentiveLevelGroups.add(permittedIncentiveLevelGroup)
    }

    group.sessionIncentiveLevels.addAll(permittedIncentiveLevelGroups)

    return group
  }
}

@Component
@Transactional
class VsipReportingEntityHelper(
  private val vsipReportingRepository: VSIPReportingRepository,
) {
  fun create(reportName: VSIPReport, reportDate: LocalDate?) {
    vsipReportingRepository.save(VSIPReporting(reportName, reportDate))
  }

  fun get(reportName: VSIPReport): VSIPReporting? {
    return vsipReportingRepository.findById(reportName).getOrNull()
  }
}

@Component
@Transactional
class VisitNotificationEventHelper(
  private val visitNotificationEventRepository: TestVisitNotificationEventRepository,
) {
  fun create(
    visitBookingReference: String,
    notificationEventType: NotificationEventType,
  ): VisitNotificationEvent {
    return visitNotificationEventRepository.saveAndFlush(
      VisitNotificationEvent(
        bookingReference = visitBookingReference,
        type = notificationEventType,
      ),
    )
  }

  fun getVisitNotifications(
    visitBookingReference: String,
  ): List<VisitNotificationEvent> = visitNotificationEventRepository.findByBookingReference(visitBookingReference)
}

class AllowedSessionLocationHierarchy(
  val levelOneCode: String,
  val levelTwoCode: String? = null,
  val levelThreeCode: String? = null,
  val levelFourCode: String? = null,
)
