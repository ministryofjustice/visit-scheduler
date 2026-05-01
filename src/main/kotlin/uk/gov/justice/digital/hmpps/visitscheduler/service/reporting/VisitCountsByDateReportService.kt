package uk.gov.justice.digital.hmpps.visitscheduler.service.reporting

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting.SessionVisitCountsByDateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting.VisitCountBySessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting.VisitCountReportStats
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionScheduleDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitCountStats
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.ExcludeDateService
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonsService
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionService
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionSlotService
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryClientService
import java.time.LocalDate

@Service
class VisitCountsByDateReportService(
  private val prisonsService: PrisonsService,
  private val sessionService: SessionService,
  private val telemetryClientService: TelemetryClientService,
  private val sessionSlotService: SessionSlotService,
  private val excludeDateService: ExcludeDateService,
  private val visitRepository: VisitRepository,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getVisitCountsReportForDate(reportDate: LocalDate): List<SessionVisitCountsByDateDto> {
    val today = LocalDate.now()
    return if (reportDate == today || reportDate.isAfter(today)) {
      LOG.info("Report date {} is not in the past.", reportDate)
      emptyList()
    } else {
      getVisitCountsBySession(reportDate)
    }
  }

  fun getVisitCountsBySession(reportDate: LocalDate): List<SessionVisitCountsByDateDto> = getSessionsReport(reportDate)

  private fun getSessionsReport(reportDate: LocalDate): List<SessionVisitCountsByDateDto> {
    val prisons = getAllActivePrisons()
    return getSessionVisitCounts(reportDate, getVisitCountsForPrisons(prisons, reportDate))
  }

  private fun getSessionVisitCounts(
    reportDate: LocalDate,
    sessionsReportDto: Map<PrisonDto, Map<SessionScheduleDto, VisitCountBySessionDto>>,
  ): List<SessionVisitCountsByDateDto> {
    val sessionVisitCounts = mutableListOf<SessionVisitCountsByDateDto>()

    sessionsReportDto.entries.forEach { prisonMap ->
      val prison = prisonMap.key
      val sessions = prisonMap.value
      val hasSessions = sessions.isNotEmpty()

      if (hasSessions) {
        sessions.forEach { session ->
          sessionVisitCounts.add(createSessionVisitCountsDto(reportDate, prison, session.value))
        }
      } else {
        sessionVisitCounts.add(createSessionVisitCountsDto(reportDate, prison))
      }
    }

    return sessionVisitCounts
  }

  private fun getVisitCountsForPrisons(prisons: List<PrisonDto>, reportDate: LocalDate): Map<PrisonDto, Map<SessionScheduleDto, VisitCountBySessionDto>> {
    val prisonDetails = mutableMapOf<PrisonDto, Map<SessionScheduleDto, VisitCountBySessionDto>>()
    prisons.forEach { prison ->
      prisonDetails[prison] = getVisitCountsForPrison(prison, reportDate)
    }

    return prisonDetails
  }

  private fun getVisitCountsForPrison(prison: PrisonDto, reportDate: LocalDate): Map<SessionScheduleDto, VisitCountBySessionDto> {
    var sessionDetails = mapOf<SessionScheduleDto, VisitCountBySessionDto>()
    val isExcludedDate = isExcludedDate(prison, reportDate)

    if (!isExcludedDate) {
      val sessions = getSessionsByDateForPrison(prison, reportDate)
      sessionDetails = getVisitCountsForSessions(sessions, reportDate)
    }

    return sessionDetails
  }

  private fun isExcludedDate(prison: PrisonDto, reportDate: LocalDate): Boolean = excludeDateService.getPrisonExcludeDates(prison.code).map { it.excludeDate }.contains(reportDate)

  private fun getVisitCountsForSessions(sessions: List<SessionScheduleDto>, reportDate: LocalDate): Map<SessionScheduleDto, VisitCountBySessionDto> {
    val sessionDetails = mutableMapOf<SessionScheduleDto, VisitCountBySessionDto>()
    sessions.forEach { session ->
      sessionDetails[session] = getVisitCountsForSession(session, reportDate)
    }

    return sessionDetails
  }

  private fun getAllActivePrisons(): List<PrisonDto> = prisonsService.getPrisons().filter { it.active }

  private fun getVisitCountsForSession(sessionSchedule: SessionScheduleDto, reportDate: LocalDate): VisitCountBySessionDto {
    val visitCountsByStatusAndRestriction = getVisitCountsByStatusAndRestriction(sessionSchedule.sessionTemplateReference, reportDate)
    return getSessionDetails(sessionSchedule, visitCountsByStatusAndRestriction)
  }

  private fun getSessionsByDateForPrison(prison: PrisonDto, reportDate: LocalDate): List<SessionScheduleDto> = sessionService.getSessionSchedule(prison.code, reportDate)

  private fun getVisitCountsByStatusAndRestriction(sessionTemplateReference: String, reportDate: LocalDate): Map<Pair<VisitStatus, VisitRestriction>, VisitCountReportStats> {
    val visitCounts = mutableMapOf<Pair<VisitStatus, VisitRestriction>, VisitCountReportStats>()
    val visitCountsBySession = getVisitCountsBySession(sessionTemplateReference, reportDate)
    val visitCountReportRestrictions = listOf(VisitRestriction.OPEN, VisitRestriction.CLOSED)
    VisitStatus.entries.forEach { visitStatus ->
      visitCountReportRestrictions.forEach { visitRestriction ->
        visitCounts[Pair(visitStatus, visitRestriction)] = getVisitCount(visitCountsBySession, visitStatus, visitRestriction)
      }
    }
    return visitCounts
  }

  private fun getVisitCount(visitCountStats: List<VisitCountStats>, visitStatus: VisitStatus, visitRestriction: VisitRestriction): VisitCountReportStats {
    val visitCountStat = visitCountStats.firstOrNull { it.visitRestriction == visitRestriction && it.visitStatus == visitStatus }
    return visitCountStat?.let {
      VisitCountReportStats(visitCount = it.visitCount, visitorCount = it.visitorCount)
    } ?: VisitCountReportStats(0, 0)
  }

  private fun getVisitCountsBySession(sessionTemplateReference: String, visitDate: LocalDate): List<VisitCountStats> {
    val sessionSlotId = sessionSlotService.getSessionSlot(sessionTemplateReference, visitDate)?.id
    return sessionSlotId?.let {
      val x = visitRepository.getVisitCountsBySession(it)
      println("x |+ $x")
      return x
    } ?: emptyList()
  }

  private fun createSessionVisitCountsDto(reportDate: LocalDate, prison: PrisonDto, visitCountBySession: VisitCountBySessionDto): SessionVisitCountsByDateDto = SessionVisitCountsByDateDto(
    reportDate = reportDate,
    prisonCode = prison.code,
    isBlockedDate = isExcludedDate(prison, reportDate),
    hasSessionsOnDate = true,
    visitCountBySession = visitCountBySession,
  )

  private fun createSessionVisitCountsDto(reportDate: LocalDate, prison: PrisonDto): SessionVisitCountsByDateDto = SessionVisitCountsByDateDto(
    reportDate = reportDate,
    prisonCode = prison.code,
    isBlockedDate = isExcludedDate(prison, reportDate),
    hasSessionsOnDate = false,
    visitCountBySession = null,
  )

  private fun getSessionDetails(session: SessionScheduleDto, visitCounts: Map<Pair<VisitStatus, VisitRestriction>, VisitCountReportStats>): VisitCountBySessionDto = VisitCountBySessionDto(
    sessionReference = session.sessionTemplateReference,
    sessionTimeSlot = session.sessionTimeSlot,
    sessionCapacity = session.capacity,
    visitType = session.visitType,
    openBookedCount = visitCounts[Pair(VisitStatus.BOOKED, VisitRestriction.OPEN)]?.visitCount ?: 0,
    openBookedVisitorsCount = visitCounts[Pair(VisitStatus.BOOKED, VisitRestriction.OPEN)]?.visitorCount ?: 0,
    closedBookedCount = visitCounts[Pair(VisitStatus.BOOKED, VisitRestriction.CLOSED)]?.visitCount ?: 0,
    closedBookedVisitorsCount = visitCounts[Pair(VisitStatus.BOOKED, VisitRestriction.CLOSED)]?.visitorCount ?: 0,
    openCancelledCount = visitCounts[Pair(VisitStatus.CANCELLED, VisitRestriction.OPEN)]?.visitCount ?: 0,
    closedCancelledCount = visitCounts[Pair(VisitStatus.CANCELLED, VisitRestriction.CLOSED)]?.visitCount ?: 0,
    visitRoom = session.visitRoom,
  )

  fun sendTelemetryEvent(sessionReports: List<SessionVisitCountsByDateDto>) {
    sessionReports.forEach {
      telemetryClientService.trackVisitCountsEvent(it)
    }
  }
}
