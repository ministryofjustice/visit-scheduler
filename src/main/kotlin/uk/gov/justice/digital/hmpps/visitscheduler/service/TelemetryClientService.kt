package uk.gov.justice.digital.hmpps.visitscheduler.service

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.audit.EventAuditDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.builder.ApplicationDtoBuilder
import uk.gov.justice.digital.hmpps.visitscheduler.dto.builder.VisitDtoBuilder
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Service
class TelemetryClientService(
  private val telemetryClient: TelemetryClient,
) {

  @Autowired
  private lateinit var visitDtoBuilder: VisitDtoBuilder

  @Autowired
  private lateinit var applicationDtoBuilder: ApplicationDtoBuilder

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun createVisitBookedTrackEventFromVisitEntity(
    visitEntity: Visit,
    actionedBy: String? = null,
    applicationMethodType: ApplicationMethodType? = null,
  ): MutableMap<String, String> {
    val visitDto = visitDtoBuilder.build(visitEntity)
    return createVisitTrackEventFromVisitDto(visitDto, actionedBy, applicationMethodType, isBooking = true)
  }

  fun createCancelVisitTrackEventFromVisitEntity(
    visitEntity: Visit,
    actionedBy: String? = null,
    applicationMethodType: ApplicationMethodType? = null,
  ): MutableMap<String, String> {
    val visitDto = visitDtoBuilder.build(visitEntity)
    return createVisitTrackEventFromVisitDto(visitDto, actionedBy, applicationMethodType)
  }

  fun createApplicationTrackEventFromVisitEntity(
    application: Application,
    actionedBy: String? = null,
    applicationMethodType: ApplicationMethodType? = null,
  ): MutableMap<String, String> {
    val applicationDto = applicationDtoBuilder.build(application)
    return createApplicationTrackEventFromVisitDto(applicationDto, actionedBy = actionedBy)
  }

  fun createApplicationVisitTrackEventFromVisitEntity(
    applicationDto: ApplicationDto,
    visit: Visit? = null,
    actionedBy: String? = null,
  ): MutableMap<String, String> {
    return createApplicationTrackEventFromVisitDto(applicationDto, visit, actionedBy)
  }

  fun createVisitTrackEventFromVisitDto(
    visit: VisitDto,
    actionedBy: String? = null,
    applicationMethodType: ApplicationMethodType? = null,
    isBooking: Boolean = false,
  ): MutableMap<String, String> {
    val data = mutableMapOf(
      "reference" to visit.reference,
      "prisonerId" to visit.prisonerId,
      "prisonId" to visit.prisonCode,
      "visitType" to visit.visitType.name,
      "visitRoom" to visit.visitRoom,
      "visitRestriction" to visit.visitRestriction.name,
      "visitStart" to formatDateTimeToString(visit.startTimestamp),
      "visitStatus" to visit.visitStatus.name,
      "applicationReference" to visit.applicationReference,
      "hasContactInformation" to ((visit.visitContact != null).toString()),
    )

    if (isBooking) {
      visit.visitorSupport?.let {
        data.put("supportRequired", it.description)
      }
    }

    actionedBy?.let {
      data.put("actionedBy", it)
    }

    applicationMethodType?.let {
      data.put("applicationMethodType", it.name)
    }

    return data
  }

  fun createApplicationTrackEventFromVisitDto(
    application: ApplicationDto,
    visitEntity: Visit? = null,
    actionedBy: String? = null,
  ): MutableMap<String, String> {
    val data = mutableMapOf(
      "reference" to (visitEntity?.let { visitEntity.reference } ?: ""),
      "applicationReference" to application.reference,
      "prisonerId" to application.prisonerId,
      "prisonId" to application.prisonCode,
      "visitType" to application.visitType.name,
      "visitRestriction" to application.visitRestriction.name,
      "visitStart" to formatDateTimeToString(application.startTimestamp),
      "reserved" to application.reserved.toString(),
    )
    actionedBy?.let {
      data.put("actionedBy", it)
    }

    return data
  }

  fun createFlagEventFromVisitDto(
    visit: VisitDto,
    bookingEventAudit: EventAuditDto?,
    notificationEventType: NotificationEventType,
  ): MutableMap<String, String> {
    val flagEventDataMap = mutableMapOf(
      "prisonId" to visit.prisonCode,
      "reference" to visit.reference,
      "reviewType" to notificationEventType.reviewType,
      "visitStatus" to visit.visitStatus.name,
      "applicationReference" to visit.applicationReference,
      "prisonerId" to visit.prisonerId,
      "visitRestriction" to visit.visitRestriction.name,
      "visitStart" to formatDateTimeToString(visit.startTimestamp),
      "visitType" to visit.visitType.name,
      "visitRoom" to visit.visitRoom,
    )

    bookingEventAudit?.let {
      flagEventDataMap["visitBooked"] = formatDateTimeToString(it.createTimestamp)
      flagEventDataMap["actionedBy"] = it.actionedBy
    }

    return flagEventDataMap
  }

  fun trackEvent(visitEvent: TelemetryVisitEvents, properties: Map<String, String>) {
    try {
      telemetryClient.trackEvent(visitEvent.eventName, properties, null)
    } catch (e: RuntimeException) {
      LOG.error("Error occurred in call to telemetry client to log event - $e.toString()")
    }
  }

  fun formatDateTimeToString(dateTime: LocalDateTime): String {
    return dateTime.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_DATE_TIME)
  }

  fun formatTimeToString(time: LocalTime): String {
    return time.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_TIME)
  }

  fun formatDateToString(date: LocalDate): String {
    return date.format(DateTimeFormatter.ISO_DATE)
  }
}
