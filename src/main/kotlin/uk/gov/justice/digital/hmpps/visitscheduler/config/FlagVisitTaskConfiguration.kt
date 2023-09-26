package uk.gov.justice.digital.hmpps.visitscheduler.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class FlagVisitTaskConfiguration(
  @Value("\${task.log-non-associations.enabled:false}") val flagVisitsEnabled: Boolean,
  @Value("\${task.log-non-associations.number-of-days-ahead:30}") val numberOfDaysAhead: Long,
) {
  companion object {
    const val LOCK_AT_LEAST_FOR = "PT60M"
    const val LOCK_AT_MOST_FOR = "PT60M"
    const val THREAD_SLEEP_TIME_IN_MILLISECONDS: Long = 500
  }
}
