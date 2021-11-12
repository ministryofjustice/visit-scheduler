package uk.gov.justice.digital.hmpps.visitscheduler.helper

import uk.gov.justice.digital.hmpps.visitscheduler.jpa.SessionFrequency
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.VisitRepository
import java.time.LocalDate
import java.time.LocalDateTime

class VisitBuilder(
  private val repository: VisitRepository,
  private var visit: Visit,
) {

  fun build(): VisitBuilder {
    return this
  }

  fun save() {
    repository.saveAndFlush(visit)
  }

  fun buildAndSave() {
    build()
    repository.saveAndFlush(visit)
  }

  fun withVisitDateTime(visitDateTime: LocalDateTime): VisitBuilder {
    this.visit = visit.copy(visitDateTime = visitDateTime)
    return this
  }

  fun withPrisonerId(prisonerId: String): VisitBuilder {
    this.visit = visit.copy(prisonerId = prisonerId)
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
    visitDateTime = LocalDateTime.of(2021, 10, 23, 10, 30),
    active = true,
    visitType = VisitType.STANDARD_SOCIAL,
    prisonId = "MDI",
    visitStatus = VisitStatus.RESERVED,
    visitRoom = "123c"
  )
}

class SessionTemplateBuilder(
  private val repository: SessionTemplateRepository,
  private var sessionTemplate: SessionTemplate,
) {

  fun build(): SessionTemplateBuilder {
    return this
  }

  fun save() {
    repository.saveAndFlush(sessionTemplate)
  }

  fun buildAndSave() {
    build()
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
