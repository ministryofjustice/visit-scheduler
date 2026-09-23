package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections

import java.time.LocalDate

interface VisitCountsForPrisonerByDate {
  val bookedVisitsTotal: Long
  val visitDate: LocalDate
}
