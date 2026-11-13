package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.DOUBLE_BOOKING_OR_RESERVATION
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.NON_ASSOCIATION
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.PRISON_DATE_BLOCKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.SESSION_DATE_BLOCKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto

@Component
class SessionCountsUtil {
  fun getSessionCounts(visitSessions: List<VisitSessionDto>): Map<SessionCountType, Int> {
    // exclude any sessions that are blocked from available counts
    val blockedSessions = visitSessions.filter { it.sessionConflicts.map { conflictDto -> conflictDto.sessionConflict }.any { conflict -> (conflict == SESSION_DATE_BLOCKED || conflict == PRISON_DATE_BLOCKED) } }
    val allUnblockedSessions = visitSessions.filterNot { blockedSessions.contains(it) }

    // get sessions which have already been booked / in the process of booking
    val alreadyBookedSessions = allUnblockedSessions.filter { session -> session.sessionConflicts.map { it.sessionConflict }.contains(DOUBLE_BOOKING_OR_RESERVATION) }

    // get sessions that are not already booked
    val availableSessions = allUnblockedSessions.filterNot { alreadyBookedSessions.contains(it) }

    // get sessions that are unavailable to book due to non-associations
    val nonAssociationSessions = availableSessions.filter { it.sessionConflicts.map { sessionConflictDto -> sessionConflictDto.sessionConflict }.contains(NON_ASSOCIATION) }

    // get sessions that are unavailable to book due to the session being full
    val fullyBookedSessionCounts = availableSessions.filterNot { nonAssociationSessions.contains(it) }.count { !hasSessionGotCapacity(it) }

    return mapOf(
      SessionCountType.TOTAL_SESSIONS to allUnblockedSessions.size,
      SessionCountType.UNAVAILABLE_ALREADY_BOOKED to alreadyBookedSessions.size,
      SessionCountType.UNAVAILABLE_FULLY_BOOKED to fullyBookedSessionCounts,
      SessionCountType.UNAVAILABLE_NON_ASSOCIATION to nonAssociationSessions.size,
    )
  }

  fun hasSessionGotCapacity(session: VisitSessionDto, sessionRestriction: SessionRestriction): Boolean = when (sessionRestriction) {
    SessionRestriction.CLOSED -> (session.closedVisitCapacity > 0 && (session.closedVisitCapacity > (session.closedVisitBookedCount ?: 0)))
    SessionRestriction.OPEN -> (session.openVisitCapacity > 0 && (session.openVisitCapacity > (session.openVisitBookedCount ?: 0)))
  }

  private fun hasSessionGotCapacity(session: VisitSessionDto): Boolean = (hasSessionGotCapacity(session, SessionRestriction.OPEN) || hasSessionGotCapacity(session, SessionRestriction.CLOSED))
}

enum class SessionCountType {
  TOTAL_SESSIONS,
  UNAVAILABLE_NON_ASSOCIATION,
  UNAVAILABLE_ALREADY_BOOKED,
  UNAVAILABLE_FULLY_BOOKED,
}
