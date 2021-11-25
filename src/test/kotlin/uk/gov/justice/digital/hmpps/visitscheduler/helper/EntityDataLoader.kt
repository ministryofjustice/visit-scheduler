package uk.gov.justice.digital.hmpps.visitscheduler.helper

import uk.gov.justice.digital.hmpps.visitscheduler.jpa.SessionFrequency
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitVisitorPk
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.VisitVisitorRepository
import java.time.LocalDate
import java.time.LocalDateTime

class VisitBuilder(
  private val repository: VisitRepository,
  private var visit: Visit,
) {

  fun save(): Visit = repository.saveAndFlush(visit)

  fun withVisitStart(visitDateTime: LocalDateTime): VisitBuilder {
    this.visit = visit.copy(visitStart = visitDateTime)
    return this
  }

  fun withVisitEnd(visitDateTime: LocalDateTime): VisitBuilder {
    this.visit = visit.copy(visitEnd = visitDateTime)
    return this
  }

  fun withPrisonerId(prisonerId: String): VisitBuilder {
    this.visit = visit.copy(prisonerId = prisonerId)
    return this
  }

  fun withPrisonId(prisonId: String): VisitBuilder {
    this.visit = visit.copy(prisonId = prisonId)
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
    visitStart = LocalDateTime.of(2021, 10, 23, 10, 30),
    visitEnd = LocalDateTime.of(2021, 10, 23, 11, 30),
    visitType = VisitType.STANDARD_SOCIAL,
    prisonId = "MDI",
    status = VisitStatus.RESERVED,
    visitRoom = "123c"
  )
}

fun visitVisitorCreator(
  repository: VisitVisitorRepository,
  contactId: Long,
  visitId: Long
) {
  repository.saveAndFlush(VisitVisitor(VisitVisitorPk(contactId = contactId, visitId = visitId)))
}

class SessionTemplateBuilder(
  private val repository: SessionTemplateRepository,
  private var sessionTemplate: SessionTemplate,
) {
  fun save() {
    repository.saveAndFlush(sessionTemplate)
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
    frequency = SessionFrequency.DAILY.name,
    openCapacity = 5,
    closedCapacity = 1,
    visitRoom = "3B",
    restrictions = "Restricted to B wing",
    visitType = "Social"
  )
}
