package uk.gov.justice.digital.hmpps.visitscheduler.utils.matchers

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel

@Component
class SessionIncentiveLevelMatcher : SessionGroupMatcher<Set<IncentiveLevel>> {
  override fun hasAllMatch(o1: Set<IncentiveLevel>, o2: Set<IncentiveLevel>): Boolean {
    return o2.isEmpty() || o1.stream().allMatch {
      o2.contains(it)
    }
  }

  override fun hasAnyMatch(o1: Set<IncentiveLevel>, o2: Set<IncentiveLevel>): Boolean {
    return o2.isEmpty() || o1.stream().anyMatch {
      o2.contains(it)
    }
  }
}
