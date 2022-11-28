package uk.gov.justice.digital.hmpps.visitscheduler.helper

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
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
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository
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
    activePrison: Boolean = true
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
        _reference = reference
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
class PermittedSessionLocationHelper(
  private val sessionRepository: SessionTemplateRepository,
  private val repository: TestPermittedSessionLocationRepository
) {

  fun create(sessionTemplate: SessionTemplate): PermittedSessionLocation {
    val permittedSessionLocation = repository.saveAndFlush(
      PermittedSessionLocation(
        sessionTemplates = mutableListOf(sessionTemplate),
        prisonId = sessionTemplate.prison.id,
        prison = sessionTemplate.prison,
        levelOneCode = "A",
        levelTwoCode = "1",
        levelThreeCode = "W",
        levelFourCode = "001"
      )
    )

    sessionTemplate.permittedSessionLocations?.add(permittedSessionLocation)
    sessionRepository.saveAndFlush(sessionTemplate)

    return permittedSessionLocation
  }

  fun create(sessionTemplate: SessionTemplate, prisonHierarchies: List<AllowedPrisonHierarchy>): MutableList<PermittedSessionLocation> {
    val permittedSessionLocations = mutableListOf<PermittedSessionLocation>()

    for (prisonHierarchy in prisonHierarchies) {
      val permittedSessionLocation = repository.saveAndFlush(
        PermittedSessionLocation(
          sessionTemplates = mutableListOf(sessionTemplate),
          prisonId = sessionTemplate.prison.id,
          prison = sessionTemplate.prison,
          levelOneCode = prisonHierarchy.levelOneCode,
          levelTwoCode = prisonHierarchy.levelTwoCode,
          levelThreeCode = prisonHierarchy.levelThreeCode,
          levelFourCode = prisonHierarchy.levelFourCode
        )
      )

      permittedSessionLocations.add(permittedSessionLocation)
    }
    sessionTemplate.permittedSessionLocations?.addAll(permittedSessionLocations)
    sessionRepository.saveAndFlush(sessionTemplate)

    return permittedSessionLocations
  }
}

@Component
class SessionTemplateEntityHelper(
  private val sessionRepository: SessionTemplateRepository,
  private val prisonEntityHelper: PrisonEntityHelper
) {

  fun create(
    id: Long = 123,
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
    permittedSessionLocations: MutableList<PermittedSessionLocation>? = mutableListOf(),
    biWeekly: Boolean = false,
  ): SessionTemplate {

    val prison = prisonEntityHelper.create(prisonCode, activePrison)

    return sessionRepository.saveAndFlush(
      SessionTemplate(
        id = id,
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
        permittedSessionLocations = permittedSessionLocations,
        biWeekly = biWeekly
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
  private val permittedSessionLocationRepository: TestPermittedSessionLocationRepository
) {

  fun deleteAll() {
    sessionRepository.deleteAll()
    sessionRepository.flush()
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
