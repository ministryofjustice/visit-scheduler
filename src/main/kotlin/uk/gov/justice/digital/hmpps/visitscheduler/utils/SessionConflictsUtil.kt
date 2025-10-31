package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.DOUBLE_BOOKING_OR_RESERVATION
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.NON_ASSOCIATION
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.PRISON_DATE_BLOCKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.SESSION_DATE_BLOCKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.AdditionalSessionConflictInfoDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionConflictDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplateExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.service.NonAssociationConflictSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.NonAssociationConflictType
import java.time.LocalDate

@Component
class SessionConflictsUtil {
  fun addSessionConflicts(
    session: VisitSessionDto,
    nonAssociationConflictSessions: List<NonAssociationConflictSessionDto>,
    doubleBookingOrReservationSessions: List<VisitSessionDto>,
    prisonExcludeDates: List<LocalDate>,
    sessionExcludeDates: List<SessionTemplateExcludeDate>,
  ) {
    getNonAssociationSessionConflict(session, nonAssociationConflictSessions)?.let {
      session.sessionConflicts.add(it)
    }
    getDoubleBookingOrReservationSessionConflict(doubleBookingOrReservationSessions, session)?.let {
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
    if (nonAssociationConflictSessionsForDate.isNotEmpty()) { // removed &&  policyFilterNonAssociation) {
      val nonAssociationConflictAttributes = getNonAssociationConflictAttributes(nonAssociationConflictSessionsForDate)
      return SessionConflictDto(NON_ASSOCIATION, nonAssociationConflictAttributes)
    }

    return null
  }

  private fun getDoubleBookingOrReservationSessionConflict(
    doubleBookingOrReservationSessions: List<VisitSessionDto>,
    visitSession: VisitSessionDto,
  ): SessionConflictDto? {
    val sessionConflict = SessionConflictDto(DOUBLE_BOOKING_OR_RESERVATION)
    return if (hasDoubleBookingOrReservationSessions(doubleBookingOrReservationSessions, visitSession)) sessionConflict else null
  }

  private fun getPrisonDateExcludedSessionConflict(
    prisonExcludeDates: List<LocalDate>,
    visitSession: VisitSessionDto,
  ): SessionConflictDto? {
    val sessionConflict = SessionConflictDto(PRISON_DATE_BLOCKED)
    return if (isDateExcluded(prisonExcludeDates, visitSession)) sessionConflict else null
  }

  private fun getSessionDateExcludedSessionConflict(
    sessionExcludeDates: List<SessionTemplateExcludeDate>,
    visitSession: VisitSessionDto,
  ): SessionConflictDto? {
    val sessionConflict = SessionConflictDto(SESSION_DATE_BLOCKED)
    return if (isSessionExcluded(sessionExcludeDates, visitSession)) sessionConflict else null
  }

  private fun hasDoubleBookingOrReservationSessions(
    doubleBookingOrReservationSessions: List<VisitSessionDto>,
    it: VisitSessionDto,
  ): Boolean = doubleBookingOrReservationSessions.contains(it)

  private fun isDateExcluded(
    prisonExcludeDates: List<LocalDate>,
    visitSession: VisitSessionDto,
  ): Boolean = prisonExcludeDates.any { it == visitSession.startTimestamp.toLocalDate() }

  private fun isSessionExcluded(
    sessionExcludeDates: List<SessionTemplateExcludeDate>,
    visitSession: VisitSessionDto,
  ): Boolean = sessionExcludeDates.any { it.sessionTemplate.reference == visitSession.sessionTemplateReference && it.excludeDate == visitSession.startTimestamp.toLocalDate() }
  private fun getNonAssociationConflictAttributes(nonAssociationConflictSessions: List<NonAssociationConflictSessionDto>): List<List<AdditionalSessionConflictInfoDto>> {
    val nonAssociationConflictAttributesList = mutableListOf<List<AdditionalSessionConflictInfoDto>>()
    nonAssociationConflictSessions.forEach {
      nonAssociationConflictAttributesList.add(getNonAssociationConflictAttributes(it))
    }
    return nonAssociationConflictAttributesList.toList()
  }

  private fun getNonAssociationConflictAttributes(nonAssociationConflictSession: NonAssociationConflictSessionDto): List<AdditionalSessionConflictInfoDto> {
    val nonAssociationConflictAttributes = mutableListOf<AdditionalSessionConflictInfoDto>()
    nonAssociationConflictAttributes.add(AdditionalSessionConflictInfoDto("prisonerId", nonAssociationConflictSession.prisonerId))
    nonAssociationConflictAttributes.add(AdditionalSessionConflictInfoDto("type", nonAssociationConflictSession.conflictType.name))
    if (nonAssociationConflictSession.conflictType == NonAssociationConflictType.VISIT) {
      nonAssociationConflictAttributes.add(AdditionalSessionConflictInfoDto("reference", nonAssociationConflictSession.reference!!))
    }

    return nonAssociationConflictAttributes.toList()
  }
}
