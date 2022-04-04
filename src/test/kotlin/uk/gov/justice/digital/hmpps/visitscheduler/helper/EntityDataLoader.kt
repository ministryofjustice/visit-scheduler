package uk.gov.justice.digital.hmpps.visitscheduler.helper

import uk.gov.justice.digital.hmpps.visitscheduler.jpa.SessionFrequency
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitContact
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitNoteType
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitSupport
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.VisitRepository
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

  fun withRestriction(restriction: VisitRestriction): VisitBuilder {
    this.visit = visit.copy(visitRestriction = restriction)
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
    visitRoom = "123c",
    visitStart = LocalDateTime.of(2021, 10, 23, 10, 30),
    visitEnd = LocalDateTime.of(2021, 10, 23, 11, 30),
    visitType = VisitType.STANDARD_SOCIAL,
    visitStatus = VisitStatus.RESERVED,
    visitRestriction = VisitRestriction.OPEN,
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
    startDate = LocalDate.of(2021, 10, 23),
    frequency = SessionFrequency.DAILY,
    openCapacity = 5,
    closedCapacity = 1,
    visitRoom = "3B",
    restrictions = "Restricted to B wing",
    visitType = VisitType.STANDARD_SOCIAL
  )
}
