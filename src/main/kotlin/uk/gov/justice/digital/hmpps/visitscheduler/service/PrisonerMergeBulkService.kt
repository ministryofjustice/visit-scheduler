package uk.gov.justice.digital.hmpps.visitscheduler.service

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.config.VisitSchedulerExceptionHandler
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerMergeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerMergeNotificationsDto

@Service
class PrisonerMergeBulkService(
  private val prisonerMergeService: PrisonerMergeService,
  private val telemetryClient: TelemetryClient,
) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun handlePrisonerMerges(notificationDto: PrisonerMergeNotificationsDto) {
    LOG.info("Prisoner merge notifications received : {}", notificationDto)

    notificationDto.prisonerMergeNotifications.forEach { prisonerMergeNotification ->
      try {
        prisonerMergeService.handlePrisonerMerge(prisonerMergeNotification)
      } catch (e: Exception) {
        LOG.error("Failed to process prisoner merge notification : {}", prisonerMergeNotification, e)
        trackPrisonerMergeFailure(prisonerMergeNotification, e)
      }
    }
  }

  private fun trackPrisonerMergeFailure(notificationDto: PrisonerMergeNotificationDto, exception: Exception) {
    try {
      telemetryClient.trackEvent(
        TelemetryVisitEvents.MANUAL_MERGE_EVENT_FAILED_FOR_PRISONER.eventName,
        mapOf(
          "oldPrisonerNumber" to notificationDto.oldPrisonerNumber,
          "newPrisonerNumber" to notificationDto.newPrisonerNumber,
          "exceptionType" to exception::class.simpleName.orEmpty(),
          "exceptionMessage" to (exception.message?.take(VisitSchedulerExceptionHandler.MAX_ERROR_LENGTH) ?: ""),
        ),
        null,
      )
    } catch (e: RuntimeException) {
      LOG.error("Error occurred in call to telemetry client to log event", e)
    }
  }
}
