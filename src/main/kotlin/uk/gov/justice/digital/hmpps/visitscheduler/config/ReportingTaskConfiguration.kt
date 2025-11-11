package uk.gov.justice.digital.hmpps.visitscheduler.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class ReportingTaskConfiguration(
  @param:Value("\${task.reporting.visit-counts-report.enabled:false}") val visitCountsReportingEnabled: Boolean,
  @param:Value("\${task.reporting.overbooked-sessions-report.enabled:false}") val overbookedSessionsReportingEnabled: Boolean,
) {
  companion object {
    const val LOCK_AT_LEAST_FOR = "PT60M"
    const val LOCK_AT_MOST_FOR = "PT60M"
  }
}
