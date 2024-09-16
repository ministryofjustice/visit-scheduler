package uk.gov.justice.digital.hmpps.visitscheduler.service.reporting

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting.SessionVisitCountsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionScheduleDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitRestrictionStats
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonsService
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionService
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionSlotService
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryClientService
import uk.gov.justice.digital.hmpps.visitscheduler.task.ReportingTask.Companion.LOG
import java.time.LocalDate

@Service
class VisitCountsByDateReportService(
  private val prisonsService: PrisonsService,
  private val sessionService: SessionService,
  private val telemetryClientService: TelemetryClientService,
  private val sessionSlotService: SessionSlotService,
  private val visitRepository: VisitRepository,
) {
  fun getVisitCountsReportForDate(reportDate: LocalDate): List<SessionVisitCountsDto> {
    val today = LocalDate.now()
    return if (reportDate == today || reportDate.isAfter(today)) {
      LOG.info("Report date {} is not in the past.", reportDate)
      emptyList()
    } else {
      getVisitCountsBySession(reportDate)
    }
  }

  private fun getVisitCountsBySession(reportDate: LocalDate): List<SessionVisitCountsDto> {
    return getSessionsReport(reportDate)
  }

  private fun getSessionsReport(reportDate: LocalDate): List<SessionVisitCountsDto> {
    val prisons = getAllActivePrisons()
    return getSessionVisitCounts(reportDate, getVisitCountsForPrisons(prisons, reportDate))
  }

  private fun getSessionVisitCounts(
    reportDate: LocalDate,
    sessionsReportDto: Map<PrisonDto, Map<SessionScheduleDto, Map<Pair<VisitStatus, VisitRestriction>, Int>>>,
  ): List<SessionVisitCountsDto> {
    val sessionVisitCounts = mutableListOf<SessionVisitCountsDto>()

    sessionsReportDto.entries.forEach { prisonMap ->
      val prison = prisonMap.key
      val sessions = prisonMap.value
      var sessionVisitCount = createSessionVisitCountsDto(reportDate = reportDate, prison, sessions.keys)

      if (sessions.isNotEmpty()) {
        sessions.forEach { sessionEntry ->
          sessionVisitCount = createSessionVisitCountsDto(reportDate = reportDate, prison, sessions.keys)
          val session = sessionEntry.key
          val counts = sessionEntry.value
          setSessionDetails(sessionVisitCount, session, counts)
          sessionVisitCounts.add(sessionVisitCount)
        }
      } else {
        sessionVisitCounts.add(sessionVisitCount)
      }
    }

    return sessionVisitCounts
  }

  private fun getVisitCountsForPrisons(prisons: List<PrisonDto>, reportDate: LocalDate): Map<PrisonDto, Map<SessionScheduleDto, Map<Pair<VisitStatus, VisitRestriction>, Int>>> {
    val prisonDetails = mutableMapOf<PrisonDto, Map<SessionScheduleDto, Map<Pair<VisitStatus, VisitRestriction>, Int>>>()
    prisons.forEach { prison ->
      prisonDetails[prison] = getVisitCountsForPrison(prison, reportDate)
    }

    return prisonDetails
  }

  private fun getVisitCountsForPrison(prison: PrisonDto, reportDate: LocalDate): Map<SessionScheduleDto, Map<Pair<VisitStatus, VisitRestriction>, Int>> {
    var sessionDetails = mapOf<SessionScheduleDto, Map<Pair<VisitStatus, VisitRestriction>, Int>>()
    val isExcludedDate = isExcludedDate(prison, reportDate)

    if (!isExcludedDate) {
      val sessions = getSessionsByDateForPrison(prison, reportDate)
      sessionDetails = getVisitCountsForSessions(sessions, reportDate)
    }

    return sessionDetails
  }

  private fun isExcludedDate(prison: PrisonDto, reportDate: LocalDate): Boolean {
    return prison.excludeDates.contains(reportDate)
  }

  private fun getVisitCountsForSessions(sessions: List<SessionScheduleDto>, reportDate: LocalDate): Map<SessionScheduleDto, Map<Pair<VisitStatus, VisitRestriction>, Int>> {
    val sessionDetails = mutableMapOf<SessionScheduleDto, Map<Pair<VisitStatus, VisitRestriction>, Int>>()
    sessions.forEach { session ->
      sessionDetails[session] = getVisitCountsForSession(session, reportDate)
    }

    return sessionDetails
  }

  private fun getAllActivePrisons(): List<PrisonDto> {
    return prisonsService.getPrisons().filter { it.active }
  }

  private fun getVisitCountsForSession(sessionSchedule: SessionScheduleDto, reportDate: LocalDate): Map<Pair<VisitStatus, VisitRestriction>, Int> {
    return getVisitCountsByStatusAndRestriction(sessionSchedule.sessionTemplateReference, reportDate)
  }

  private fun getSessionsByDateForPrison(prison: PrisonDto, reportDate: LocalDate): List<SessionScheduleDto> {
    return sessionService.getSessionSchedule(prison.code, reportDate)
  }

  private fun getVisitCountsByStatusAndRestriction(sessionTemplateReference: String, reportDate: LocalDate): Map<Pair<VisitStatus, VisitRestriction>, Int> {
    val visitCounts = mutableMapOf<Pair<VisitStatus, VisitRestriction>, Int>()
    val bookedCounts = getVisitCountsBySession(sessionTemplateReference, VisitStatus.BOOKED, reportDate)
    val cancelledCounts = getVisitCountsBySession(sessionTemplateReference, VisitStatus.CANCELLED, reportDate)

    visitCounts[Pair(VisitStatus.BOOKED, VisitRestriction.OPEN)] = bookedCounts.firstOrNull { it.visitRestriction == VisitRestriction.OPEN }?.count ?: 0
    visitCounts[Pair(VisitStatus.BOOKED, VisitRestriction.CLOSED)] = bookedCounts.firstOrNull { it.visitRestriction == VisitRestriction.CLOSED }?.count ?: 0
    visitCounts[Pair(VisitStatus.CANCELLED, VisitRestriction.OPEN)] = cancelledCounts.firstOrNull { it.visitRestriction == VisitRestriction.OPEN }?.count ?: 0
    visitCounts[Pair(VisitStatus.CANCELLED, VisitRestriction.CLOSED)] = cancelledCounts.firstOrNull { it.visitRestriction == VisitRestriction.CLOSED }?.count ?: 0

    return visitCounts
  }

  private fun getVisitCountsBySession(sessionTemplateReference: String, visitStatus: VisitStatus, visitDate: LocalDate): List<VisitRestrictionStats> {
    val sessionSlotId = sessionSlotService.getSessionSlot(sessionTemplateReference, visitDate)?.id
    sessionSlotId?.let {
      return when (visitStatus) {
        VisitStatus.BOOKED -> {
          visitRepository.getCountOfBookedSessionVisitsForOpenOrClosedRestriction(it)
        }

        VisitStatus.CANCELLED -> {
          visitRepository.getCountOfCancelledSessionVisitsForOpenOrClosedRestriction(
            it,
          )
        }
      }
    }

    return listOf()
  }

  private fun createSessionVisitCountsDto(reportDate: LocalDate, prison: PrisonDto, sessions: Set<SessionScheduleDto>): SessionVisitCountsDto {
    return SessionVisitCountsDto(reportDate = reportDate, prisonCode = prison.code, isBlockedDate = isExcludedDate(prison, reportDate), hasSessionsOnDate = sessions.isNotEmpty())
  }

  private fun setSessionDetails(sessionVisitCount: SessionVisitCountsDto, session: SessionScheduleDto, visitCounts: Map<Pair<VisitStatus, VisitRestriction>, Int>) {
    sessionVisitCount.sessionReference = session.sessionTemplateReference
    sessionVisitCount.sessionTimeSlot = session.sessionTimeSlot
    sessionVisitCount.sessionCapacity = session.capacity
    sessionVisitCount.visitType = session.visitType
    sessionVisitCount.openBookedCount = visitCounts[Pair(VisitStatus.BOOKED, VisitRestriction.OPEN)] ?: 0
    sessionVisitCount.closedBookedCount = visitCounts[Pair(VisitStatus.BOOKED, VisitRestriction.CLOSED)] ?: 0
    sessionVisitCount.openCancelledCount = visitCounts[Pair(VisitStatus.CANCELLED, VisitRestriction.OPEN)] ?: 0
    sessionVisitCount.closedCancelledCount = visitCounts[Pair(VisitStatus.CANCELLED, VisitRestriction.CLOSED)] ?: 0
  }

  fun sendTelemetryEvent(sessionReports: List<SessionVisitCountsDto>) {
    sessionReports.forEach {
      telemetryClientService.trackVisitCountsEvent(it)
    }
  }
}
