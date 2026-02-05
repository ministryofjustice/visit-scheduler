package uk.gov.justice.digital.hmpps.visitscheduler.utils.rules

import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonVisitRequestRules

interface Rule<T> {
  fun ruleCheck(t: T, prisonVisitRequestRules: PrisonVisitRequestRules): Boolean
}
