package uk.gov.justice.digital.hmpps.visitscheduler.helper

import org.junit.jupiter.api.Assertions.assertNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdatePrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UserClientDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.BOOKED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventAttributeType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionTemplateVisitOrderRestrictionType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.SYSTEM
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VSIPReport
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.ActionedBy
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonUserClient
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VSIPReporting
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitExternalSystemDetails
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNotifyHistory
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitSupport
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEventAttribute
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplateExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplateUserClient
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionCategoryGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionPrisonerCategory
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.SessionIncentiveLevelGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.SessionPrisonerIncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.PermittedSessionLocation
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonExcludeDateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonUserClientRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionCategoryGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionIncentiveLevelGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionLocationGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateExcludeDateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateUserClientRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestActionedByRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestApplicationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestEventAuditRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestPermittedSessionLocationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestPrisonRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestPrisonUserClientRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionSlotRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VSIPReportingRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitNotifyHistoryRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.jvm.optionals.getOrNull

@Component
@Transactional
class PrisonEntityHelper(
  private val prisonRepository: TestPrisonRepository,
  private val prisonUserClientRepository: PrisonUserClientRepository,
) {

  companion object {
    fun createPrison(
      prisonCode: String = "MDI",
      activePrison: Boolean = true,
      policyNoticeDaysMin: Int = 2,
      policyNoticeDaysMax: Int = 28,
      maxTotalVisitors: Int = 6,
      maxAdultVisitors: Int = 3,
      maxChildVisitors: Int = 3,
      adultAgeYears: Int = 18,
    ): Prison = Prison(code = prisonCode, active = activePrison, policyNoticeDaysMin, policyNoticeDaysMax, maxTotalVisitors, maxAdultVisitors, maxChildVisitors, adultAgeYears)

    fun createPrisonDto(
      prisonCode: String = "AWE",
      activePrison: Boolean = true,
      clients: List<UserClientDto> = mutableListOf(),
      policyNoticeDaysMin: Int = 2,
      policyNoticeDaysMax: Int = 28,
      maxTotalVisitors: Int = 6,
      maxAdultVisitors: Int = 3,
      maxChildVisitors: Int = 3,
      adultAgeYears: Int = 18,
    ): PrisonDto = PrisonDto(
      code = prisonCode,
      active = activePrison,
      policyNoticeDaysMin = policyNoticeDaysMin,
      policyNoticeDaysMax = policyNoticeDaysMax,
      maxTotalVisitors = maxTotalVisitors,
      maxAdultVisitors = maxAdultVisitors,
      maxChildVisitors = maxChildVisitors,
      adultAgeYears = adultAgeYears,
      clients = clients,
    )

    fun updatePrisonDto(
      policyNoticeDaysMin: Int = 10,
      policyNoticeDaysMax: Int = 20,
      maxTotalVisitors: Int = 4,
      maxAdultVisitors: Int = 2,
      maxChildVisitors: Int = 2,
      adultAgeYears: Int = 16,
    ): UpdatePrisonDto = UpdatePrisonDto(policyNoticeDaysMin, policyNoticeDaysMax, maxTotalVisitors, maxAdultVisitors, maxChildVisitors, adultAgeYears)
  }

  @Transactional(propagation = REQUIRES_NEW)
  fun create(
    prisonCode: String = "MDI",
    activePrison: Boolean = true,
    excludeDates: List<LocalDate> = listOf(),
    policyNoticeDaysMin: Int = 2,
    policyNoticeDaysMax: Int = 28,
    dontMakeClient: Boolean = false,
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

      if (!dontMakeClient) {
        prisonUserClientRepository.saveAndFlush(
          createPrisonUserClient(
            prison = prison,
            active = true,
            userType = STAFF,
          ),
        )

        prisonUserClientRepository.saveAndFlush(
          createPrisonUserClient(
            prison = prison,
            active = true,
            userType = PUBLIC,
          ),
        )
      }
    } else {
      prison.active = activePrison
    }
    prison?.let {
      prison.excludeDates.addAll(excludeDates.map { PrisonExcludeDate(prisonId = prison.id, prison = prison, excludeDate = it, actionedBy = "TEST_USER") })
    }
    return prison!!
  }

  private fun createPrisonUserClient(prison: Prison, active: Boolean, userType: UserType): PrisonUserClient {
    val prisonUserClient = PrisonUserClient(prison = prison, prisonId = prison.id, active = active, userType = userType)
    prison.clients.add(prisonUserClient)
    return prisonUserClient
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

  fun createVisit(
    application: Application,
    visit: Visit,
  ): Visit {
    visit.addApplication(application)

    with(application.visitContact!!) {
      visit.visitContact = VisitContact(visit = visit, visitId = visit.id, name = name, telephone = telephone, email = email)
    }

    application.support?.let {
      visit.support = VisitSupport(visit = visit, visitId = visit.id, description = it.description)
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

  fun createFromApplication(
    application: Application,
    visitStatus: VisitStatus = BOOKED,
    visitSubStatus: VisitSubStatus = VisitSubStatus.AUTO_APPROVED,
    sessionTemplate: SessionTemplate,
    outcomeStatus: OutcomeStatus? = null,
  ): Visit {
    val visit = create(
      visitStatus = visitStatus,
      visitSubStatus = visitSubStatus,
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
      userType = application.userType,
    )

    return createVisit(application, visit)
  }

  fun createFromApplication(
    application: Application,
    visitStatus: VisitStatus = BOOKED,
    visitSubStatus: VisitSubStatus = VisitSubStatus.AUTO_APPROVED,
    outcomeStatus: OutcomeStatus? = null,
  ): Visit {
    val visit = create(
      visitStatus = visitStatus,
      visitSubStatus = visitSubStatus,
      prisonerId = application.prisonerId,
      slotDate = application.sessionSlot.slotDate,
      visitStart = application.sessionSlot.slotStart.toLocalTime(),
      visitEnd = application.sessionSlot.slotEnd.toLocalTime(),
      visitType = application.visitType,
      visitRestriction = application.restriction,
      outcomeStatus = outcomeStatus,
      createApplication = false,
      prisonCode = application.prison.code,
      activePrison = true,
      visitRoom = "Visit Room 1",
      userType = application.userType,
    )

    return createVisit(application, visit)
  }

  @Transactional
  fun create(
    visitStatus: VisitStatus = BOOKED,
    visitSubStatus: VisitSubStatus = VisitSubStatus.AUTO_APPROVED,
    sessionTemplate: SessionTemplate,
    prisonerId: String = "testPrisonerId",
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
    visitContact: ContactDto? = null,
    userType: UserType? = STAFF,
  ): Visit {
    val prison = prisonEntityHelper.create(prisonCode, activePrison)
    val sessionSlot = sessionSlotEntityHelper.create(sessionTemplate.reference, prison.id, slotDate, visitStart, visitEnd)

    val notSaved = Visit(
      visitStatus = visitStatus,
      visitSubStatus = visitSubStatus,
      prisonerId = prisonerId,
      prisonId = prison.id,
      prison = prison,
      visitRoom = visitRoom,
      sessionSlotId = sessionSlot.id,
      sessionSlot = sessionSlot,
      visitType = visitType,
      visitRestriction = visitRestriction,
      userType = userType!!,
    )

    notSaved.outcomeStatus = outcomeStatus

    val savedVisit = visitRepository.saveAndFlush(notSaved)
    if (visitContact != null) {
      createContact(visit = savedVisit, visitContact.name, visitContact.telephone, visitContact.email)
    }

    return if (createApplication) {
      savedVisit.addApplication(applicationEntityHelper.create(savedVisit))
      savedVisit
    } else {
      savedVisit
    }
  }

  @Transactional
  fun create(
    visitStatus: VisitStatus = BOOKED,
    visitSubStatus: VisitSubStatus = VisitSubStatus.AUTO_APPROVED,
    prisonerId: String = "testPrisonerId",
    prisonCode: String,
    visitRoom: String,
    slotDate: LocalDate,
    visitStart: LocalTime,
    visitEnd: LocalTime,
    visitType: VisitType,
    visitRestriction: VisitRestriction = VisitRestriction.OPEN,
    activePrison: Boolean,
    outcomeStatus: OutcomeStatus? = null,
    createApplication: Boolean = true,
    createContact: Boolean = false,
    userType: UserType? = STAFF,
  ): Visit {
    val prison = prisonEntityHelper.create(prisonCode, activePrison)
    val sessionSlot = sessionSlotEntityHelper.create(prison.id, slotDate, visitStart, visitEnd)

    val notSaved = Visit(
      visitStatus = visitStatus,
      visitSubStatus = visitSubStatus,
      prisonerId = prisonerId,
      prisonId = prison.id,
      prison = prison,
      visitRoom = visitRoom,
      sessionSlotId = sessionSlot.id,
      sessionSlot = sessionSlot,
      visitType = visitType,
      visitRestriction = visitRestriction,
      userType = userType!!,
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
    phone: String? = "0123456789",
    email: String? = "email@example.com",
  ) {
    visit.visitContact = VisitContact(
      visitId = visit.id,
      name = name,
      telephone = phone,
      email = email,
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
    description: String,
  ) {
    visit.support =
      VisitSupport(
        visitId = visit.id,
        description = description,
        visit = visit,
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

  fun save(visit: Visit): Visit = visitRepository.saveAndFlush(visit)

  fun getBookedVisit(reference: String): Visit? = visitRepository.findBookedVisit(reference)

  fun createVisitExternalSystemClientReference(visit: Visit, clientReference: String) {
    visit.visitExternalSystemDetails = VisitExternalSystemDetails(visitId = visit.id, clientReference = clientReference, clientName = "MDI", visit = visit)
  }
}

@Component
@Transactional
class EventAuditEntityHelper(
  private val eventAuditRepository: TestEventAuditRepository,
  private val testActionedByRepository: TestActionedByRepository,
) {

  fun createForVisitAndApplication(
    visit: Visit,
    actionedByValues: List<String> = listOf("ACTIONED_BY"),
    applicationMethodType: ApplicationMethodType = ApplicationMethodType.PHONE,
    types: List<EventAuditType>,
    userTypes: List<UserType>,
    text: String? = null,
  ): List<EventAudit> {
    val application = visit.getLastApplication() ?: throw IllegalArgumentException("Must have an application")
    val eventAuditList = mutableListOf<EventAudit>()

    types.forEachIndexed { index, eventAuditType ->

      eventAuditList.add(
        create(
          reference = visit.reference,
          applicationReference = application.reference,
          sessionTemplateReference = application.sessionSlot.sessionTemplateReference,
          actionedByValue = actionedByValues[index],
          type = eventAuditType,
          applicationMethodType = applicationMethodType,
          text = text,
          userType = userTypes[index],
        ),
      )
    }

    return eventAuditList
  }

  fun create(
    visit: Visit,
    actionedByValue: String = "ACTIONED_BY",
    applicationMethodType: ApplicationMethodType = ApplicationMethodType.PHONE,
    type: EventAuditType = BOOKED_VISIT,
    text: String? = null,
  ): EventAudit = create(
    reference = visit.reference,
    applicationReference = visit.getLastApplication()?.reference ?: "",
    sessionTemplateReference = visit.sessionSlot.sessionTemplateReference,
    actionedByValue = actionedByValue,
    type = type,
    applicationMethodType = applicationMethodType,
    text = text,
  )

  fun create(
    application: Application,
    actionedByValue: String = "ACTIONED_BY",
    applicationMethodType: ApplicationMethodType = ApplicationMethodType.PHONE,
    type: EventAuditType = BOOKED_VISIT,
    text: String? = null,
  ): EventAudit = create(
    applicationReference = application.reference,
    sessionTemplateReference = application.sessionSlot.sessionTemplateReference,
    actionedByValue = actionedByValue,
    type = type,
    applicationMethodType = applicationMethodType,
    text = text,
  )

  fun create(
    reference: String = "",
    applicationReference: String = "",
    actionedByValue: String = "ACTIONED_BY",
    sessionTemplateReference: String? = "sessionTemplateReference",
    applicationMethodType: ApplicationMethodType = ApplicationMethodType.PHONE,
    type: EventAuditType = BOOKED_VISIT,
    text: String?,
    userType: UserType = STAFF,
  ): EventAudit {
    val actionedBy = createOrGetActionBy(actionedByValue, userType)

    return save(
      EventAudit(
        actionedBy = actionedBy,
        bookingReference = reference,
        applicationReference = applicationReference,
        sessionTemplateReference = sessionTemplateReference,
        type = type,
        applicationMethodType = applicationMethodType,
        text = text,
      ),
    )
  }

  private fun createOrGetActionBy(actionedByValue: String? = null, userType: UserType): ActionedBy {
    if (userType == SYSTEM) {
      assertNull(actionedByValue)
    }

    val bookerReference: String? = if (userType == PUBLIC) actionedByValue else null
    val userName: String? = if (userType == STAFF) actionedByValue else null

    val actionBy = testActionedByRepository.findActionedBy(actionedByValue, userType)

    return actionBy ?: testActionedByRepository.saveAndFlush(
      ActionedBy(
        bookerReference = bookerReference,
        userName = userName,
        userType = userType,
      ),
    )
  }

  fun save(event: EventAudit): EventAudit = eventAuditRepository.saveAndFlush(event)
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
  private val sessionTemplateExcludeDateRepository: SessionTemplateExcludeDateRepository,
  private val sessionTemplateUserClientRepository: SessionTemplateUserClientRepository,
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
    includeLocationGroupType: Boolean = true,
    includeCategoryGroupType: Boolean = true,
    includeIncentiveGroupType: Boolean = true,
    excludeDates: MutableList<LocalDate> = mutableListOf(),
    clients: List<UserClientDto> = listOf(UserClientDto(STAFF, true), UserClientDto(PUBLIC, true)),
    visitOrderRestrictionType: SessionTemplateVisitOrderRestrictionType = SessionTemplateVisitOrderRestrictionType.VO_PVO,
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
      includeLocationGroupType = includeLocationGroupType,
      includeCategoryGroupType = includeCategoryGroupType,
      includeIncentiveGroupType = includeIncentiveGroupType,
      excludeDates = excludeDates,
      clients = clients,
      visitOrderRestrictionType = visitOrderRestrictionType,
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
    includeLocationGroupType: Boolean = true,
    includeCategoryGroupType: Boolean = true,
    includeIncentiveGroupType: Boolean = true,
    excludeDates: MutableList<LocalDate> = mutableListOf(),
    clients: List<UserClientDto> = listOf(UserClientDto(STAFF, true), UserClientDto(PUBLIC, true)),
    visitOrderRestrictionType: SessionTemplateVisitOrderRestrictionType = SessionTemplateVisitOrderRestrictionType.VO_PVO,
  ): SessionTemplate {
    val sessionTemplate = sessionRepository.saveAndFlush(
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
        includeLocationGroupType = includeLocationGroupType,
        includeCategoryGroupType = includeCategoryGroupType,
        includeIncentiveGroupType = includeIncentiveGroupType,
        visitOrderRestriction = visitOrderRestrictionType,
      ),
    )

    excludeDates.forEach { excludeDate ->
      sessionTemplateExcludeDateRepository.saveAndFlush(
        SessionTemplateExcludeDate(
          sessionTemplateId = sessionTemplate.id,
          sessionTemplate = sessionTemplate,
          excludeDate = excludeDate,
          actionedBy = "TEST_USER",
        ),
      )
    }

    clients.forEach { userType ->
      sessionTemplateUserClientRepository.saveAndFlush(
        SessionTemplateUserClient(
          sessionTemplateId = sessionTemplate.id,
          sessionTemplate = sessionTemplate,
          userType = userType.userType,
          active = userType.active,
        ),
      )
    }

    return sessionTemplate
  }
}

@Transactional
@Component
class DeleteEntityHelper(
  private val visitRepository: VisitRepository,
  private val prisonRepository: TestPrisonRepository,
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
  private val testPrisonUserClientRepository: TestPrisonUserClientRepository,
  private val testActionedByRepository: TestActionedByRepository,
  private val visitNotifyHistoryRepository: VisitNotifyHistoryRepository,
) {

  @Transactional(propagation = REQUIRES_NEW)
  fun deleteAll() {
    println("Delete all")
    sessionRepository.deleteAll()
    sessionRepository.flush()
    sessionLocationGroupRepository.deleteAll()
    sessionLocationGroupRepository.flush()
    permittedSessionLocationRepository.deleteAll()
    permittedSessionLocationRepository.flush()
    visitRepository.deleteAll()
    visitRepository.flush()
    permittedSessionLocationRepository.deleteAll()
    permittedSessionLocationRepository.flush()
    testPrisonUserClientRepository.deleteAll()
    testPrisonUserClientRepository.flush()
    prisonRepository.deleteAll()
    prisonRepository.flush()
    prisonExcludeDateRepository.deleteAll()
    prisonExcludeDateRepository.flush()
    sessionCategoryGroupRepository.deleteAll()
    sessionCategoryGroupRepository.flush()
    visitNotifyHistoryRepository.deleteAll()
    visitNotifyHistoryRepository.flush()
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
    testActionedByRepository.deleteAll()
    testActionedByRepository.flush()
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

  fun get(reportName: VSIPReport): VSIPReporting? = vsipReportingRepository.findById(reportName).getOrNull()
}

@Component
@Transactional
class VisitNotificationEventHelper(
  private val visitNotificationEventRepository: TestVisitNotificationEventRepository,
) {
  fun create(
    visit: Visit,
    notificationEventType: NotificationEventType,
    notificationAttributes: Map<NotificationEventAttributeType, String> = emptyMap(),
  ): VisitNotificationEvent {
    val notificationEvent = visitNotificationEventRepository.save(
      VisitNotificationEvent(
        visitId = visit.id,
        visit = visit,
        type = notificationEventType,
        bookingReference = visit.reference,
      ),
    )

    notificationAttributes.forEach {
      notificationEvent.visitNotificationEventAttributes.add(
        VisitNotificationEventAttribute(
          attributeName = it.key,
          attributeValue = it.value,
          visitNotificationEvent = notificationEvent,
          visitNotificationEventId = notificationEvent.id,
        ),
      )
    }

    return visitNotificationEventRepository.saveAndFlush(notificationEvent)
  }

  fun getVisitNotifications(
    visitBookingReference: String,
  ): List<VisitNotificationEvent> = visitNotificationEventRepository.findVisitNotificationEventByVisitReference(visitBookingReference)

  fun getAllVisitNotifications(): List<VisitNotificationEvent> = visitNotificationEventRepository.findAll()
}

@Component
@Transactional
class VisitNotifyHistoryHelper(
  private val visitNotifyHistoryRepository: VisitNotifyHistoryRepository,
) {
  fun create(visitNotifyHistory: VisitNotifyHistory) {
    visitNotifyHistoryRepository.save(visitNotifyHistory)
  }
}

class AllowedSessionLocationHierarchy(
  val levelOneCode: String,
  val levelTwoCode: String? = null,
  val levelThreeCode: String? = null,
  val levelFourCode: String? = null,
)
