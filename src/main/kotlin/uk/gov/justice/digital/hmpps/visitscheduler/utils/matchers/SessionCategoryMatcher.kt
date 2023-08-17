package uk.gov.justice.digital.hmpps.visitscheduler.utils.matchers

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType

@Component
class SessionCategoryMatcher : SessionGroupMatcher<Set<PrisonerCategoryType>> {
  override fun hasAllMatch(o1: Set<PrisonerCategoryType>, o2: Set<PrisonerCategoryType>): Boolean {
    return if ((o1.isEmpty() && o2.isEmpty()) || o2.isEmpty()) {
      true
    } else if (o1.isEmpty()) {
      false
    } else {
      o1.stream().allMatch {
        o2.contains(it)
      }
    }
  }

  override fun hasAnyMatch(o1: Set<PrisonerCategoryType>, o2: Set<PrisonerCategoryType>): Boolean {
    return o2.isEmpty() || o1.stream().anyMatch {
      o2.contains(it)
    }
  }
}
