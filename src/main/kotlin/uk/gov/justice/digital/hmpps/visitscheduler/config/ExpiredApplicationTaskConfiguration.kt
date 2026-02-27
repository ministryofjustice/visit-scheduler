package uk.gov.justice.digital.hmpps.visitscheduler.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class ExpiredApplicationTaskConfiguration(
  @param:Value("\${task.delete.expired-applications.enabled:true}") val expiredApplicationTaskEnabled: Boolean,
  @param:Value("\${task.delete.expired-applications.validity-minutes:1440}") val deleteExpiredApplicationsAfterMinutes: Int,
) {
  companion object {
    const val LOCK_AT_LEAST_FOR = "PT5M"
    const val LOCK_AT_MOST_FOR = "PT5M"
  }
}
