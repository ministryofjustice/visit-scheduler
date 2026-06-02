package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections

import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus

interface VisitCountStats {
  val visitRestriction: VisitRestriction
  val visitStatus: VisitStatus
  val visitCount: Int
  val visitorCount: Int
}
