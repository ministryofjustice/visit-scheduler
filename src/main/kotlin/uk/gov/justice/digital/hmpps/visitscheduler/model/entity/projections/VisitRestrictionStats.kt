package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections

import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction

interface VisitRestrictionStats {
  val visitRestriction: VisitRestriction
  val count: Int
}
