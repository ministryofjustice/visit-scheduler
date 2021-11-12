package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.data.VisitSession
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.SessionFrequency
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.resource.VisitDto
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.transaction.Transactional

@Service
@Transactional
class VisitSchedulerService(
  private val visitRepository: VisitRepository,
  private val sessionTemplateRepository: SessionTemplateRepository,
  private val clock: Clock,
  @Value("\${policy.book-ahead-period}") private val bookAheadPeriod: Long,
) {

  fun findVisits(prisonerId: String): List<VisitDto> {
    return visitRepository.findByPrisonerId(prisonerId).map { VisitDto(it) }
  }

  fun getVisitSessions(prisonId: String): List<VisitSession> {
    val availableSessionEndDate = LocalDate.now(clock).plusDays(bookAheadPeriod)
    val sessions =
      sessionTemplateRepository.findValidSessionsByPrisonId(prisonId, LocalDate.now(clock), availableSessionEndDate)
    return sessions.map {
      buildSessions(it, availableSessionEndDate)
    }.flatten().sortedWith(compareBy { it.startTimestamp })
  }

  // from the start date calculate the slots for based on chosen frequency  (expiry is inclusive)
  private fun buildSessions(
    it: SessionTemplate,
    availableSessionEndDate: LocalDate
  ): List<VisitSession> {
    val lastDayOfSession =
      if (it.expiryDate == null || availableSessionEndDate.isBefore(it.expiryDate)) availableSessionEndDate else it.expiryDate
    return it.startDate.datesUntil(lastDayOfSession.plusDays(1), SessionFrequency.valueOf(it.frequency).frequencyPeriod)
      .map { date ->
        VisitSession(
          sessionTemplateId = it.id,
          prisonId = it.prisonId,
          startTimestamp = LocalDateTime.of(date, it.startTime),
          closedVisitBookedCount = 0,
          openVisitBookedCount = 0,
          openVisitCapacity = it.openCapacity,
          closedVisitCapacity = it.closedCapacity,
          endTimestamp = LocalDateTime.of(date, it.endTime),
          visitRoomName = it.visitRoom,
          visitType = it.visitType,
          restrictions = it.restrictions
        )
      }.filter { session -> session.startTimestamp > LocalDateTime.now(clock) } // TODO - can visits ever be booked for the same day?
      .toList()
  }
}
