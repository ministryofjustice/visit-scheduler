package uk.gov.justice.digital.hmpps.visitscheduler.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class ExpiredVisitTaskConfiguration(
  @Value("\${task.expired-visit.enabled:false}") val expiredVisitTaskEnabled: Boolean,
) {
  companion object {
    const val LOCK_AT_LEAST_FOR = "PT5M"
    const val LOCK_AT_MOST_FOR = "PT5M"
  }
}
