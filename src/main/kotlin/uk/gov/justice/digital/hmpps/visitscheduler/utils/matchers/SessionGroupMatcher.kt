package uk.gov.justice.digital.hmpps.visitscheduler.utils.matchers

interface SessionGroupMatcher<T> {
  fun hasAllMatch(o1: T, o2: T): Boolean
  fun hasAnyMatch(o1: T, o2: T): Boolean
}
