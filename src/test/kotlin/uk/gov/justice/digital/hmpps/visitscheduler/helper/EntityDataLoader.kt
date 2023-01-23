package uk.gov.justice.digital.hmpps.visitscheduler.helper

import org.springframework.stereotype.Component
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
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.PermittedSessionLocation
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionLocationGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionLocationGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestPermittedSessionLocationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestPrisonRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Component
class PrisonEntityHelper(
  private val prisonRepository: TestPrisonRepository
) {

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
class VisitEntityHelper(
  private val visitRepository: VisitRepository,
  private val prisonEntityHelper: PrisonEntityHelper
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
        outcomeStatus = outcomeStatus
      )
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
      visit = visit
    )
  }

  fun createVisitor(
    visit: Visit,
    nomisPersonId: Long,
    visitContact: Boolean?
  ) {
    visit.visitors.add(
      VisitVisitor(
        nomisPersonId = nomisPersonId,
        visitId = visit.id,
        visit = visit,
        visitContact = visitContact
      )
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
        visit = visit
      )
    )
  }

  fun createNote(
    visit: Visit,
    text: String,
    type: VisitNoteType
  ) {
    visit.visitNotes.add(
      VisitNote(
        visitId = visit.id,
        type = type,
        text = text,
        visit = visit
      )
    )
  }

  fun save(visit: Visit): Visit {
    return visitRepository.saveAndFlush(visit)
  }
}

@Component
class SessionLocationGroupHelper(
  private val sessionRepository: SessionTemplateRepository,
  private val repository: TestPermittedSessionLocationRepository,
  private val sessionLocationGroupRepository: SessionLocationGroupRepository
) {

  fun create(sessionTemplate: SessionTemplate): SessionLocationGroup {

    val sessionLocations = mutableListOf(
      AllowedPrisonHierarchy(
        levelOneCode = "A",
        levelTwoCode = "1",
        levelThreeCode = "W",
        levelFourCode = "001"
      )
    )
    return create(sessionTemplate, sessionLocations)
  }

  fun create(sessionTemplate: SessionTemplate, prisonHierarchies: List<AllowedPrisonHierarchy>): SessionLocationGroup {

    val group = sessionLocationGroupRepository.saveAndFlush(
      SessionLocationGroup(
        prison = sessionTemplate.prison,
        prisonId = sessionTemplate.prisonId,
        name = "Group A"
      )
    )

    val permittedGroupLocations = mutableListOf<PermittedSessionLocation>()

    for (prisonHierarchy in prisonHierarchies) {
      val permittedSessionLocation = repository.saveAndFlush(
        PermittedSessionLocation(
          groupId = group.id,
          sessionLocationGroup = group,
          levelOneCode = prisonHierarchy.levelOneCode,
          levelTwoCode = prisonHierarchy.levelTwoCode,
          levelThreeCode = prisonHierarchy.levelThreeCode,
          levelFourCode = prisonHierarchy.levelFourCode
        )
      )
      permittedGroupLocations.add(permittedSessionLocation)
    }

    group.sessionLocations.addAll(permittedGroupLocations)

    val savedGroup = sessionLocationGroupRepository.saveAndFlush(group)

    sessionTemplate.permittedSessionGroups.add(savedGroup)
    sessionRepository.saveAndFlush(sessionTemplate)

    return savedGroup
  }
}

@Component
class SessionTemplateEntityHelper(
  private val sessionRepository: SessionTemplateRepository,
  private val prisonEntityHelper: PrisonEntityHelper
) {

  fun create(
    name: String = "sessionTemplate_",
    validFromDate: LocalDate = LocalDate.of(2021, 10, 23),
    validToDate: LocalDate? = null,
    closedCapacity: Int = 5,
    openCapacity: Int = 10,
    prisonCode: String = "MDI",
    visitRoom: String = "3B",
    visitType: VisitType = VisitType.SOCIAL,
    startTime: LocalTime = LocalTime.parse("09:00"),
    endTime: LocalTime = LocalTime.parse("10:00"),
    dayOfWeek: DayOfWeek = DayOfWeek.FRIDAY,
    activePrison: Boolean = true,
    permittedSessionGroups: MutableList<SessionLocationGroup> = mutableListOf(),
    biWeekly: Boolean = false,
    enhanced: Boolean = false
  ): SessionTemplate {

    val prison = prisonEntityHelper.create(prisonCode, activePrison)

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
        permittedSessionGroups = permittedSessionGroups,
        biWeekly = biWeekly,
        enhanced = enhanced
      )
    )
  }
}

@Transactional
@Component
class DeleteEntityHelper(
  private val visitRepository: VisitRepository,
  private val prisonRepository: PrisonRepository,
  private val sessionRepository: SessionTemplateRepository,
  private val permittedSessionLocationRepository: TestPermittedSessionLocationRepository,
  private val sessionLocationGroupRepository: SessionLocationGroupRepository
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
  }
}

class AllowedPrisonHierarchy(
  val levelOneCode: String,
  val levelTwoCode: String?,
  val levelThreeCode: String?,
  val levelFourCode: String?,
)
