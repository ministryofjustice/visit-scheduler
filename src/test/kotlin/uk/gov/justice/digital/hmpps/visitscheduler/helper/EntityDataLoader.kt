package uk.gov.justice.digital.hmpps.visitscheduler.helper

import uk.gov.justice.digital.hmpps.visitscheduler.jpa.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.VisitRepository
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
    active = true
  )
}
