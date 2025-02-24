package uk.gov.justice.digital.hmpps.visitscheduler.utils.matchers

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerCategoryType

interface SessionGroupMatcher<T> {
  fun hasAllMatch(o1: Set<T>, o2: Set<T>): Boolean = if ((o1.isEmpty() && o2.isEmpty()) || o2.isEmpty()) {
    true
  } else if (o1.isEmpty()) {
    false
  } else {
    o1.stream().allMatch {
      o2.contains(it)
    }
  }

  fun hasAnyMatch(o1: Set<T>, o2: Set<T>): Boolean = o2.isEmpty() ||
    o1.stream().anyMatch {
      o2.contains(it)
    }
}

@Component
class SessionCategoryMatcher : SessionGroupMatcher<PrisonerCategoryType>

@Component
class SessionIncentiveLevelMatcher : SessionGroupMatcher<IncentiveLevel>
