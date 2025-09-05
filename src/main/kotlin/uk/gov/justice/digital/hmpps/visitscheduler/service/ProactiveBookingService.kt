package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerReceivedReasonType.TRANSFERRED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerReceivedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerReleasedNotificationDto

@Service
class ProactiveBookingService(
  private val visitNotificationEventService: VisitNotificationEventService,
  private val visitRequestsService: VisitRequestsService,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun processPrisonerReleasedEvent(notificationDto: PrisonerReleasedNotificationDto) {
    LOG.info("Entered ProactiveBookingService processPrisonerReleasedEvent with dto - $notificationDto")

    visitNotificationEventService.handlePrisonerReleasedNotification(notificationDto)
    visitRequestsService.handlePrisonerReleasedEventAutoRejectRequestVisits(notificationDto)
  }

  fun processPrisonerReceivedEvent(notificationDto: PrisonerReceivedNotificationDto) {
    LOG.info("Entered ProactiveBookingService processPrisonerReceivedEvent with dto - $notificationDto")

    if (TRANSFERRED == notificationDto.reason) {
      visitNotificationEventService.handlePrisonerReceivedNotification(notificationDto)
      visitRequestsService.handlePrisonerReceivedEventAutoRejectRequestVisits(notificationDto)
    } else {
      LOG.info("Skipping processPrisonerReceivedEvent for prisonerId ${notificationDto.prisonerNumber} as reason not TRANSFERRED, reason ${notificationDto.reason}")
    }
  }
}
