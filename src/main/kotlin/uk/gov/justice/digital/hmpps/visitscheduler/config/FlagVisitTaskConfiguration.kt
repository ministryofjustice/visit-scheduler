package uk.gov.justice.digital.hmpps.visitscheduler.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class FlagVisitTaskConfiguration(
  @Value("\${task.flag-visits.enabled:false}") val flagVisitsEnabled: Boolean,
  @Value("\${task.flag-visits.number-of-days-ahead:30}") val numberOfDaysAhead: Int,
) {
  companion object {
    const val LOCK_AT_LEAST_FOR = "PT4H"
    const val LOCK_AT_MOST_FOR = "PT4H"
    const val THREAD_SLEEP_TIME_IN_MILLISECONDS: Long = 100
  }
}
