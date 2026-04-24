package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.DOUBLE_BOOKING_OR_RESERVATION
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.NON_ASSOCIATION
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.PRISON_DATE_BLOCKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.SESSION_DATE_BLOCKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.AdditionalSessionConflictInfoDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.DoubleBookedConflictSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.NonAssociationConflictSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionConflictAttribute
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionConflictDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionConflictType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import java.time.LocalDate

@Component
class SessionConflictsUtil {
  fun addSessionConflicts(
    session: VisitSessionDto,
    nonAssociationConflictSessions: List<NonAssociationConflictSessionDto>,
    doubleBookingConflictSessions: List<DoubleBookedConflictSessionDto>,
    prisonExcludeDates: List<LocalDate>,
    sessionExcludeDates: List<LocalDate>,
  ) {
    getNonAssociationSessionConflict(session, nonAssociationConflictSessions)?.let {
      session.sessionConflicts.add(it)
    }
    getDoubleBookingOrReservationSessionConflict(session, doubleBookingConflictSessions)?.let {
      session.sessionConflicts.add(it)
    }
    getPrisonDateExcludedSessionConflict(prisonExcludeDates, session)?.let {
      session.sessionConflicts.add(it)
    }
    getSessionDateExcludedSessionConflict(sessionExcludeDates, session)?.let {
      session.sessionConflicts.add(it)
    }
  }

  private fun getNonAssociationSessionConflict(
    session: VisitSessionDto,
    nonAssociationConflictSessions: List<NonAssociationConflictSessionDto>,
  ): SessionConflictDto? {
    val sessionDate = session.startTimestamp.toLocalDate()
    val nonAssociationConflictSessionsForDate = nonAssociationConflictSessions.filter { it.sessionDate == sessionDate }
    if (nonAssociationConflictSessionsForDate.isNotEmpty()) { // removed && policyFilterNonAssociation) {
      val nonAssociationConflictAttributes = getNonAssociationConflictAttributes(nonAssociationConflictSessionsForDate)
      return SessionConflictDto(NON_ASSOCIATION, nonAssociationConflictAttributes)
    }

    return null
  }

  private fun getDoubleBookingOrReservationSessionConflict(
    session: VisitSessionDto,
    doubleBookingConflicts: List<DoubleBookedConflictSessionDto>,
  ): SessionConflictDto? {
    doubleBookingConflicts.firstOrNull { doubleBookingConflict ->
      doubleBookingConflict.sessionTemplateReference == session.sessionTemplateReference && session.startTimestamp.toLocalDate() == doubleBookingConflict.sessionDate
    }?.let { doubleBookedSession ->
      val doubleBookingConflictAttributes = getDoubleBookingConflictAttributes(doubleBookedSession)
      return SessionConflictDto(DOUBLE_BOOKING_OR_RESERVATION, listOf(doubleBookingConflictAttributes))
    }

    return null
  }

  private fun getPrisonDateExcludedSessionConflict(
    prisonExcludeDates: List<LocalDate>,
    visitSession: VisitSessionDto,
  ): SessionConflictDto? {
    val sessionConflict = SessionConflictDto(PRISON_DATE_BLOCKED)
    return if (isDateExcluded(prisonExcludeDates, visitSession)) sessionConflict else null
  }

  private fun getSessionDateExcludedSessionConflict(
    sessionExcludeDates: List<LocalDate>,
    visitSession: VisitSessionDto,
  ): SessionConflictDto? {
    val sessionConflict = SessionConflictDto(SESSION_DATE_BLOCKED)
    return if (isSessionExcluded(sessionExcludeDates, visitSession)) sessionConflict else null
  }

  private fun isDateExcluded(
    prisonExcludeDates: List<LocalDate>,
    visitSession: VisitSessionDto,
  ): Boolean = prisonExcludeDates.any { it == visitSession.startTimestamp.toLocalDate() }

  private fun isSessionExcluded(
    sessionExcludeDates: List<LocalDate>,
    visitSession: VisitSessionDto,
  ): Boolean = sessionExcludeDates.any { it == visitSession.startTimestamp.toLocalDate() }

  private fun getNonAssociationConflictAttributes(nonAssociationConflictSessions: List<NonAssociationConflictSessionDto>): List<List<AdditionalSessionConflictInfoDto>> {
    val nonAssociationConflictAttributesList = mutableListOf<List<AdditionalSessionConflictInfoDto>>()
    nonAssociationConflictSessions.forEach {
      nonAssociationConflictAttributesList.add(getNonAssociationConflictAttributes(it))
    }
    return nonAssociationConflictAttributesList.toList()
  }

  private fun getNonAssociationConflictAttributes(nonAssociationConflictSession: NonAssociationConflictSessionDto): List<AdditionalSessionConflictInfoDto> {
    val nonAssociationConflictAttributes = mutableListOf<AdditionalSessionConflictInfoDto>()
    nonAssociationConflictAttributes.add(AdditionalSessionConflictInfoDto(SessionConflictAttribute.PRISONER_NUMBER, nonAssociationConflictSession.prisonerId))
    nonAssociationConflictAttributes.add(AdditionalSessionConflictInfoDto(SessionConflictAttribute.CONFLICT_TYPE, nonAssociationConflictSession.conflictType.name))
    if (nonAssociationConflictSession.conflictType == SessionConflictType.VISIT) {
      nonAssociationConflictAttributes.add(AdditionalSessionConflictInfoDto(SessionConflictAttribute.REFERENCE, nonAssociationConflictSession.reference!!))
    }

    return nonAssociationConflictAttributes.toList()
  }

  private fun getDoubleBookingConflictAttributes(doubleBookedConflictSession: DoubleBookedConflictSessionDto): List<AdditionalSessionConflictInfoDto> {
    val doubleBookingConflictAttributes = mutableListOf<AdditionalSessionConflictInfoDto>()
    doubleBookingConflictAttributes.add(AdditionalSessionConflictInfoDto(SessionConflictAttribute.CONFLICT_TYPE, doubleBookedConflictSession.conflictType.name))
    if (doubleBookedConflictSession.conflictType == SessionConflictType.VISIT) {
      doubleBookingConflictAttributes.add(AdditionalSessionConflictInfoDto(SessionConflictAttribute.REFERENCE, doubleBookedConflictSession.reference!!))
    }

    return doubleBookingConflictAttributes.toList()
  }
}
