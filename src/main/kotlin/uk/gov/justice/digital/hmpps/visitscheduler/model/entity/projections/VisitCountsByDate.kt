package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections

import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import java.time.LocalDate

interface VisitCountsByDate {
  val visitDate: LocalDate
  val visitRestriction: VisitRestriction
  val visitCount: Int
}
