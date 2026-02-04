package uk.gov.justice.digital.hmpps.visitscheduler.utils.rules

interface Rule<T> {
  fun ruleCheck(t: T): Boolean
}
