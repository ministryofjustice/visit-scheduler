package uk.gov.justice.digital.hmpps.visitscheduler.utils.rules

import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonVisitRequestRules

interface VisitRequestRule<Application> {
  fun ruleCheck(application: Application, prisonVisitRequestRules: PrisonVisitRequestRules): Boolean
}
