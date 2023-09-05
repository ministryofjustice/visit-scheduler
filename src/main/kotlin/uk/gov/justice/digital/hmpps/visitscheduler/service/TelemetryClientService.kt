package uk.gov.justice.digital.hmpps.visitscheduler.service

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Service
class TelemetryClientService(
  private val telemetryClient: TelemetryClient,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun createVisitTrackEventFromVisitEntity(
    visit: Visit,
    actionedBy: String? = null,
    applicationMethodType: ApplicationMethodType? = null,
  ): MutableMap<String, String> {
    val visitDto = VisitDto(visit)
    return createVisitTrackEventFromVisitDto(visitDto, actionedBy, applicationMethodType)
  }

  fun createVisitTrackEventFromVisitDto(
    visit: VisitDto,
    actionedBy: String? = null,
    applicationMethodType: ApplicationMethodType? = null,
  ): MutableMap<String, String> {
    val data = mutableMapOf(
      "reference" to visit.reference,
      "prisonerId" to visit.prisonerId,
      "prisonId" to visit.prisonCode,
      "visitType" to visit.visitType.name,
      "visitRoom" to visit.visitRoom,
      "visitRestriction" to visit.visitRestriction.name,
      "visitStart" to visit.startTimestamp.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_DATE_TIME),
      "visitStatus" to visit.visitStatus.name,
      "applicationReference" to visit.applicationReference,
    )

    actionedBy?.let {
      data.put("actionedBy", it)
    }

    applicationMethodType?.let {
      data.put("applicationMethodType", it.name)
    }

    return data
  }

  fun createFlagEventFromVisitDto(
    visit: Visit,
    notificationEventType: NotificationEventType,
  ): MutableMap<String, String> {
    return mutableMapOf(
      "reference" to visit.reference,
      "prisonerId" to visit.prisonerId,
      "prisonId" to visit.prison.code,
      "visitType" to visit.visitType.name,
      "visitRoom" to visit.visitRoom,
      "visitRestriction" to visit.visitRestriction.name,
      "visitStart" to visit.visitStart.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_DATE_TIME),
      "visitStatus" to visit.visitStatus.name,
      "applicationReference" to visit.applicationReference,
      "reviewType" to notificationEventType.reviewType,
    )
  }

  fun trackEvent(visitEvent: TelemetryVisitEvents, properties: Map<String, String>) {
    try {
      telemetryClient.trackEvent(visitEvent.eventName, properties, null)
    } catch (e: RuntimeException) {
      LOG.error("Error occurred in call to telemetry client to log event - $e.toString()")
    }
  }
}
