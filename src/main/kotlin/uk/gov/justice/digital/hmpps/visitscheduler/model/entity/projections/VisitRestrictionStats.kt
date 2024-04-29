package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections

import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction

interface VisitRestrictionStats {
  val visitRestriction: VisitRestriction
  val count: Int
}
