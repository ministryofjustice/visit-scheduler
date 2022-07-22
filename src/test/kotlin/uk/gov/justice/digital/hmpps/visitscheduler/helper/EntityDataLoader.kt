package uk.gov.justice.digital.hmpps.visitscheduler.helper

import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitSupport
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class VisitBuilder(
  private val repository: VisitRepository,
  private var visit: Visit,
) {

  fun save(): Visit {
    return repository.saveAndFlush(visit)
  }

  fun withPrisonerId(prisonerId: String): VisitBuilder {
    this.visit = visit.copy(prisonerId = prisonerId)
    return this
  }

  fun withPrisonId(prisonId: String): VisitBuilder {
    this.visit = visit.copy(prisonId = prisonId)
    return this
  }

  fun withVisitRoom(room: String): VisitBuilder {
    this.visit = visit.copy(visitRoom = room)
    return this
  }

  fun withVisitType(type: VisitType): VisitBuilder {
    this.visit = visit.copy(visitType = type)
    return this
  }

  fun withVisitStatus(status: VisitStatus): VisitBuilder {
    this.visit = visit.copy(visitStatus = status)
    return this
  }

  fun withVisitStart(visitDateTime: LocalDateTime): VisitBuilder {
    this.visit = visit.copy(visitStart = visitDateTime)
    return this
  }

  fun withVisitEnd(visitDateTime: LocalDateTime): VisitBuilder {
    this.visit = visit.copy(visitEnd = visitDateTime)
    return this
  }
}

fun visitCreator(
  repository: VisitRepository,
  visit: Visit = defaultVisit(),
): VisitBuilder {
  return VisitBuilder(repository, visit)
}

fun visitDeleter(
  repository: VisitRepository,
) {
  repository.deleteAll()
  repository.flush()
}

fun defaultVisit(): Visit {
  return Visit(
    prisonerId = "AF12345G",
    prisonId = "MDI",
    visitRoom = "3B",
    visitStart = LocalDateTime.of(2021, 10, 23, 10, 30),
    visitEnd = LocalDateTime.of(2021, 10, 23, 11, 30),
    visitType = SOCIAL,
    visitStatus = RESERVED,
    visitRestriction = OPEN,
  )
}

fun visitContactCreator(
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

fun visitVisitorCreator(
  visit: Visit,
  nomisPersonId: Long,
) {
  visit.visitors.add(
    VisitVisitor(
      nomisPersonId = nomisPersonId,
      visitId = visit.id,
      visit = visit
    )
  )
}

fun visitSupportCreator(
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

fun visitNoteCreator(
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

class SessionTemplateBuilder(
  private val repository: SessionTemplateRepository,
  private var sessionTemplate: SessionTemplate,
) {

  fun save(): SessionTemplate =
    repository.saveAndFlush(sessionTemplate)

  fun withStartTime(startTime: LocalTime): SessionTemplateBuilder {
    this.sessionTemplate = sessionTemplate.copy(startTime = startTime)
    return this
  }

  fun withEndTime(endTime: LocalTime): SessionTemplateBuilder {
    this.sessionTemplate = sessionTemplate.copy(endTime = endTime)
    return this
  }
}

fun sessionTemplateCreator(
  repository: SessionTemplateRepository,
  sessionTemplate: SessionTemplate = defaultSessionTemplate()
): SessionTemplateBuilder {
  return SessionTemplateBuilder(repository, sessionTemplate)
}

fun sessionTemplateDeleter(
  repository: SessionTemplateRepository,
) {
  repository.deleteAll()
  repository.flush()
}

fun defaultSessionTemplate(): SessionTemplate {
  return sessionTemplate(
    prisonId = "MDI",
    validFromDate = LocalDate.of(2021, 10, 23),
    openCapacity = 5,
    closedCapacity = 1,
    visitRoom = "3B",
    visitType = SOCIAL
  )
}
