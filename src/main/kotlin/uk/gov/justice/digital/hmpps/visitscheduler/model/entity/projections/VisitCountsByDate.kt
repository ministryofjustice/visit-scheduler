package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections

import java.time.LocalDate

interface VisitCountsByDate {
  val visitDate: LocalDate
  val visitCount: Int
}
