package uk.gov.justice.digital.hmpps.visitscheduler.helper

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation.REQUIRED
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitSupport
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionCategoryGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionPrisonerCategory
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevels
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.SessionIncentiveLevelGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.SessionPrisonerIncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.PermittedSessionLocation
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionCategoryGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionIncentiveLevelGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionLocationGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestPermittedSessionLocationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestPrisonRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Component
@Transactional
class PrisonEntityHelper(
  private val prisonRepository: TestPrisonRepository,
) {

  @Transactional(propagation = REQUIRED)
  fun create(prisonCode: String = "MDI", activePrison: Boolean = true): Prison {
    var prison = prisonRepository.findByCode(prisonCode)
    if (prison == null) {
      prison = prisonRepository.saveAndFlush(Prison(code = prisonCode, active = activePrison))
    } else {
      prison.active = activePrison
    }
    return prison!!
  }
}

@Component
@Transactional
class VisitEntityHelper(
  private val visitRepository: VisitRepository,
  private val prisonEntityHelper: PrisonEntityHelper,
) {

  fun create(
    visitStatus: VisitStatus = RESERVED,
    prisonerId: String = "FF0000AA",
    prisonCode: String = "MDI",
    visitRoom: String = "A1",
    visitStart: LocalDateTime = LocalDateTime.of((LocalDateTime.now().year + 1), 11, 1, 12, 30, 44),
    visitEnd: LocalDateTime = visitStart.plusHours(1),
    visitType: VisitType = VisitType.SOCIAL,
    visitRestriction: VisitRestriction = VisitRestriction.OPEN,
    reference: String = "",
    activePrison: Boolean = true,
    outcomeStatus: OutcomeStatus? = null,
    createdBy: String = "CREATED_BY",
    updatedBy: String? = null,
    sessionTemplateReference: String? = "sessionTemplateReference",
  ): Visit {
    val prison = prisonEntityHelper.create(prisonCode, activePrison)

    return visitRepository.saveAndFlush(
      Visit(
        visitStatus = visitStatus,
        prisonerId = prisonerId,
        prisonId = prison.id,
        prison = prison,
        visitRoom = visitRoom,
        visitStart = visitStart,
        visitEnd = visitEnd,
        visitType = visitType,
        visitRestriction = visitRestriction,
        _reference = reference,
        outcomeStatus = outcomeStatus,
        createdBy = createdBy,
        updatedBy = updatedBy,
        sessionTemplateReference = sessionTemplateReference,
      ),
    )
  }

  fun createContact(
    visit: Visit,
    name: String,
    phone: String,
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
    validFromDate: LocalDate = LocalDate.of(2021, 10, 23),
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
    biWeekly: Boolean = false,
    permittedCategories: MutableList<SessionCategoryGroup> = mutableListOf(),
    permittedIncentiveLevels: MutableList<SessionIncentiveLevelGroup> = mutableListOf(),
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
      biWeekly = biWeekly,
      permittedCategories = permittedCategories,
      permittedIncentiveLevels = permittedIncentiveLevels,
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
    permittedSessionGroups: MutableList<SessionLocationGroup> = mutableListOf(),
    biWeekly: Boolean = false,
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
        permittedSessionLocationGroups = permittedSessionGroups,
        biWeekly = biWeekly,
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
  private val sessionRepository: TestSessionTemplateRepository,
  private val permittedSessionLocationRepository: TestPermittedSessionLocationRepository,
  private val sessionLocationGroupRepository: SessionLocationGroupRepository,
  private val sessionCategoryGroupRepository: SessionCategoryGroupRepository,
) {

  fun deleteAll() {
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
    sessionCategoryGroupRepository.deleteAll()
    sessionCategoryGroupRepository.flush()
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
  fun create(name: String? = "Group A", prisonCode: String = "MDI", incentiveLevelsList: List<IncentiveLevels>): SessionIncentiveLevelGroup {
    val prison = prisonEntityHelper.create(prisonCode, true)

    val group = sessionIncentiveLevelGroupRepository.saveAndFlush(
      SessionIncentiveLevelGroup(
        prison = prison,
        prisonId = prison.id,
        name = name!!,
      ),
    )

    val permittedIncentiveLevelGroups = mutableListOf<SessionPrisonerIncentiveLevel>()

    for (prisonerIncentiveLevel in incentiveLevelsList) {
      val permittedIncentiveLevelGroup =
        SessionPrisonerIncentiveLevel(
          sessionCategoryGroupId = group.id,
          sessionIncentiveLevelGroup = group,
          prisonerIncentiveLevel = prisonerIncentiveLevel,
        )
      permittedIncentiveLevelGroups.add(permittedIncentiveLevelGroup)
    }

    group.sessionIncentiveLevels.addAll(permittedIncentiveLevelGroups)

    return group
  }
}

class AllowedSessionLocationHierarchy(
  val levelOneCode: String,
  val levelTwoCode: String? = null,
  val levelThreeCode: String? = null,
  val levelFourCode: String? = null,
)
