package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections

interface VisitCountsForPrisonerByMonthAndYear {
  val bookedVisitsTotal: Long
  val month: Int
  val year: Int
}
