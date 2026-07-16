package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.DOUBLE_BOOKING_OR_RESERVATION
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.NON_ASSOCIATION
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.PRISON_DATE_BLOCKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.SESSION_DATE_BLOCKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionTemplateVisitOrderRestrictionType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.AdditionalSessionConflictInfoDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.DoubleBookedConflictSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.NonAssociationConflictSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionConflictAttribute
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionConflictType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visit.allocation.VisitOrderPrisonerBalanceDto
import java.time.DayOfWeek.MONDAY
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class SessionConflictsUtilTest {
  val sessionConflictsUtil = SessionConflictsUtil()

  private val visitDate: LocalDate = LocalDate.of(2026, 1, 1).with(TemporalAdjusters.next(MONDAY))

  fun createVisitSessionDto(visitDate: LocalDate, visitOrderRestriction: SessionTemplateVisitOrderRestrictionType = SessionTemplateVisitOrderRestrictionType.NONE): VisitSessionDto = VisitSessionDto(
    sessionTemplateReference = "ref",
    visitRoom = "visit-room",
    visitType = VisitType.SOCIAL,
    prisonCode = "HEI",
    openVisitCapacity = 10,
    openVisitBookedCount = 1,
    closedVisitCapacity = 10,
    closedVisitBookedCount = 1,
    startTimestamp = visitDate.atTime(10, 0),
    endTimestamp = visitDate.atTime(11, 0),
    sessionConflicts = mutableListOf(),
    visitOrderRestriction = visitOrderRestriction,
  )

  fun createDoubleBookedConflictSessionDto(
    reference: String,
    sessionConflictType: SessionConflictType = SessionConflictType.VISIT,
    visitSubStatus: VisitSubStatus = VisitSubStatus.APPROVED,
    sessionTemplateReference: String,
    visitDate: LocalDate,
  ): DoubleBookedConflictSessionDto = DoubleBookedConflictSessionDto(reference = reference, conflictType = sessionConflictType, visitSubStatus = visitSubStatus, sessionTemplateReference = sessionTemplateReference, sessionDate = visitDate)

  @Test
  fun `when non associations exists then session is marked with non-association session conflict`() {
    val session = createVisitSessionDto(visitDate)
    val nonAssociationSessionsList = listOf(
      NonAssociationConflictSessionDto("non-association-1", SessionConflictType.VISIT, "ref2", visitDate),
    )
    sessionConflictsUtil.addSessionConflicts(session, nonAssociationSessionsList, emptyList(), emptyList(), emptyList(), emptyList(), null)
    assertThat(session.sessionConflicts.size).isEqualTo(1)
    assertThat(session.sessionConflicts[0].sessionConflict).isEqualTo(NON_ASSOCIATION)
    assertThat(session.sessionConflicts.flatMap { it.additionalAttributes }).containsAll(
      listOf(
        listOf(
          AdditionalSessionConflictInfoDto(SessionConflictAttribute.PRISONER_NUMBER, "non-association-1"),
          AdditionalSessionConflictInfoDto(SessionConflictAttribute.CONFLICT_TYPE, "VISIT"),
          AdditionalSessionConflictInfoDto(SessionConflictAttribute.REFERENCE, "ref2"),
        ),
      ),
    )
  }

  @Test
  fun `when non associations exists but not for same date then session is not marked with non-association session conflict`() {
    val session = createVisitSessionDto(visitDate)
    val nonAssociationSessionsList = listOf(
      NonAssociationConflictSessionDto("non-association-1", SessionConflictType.VISIT, "ref2", visitDate.plusDays(1)),
    )
    sessionConflictsUtil.addSessionConflicts(session, nonAssociationSessionsList, emptyList(), emptyList(), emptyList(), emptyList(), null)
    assertThat(session.sessionConflicts.size).isEqualTo(0)
  }

  @Test
  fun `when non associations do not exist then session is not marked with non-association session conflict`() {
    val session = createVisitSessionDto(visitDate)
    val nonAssociationSessionsList = emptyList<NonAssociationConflictSessionDto>()
    sessionConflictsUtil.addSessionConflicts(session, nonAssociationSessionsList, emptyList(), emptyList(), emptyList(), emptyList(), null)
    assertThat(session.sessionConflicts.size).isEqualTo(0)
  }

  @Test
  fun `when booking for session does not exist then session is not marked with double booking session conflict`() {
    val session = createVisitSessionDto(visitDate)
    val doubleBookingSessionList = emptyList<DoubleBookedConflictSessionDto>()
    sessionConflictsUtil.addSessionConflicts(session, emptyList(), doubleBookingSessionList, emptyList(), emptyList(), emptyList(), null)
    assertThat(session.sessionConflicts.size).isEqualTo(0)
  }

  @Test
  fun `when booking exists for session but not for same date then session is not marked with double booking session conflict`() {
    val session = createVisitSessionDto(visitDate)
    sessionConflictsUtil.addSessionConflicts(session, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null)
    assertThat(session.sessionConflicts.size).isEqualTo(0)
  }

  @Test
  fun `when booking for session exists then session is marked with double booking session conflict`() {
    val session = createVisitSessionDto(visitDate)
    val doubleBookingSessionList = listOf(
      createDoubleBookedConflictSessionDto(
        reference = "visit-1",
        sessionTemplateReference = session.sessionTemplateReference,
        visitDate = session.startTimestamp.toLocalDate(),
      ),
    )
    sessionConflictsUtil.addSessionConflicts(session, emptyList(), doubleBookingSessionList, emptyList(), emptyList(), emptyList(), null)
    assertThat(session.sessionConflicts.size).isEqualTo(1)
    assertThat(session.sessionConflicts[0].sessionConflict).isEqualTo(DOUBLE_BOOKING_OR_RESERVATION)
    assertThat(session.sessionConflicts.flatMap { it.additionalAttributes }).containsAll(
      listOf(
        listOf(
          AdditionalSessionConflictInfoDto(SessionConflictAttribute.CONFLICT_TYPE, "VISIT"),
          AdditionalSessionConflictInfoDto(SessionConflictAttribute.REFERENCE, "visit-1"),
        ),
      ),
    )
  }

  @Test
  fun `when reservation exists for limit reached session then session is not marked with remand limit reached conflict`() {
    val session = createVisitSessionDto(visitDate)
    val doubleBookingSessionList = listOf(
      createDoubleBookedConflictSessionDto(
        reference = "",
        sessionConflictType = SessionConflictType.APPLICATION,
        sessionTemplateReference = session.sessionTemplateReference,
        visitDate = session.startTimestamp.toLocalDate(),
      ),
    )

    sessionConflictsUtil.addSessionConflicts(session, emptyList(), doubleBookingSessionList, listOf(session), emptyList(), emptyList(), null)

    assertThat(session.sessionConflicts.size).isEqualTo(1)
    assertThat(session.sessionConflicts.map { it.sessionConflict }).containsOnly(DOUBLE_BOOKING_OR_RESERVATION)
    assertThat(session.sessionConflicts.flatMap { it.additionalAttributes }).containsAll(
      listOf(
        listOf(
          AdditionalSessionConflictInfoDto(SessionConflictAttribute.CONFLICT_TYPE, "APPLICATION"),
        ),
      ),
    )
  }

  @Test
  fun `when date is not blocked by prison then session is not marked with prison blocked session conflict`() {
    val session = createVisitSessionDto(visitDate)
    val prisonBlockedList = emptyList<LocalDate>()
    sessionConflictsUtil.addSessionConflicts(session, emptyList(), emptyList(), emptyList(), prisonBlockedList, emptyList(), null)
    assertThat(session.sessionConflicts.size).isEqualTo(0)
  }

  @Test
  fun `when same date is not blocked by prison then session is not marked with prison blocked session conflict`() {
    val session = createVisitSessionDto(visitDate)
    val prisonBlockedList = listOf<LocalDate>(visitDate.plusDays(1))
    sessionConflictsUtil.addSessionConflicts(session, emptyList(), emptyList(), emptyList(), prisonBlockedList, emptyList(), null)
    assertThat(session.sessionConflicts.size).isEqualTo(0)
  }

  @Test
  fun `when date is blocked for by prison then session is marked with prison blocked session conflict`() {
    val session = createVisitSessionDto(visitDate)
    val prisonBlockedList = listOf(visitDate)
    sessionConflictsUtil.addSessionConflicts(session, emptyList(), emptyList(), emptyList(), prisonBlockedList, emptyList(), null)
    assertThat(session.sessionConflicts.size).isEqualTo(1)
    assertThat(session.sessionConflicts[0].sessionConflict).isEqualTo(PRISON_DATE_BLOCKED)
  }

  @Test
  fun `when session is not blocked then session is not marked with session blocked session conflict`() {
    val session = createVisitSessionDto(visitDate)
    val sessionBlockedList = emptyList<LocalDate>()
    sessionConflictsUtil.addSessionConflicts(session, emptyList(), emptyList(), emptyList(), emptyList(), sessionBlockedList, null)
    assertThat(session.sessionConflicts.size).isEqualTo(0)
  }

  @Test
  fun `when session is not blocked for same date then session is not marked with session blocked session conflict`() {
    val session = createVisitSessionDto(visitDate)
    val sessionBlockedList = listOf<LocalDate>(visitDate.plusDays(1))
    sessionConflictsUtil.addSessionConflicts(session, emptyList(), emptyList(), emptyList(), emptyList(), sessionBlockedList, null)
    assertThat(session.sessionConflicts.size).isEqualTo(0)
  }

  @Test
  fun `when session is blocked for same date then session is marked with session blocked session conflict`() {
    val session = createVisitSessionDto(visitDate)
    val sessionBlockedList = listOf(visitDate)
    sessionConflictsUtil.addSessionConflicts(session, emptyList(), emptyList(), emptyList(), emptyList(), sessionBlockedList, null)
    assertThat(session.sessionConflicts.size).isEqualTo(1)
    assertThat(session.sessionConflicts[0].sessionConflict).isEqualTo(SESSION_DATE_BLOCKED)
  }

  @Test
  fun `when multiple session conflicts then session is marked with all flagged session conflicts`() {
    val session = createVisitSessionDto(visitDate)
    val nonAssociationSessionsList = listOf(
      NonAssociationConflictSessionDto("non-association-1", SessionConflictType.VISIT, "ref2", visitDate),
    )
    val doubleBookingSessionList = listOf(
      createDoubleBookedConflictSessionDto(
        reference = "visit-1",
        sessionTemplateReference = session.sessionTemplateReference,
        visitDate = session.startTimestamp.toLocalDate(),
      ),
    )

    val prisonBlockedList = listOf(visitDate)
    val sessionBlockedList = listOf(visitDate)

    sessionConflictsUtil.addSessionConflicts(session, nonAssociationSessionsList, doubleBookingSessionList, emptyList(), prisonBlockedList, sessionBlockedList, null)
    assertThat(session.sessionConflicts.size).isEqualTo(4)
    assertThat(session.sessionConflicts.map { it.sessionConflict }).containsAll(listOf(SESSION_DATE_BLOCKED, NON_ASSOCIATION, DOUBLE_BOOKING_OR_RESERVATION, PRISON_DATE_BLOCKED))
  }

  @Test
  fun `when a session visit order restriction is VO only and prisoner does not have any VOs - negative VOs then session is marked with no VOs session conflict`() {
    val session = createVisitSessionDto(visitDate, SessionTemplateVisitOrderRestrictionType.VO)
    val voBalance = VisitOrderPrisonerBalanceDto(prisonerId = "test", voBalance = -2, pvoBalance = 3)

    sessionConflictsUtil.addSessionConflicts(
      session = session,
      nonAssociationConflictSessions = emptyList(),
      doubleBookingConflictSessions = emptyList(),
      limitReachedSessions = emptyList(),
      prisonExcludeDates = emptyList(),
      sessionExcludeDates = emptyList(),
      voBalance = voBalance,
    )
    assertThat(session.sessionConflicts.size).isEqualTo(1)
    assertThat(session.sessionConflicts.map { it.sessionConflict }).containsOnly(SessionConflict.NO_VO_BALANCE)
  }

  @Test
  fun `when a session visit order restriction is VO only and prisoner does not have any VOs - zero VOs then session is marked with no VOs session conflict`() {
    val session = createVisitSessionDto(visitDate, SessionTemplateVisitOrderRestrictionType.VO)
    val voBalance = VisitOrderPrisonerBalanceDto(prisonerId = "test", voBalance = 0, pvoBalance = 3)

    sessionConflictsUtil.addSessionConflicts(
      session = session,
      nonAssociationConflictSessions = emptyList(),
      doubleBookingConflictSessions = emptyList(),
      limitReachedSessions = emptyList(),
      prisonExcludeDates = emptyList(),
      sessionExcludeDates = emptyList(),
      voBalance = voBalance,
    )
    assertThat(session.sessionConflicts.size).isEqualTo(1)
    assertThat(session.sessionConflicts.map { it.sessionConflict }).containsOnly(SessionConflict.NO_VO_BALANCE)
  }

  @Test
  fun `when a session visit order restriction is VO only and prisoner has VOs - then session is not marked with no VOs session conflict`() {
    val session = createVisitSessionDto(visitDate, SessionTemplateVisitOrderRestrictionType.VO)
    val voBalance = VisitOrderPrisonerBalanceDto(prisonerId = "test", voBalance = 1, pvoBalance = 3)

    sessionConflictsUtil.addSessionConflicts(
      session = session,
      nonAssociationConflictSessions = emptyList(),
      doubleBookingConflictSessions = emptyList(),
      limitReachedSessions = emptyList(),
      prisonExcludeDates = emptyList(),
      sessionExcludeDates = emptyList(),
      voBalance = voBalance,
    )
    assertThat(session.sessionConflicts).isEmpty()
  }

  @Test
  fun `when a session visit order restriction is PVO only and prisoner does not have any PVOs - negative PVOs then session is marked with no PVOs session conflict`() {
    val session = createVisitSessionDto(visitDate, SessionTemplateVisitOrderRestrictionType.PVO)
    val voBalance = VisitOrderPrisonerBalanceDto(prisonerId = "test", voBalance = 2, pvoBalance = -3)

    sessionConflictsUtil.addSessionConflicts(
      session = session,
      nonAssociationConflictSessions = emptyList(),
      doubleBookingConflictSessions = emptyList(),
      limitReachedSessions = emptyList(),
      prisonExcludeDates = emptyList(),
      sessionExcludeDates = emptyList(),
      voBalance = voBalance,
    )
    assertThat(session.sessionConflicts.size).isEqualTo(1)
    assertThat(session.sessionConflicts.map { it.sessionConflict }).containsOnly(SessionConflict.NO_PVO_BALANCE)
  }

  @Test
  fun `when a session visit order restriction is PVO only and prisoner does not have any PVOs - zero PVOs then session is marked with no PVOs session conflict`() {
    val session = createVisitSessionDto(visitDate, SessionTemplateVisitOrderRestrictionType.PVO)
    val voBalance = VisitOrderPrisonerBalanceDto(prisonerId = "test", voBalance = 2, pvoBalance = 0)

    sessionConflictsUtil.addSessionConflicts(
      session = session,
      nonAssociationConflictSessions = emptyList(),
      doubleBookingConflictSessions = emptyList(),
      limitReachedSessions = emptyList(),
      prisonExcludeDates = emptyList(),
      sessionExcludeDates = emptyList(),
      voBalance = voBalance,
    )
    assertThat(session.sessionConflicts.size).isEqualTo(1)
    assertThat(session.sessionConflicts.map { it.sessionConflict }).containsOnly(SessionConflict.NO_PVO_BALANCE)
  }

  @Test
  fun `when a session visit order restriction is PVO only and prisoner has PVOs - then session is not marked with no PVOs session conflict`() {
    val session = createVisitSessionDto(visitDate, SessionTemplateVisitOrderRestrictionType.PVO)
    val voBalance = VisitOrderPrisonerBalanceDto(prisonerId = "test", voBalance = 1, pvoBalance = 3)

    sessionConflictsUtil.addSessionConflicts(
      session = session,
      nonAssociationConflictSessions = emptyList(),
      doubleBookingConflictSessions = emptyList(),
      limitReachedSessions = emptyList(),
      prisonExcludeDates = emptyList(),
      sessionExcludeDates = emptyList(),
      voBalance = voBalance,
    )
    assertThat(session.sessionConflicts).isEmpty()
  }

  @Test
  fun `when a session visit order restriction is VO or PVO and prisoner does not have any VOs or PVOs - negative VOs and PVOs then session is marked with no PVOs session conflict`() {
    val session = createVisitSessionDto(visitDate, SessionTemplateVisitOrderRestrictionType.VO_PVO)
    val voBalance = VisitOrderPrisonerBalanceDto(prisonerId = "test", voBalance = -2, pvoBalance = -3)

    sessionConflictsUtil.addSessionConflicts(
      session = session,
      nonAssociationConflictSessions = emptyList(),
      doubleBookingConflictSessions = emptyList(),
      limitReachedSessions = emptyList(),
      prisonExcludeDates = emptyList(),
      sessionExcludeDates = emptyList(),
      voBalance = voBalance,
    )
    assertThat(session.sessionConflicts.size).isEqualTo(1)
    assertThat(session.sessionConflicts.map { it.sessionConflict }).containsOnly(SessionConflict.NO_VO_OR_PVO_BALANCE)
  }

  @Test
  fun `when a session visit order restriction is VO or PVO and prisoner does not have any VOs or PVOs - zero VOs and PVOs then session is marked with no PVOs session conflict`() {
    val session = createVisitSessionDto(visitDate, SessionTemplateVisitOrderRestrictionType.VO_PVO)
    val voBalance = VisitOrderPrisonerBalanceDto(prisonerId = "test", voBalance = 0, pvoBalance = 0)

    sessionConflictsUtil.addSessionConflicts(
      session = session,
      nonAssociationConflictSessions = emptyList(),
      doubleBookingConflictSessions = emptyList(),
      limitReachedSessions = emptyList(),
      prisonExcludeDates = emptyList(),
      sessionExcludeDates = emptyList(),
      voBalance = voBalance,
    )
    assertThat(session.sessionConflicts.size).isEqualTo(1)
    assertThat(session.sessionConflicts.map { it.sessionConflict }).containsOnly(SessionConflict.NO_VO_OR_PVO_BALANCE)
  }

  @Test
  fun `when a session visit order restriction is VO or PVO and prisoner has VOs but no PVOs then session is not marked with any session conflict`() {
    val session = createVisitSessionDto(visitDate, SessionTemplateVisitOrderRestrictionType.VO_PVO)
    val voBalance = VisitOrderPrisonerBalanceDto(prisonerId = "test", voBalance = 1, pvoBalance = -3)

    sessionConflictsUtil.addSessionConflicts(
      session = session,
      nonAssociationConflictSessions = emptyList(),
      doubleBookingConflictSessions = emptyList(),
      limitReachedSessions = emptyList(),
      prisonExcludeDates = emptyList(),
      sessionExcludeDates = emptyList(),
      voBalance = voBalance,
    )
    assertThat(session.sessionConflicts).isEmpty()
  }

  @Test
  fun `when a session visit order restriction is VO or PVO and prisoner has PVOs but no VOs then session is not marked with any session conflict`() {
    val session = createVisitSessionDto(visitDate, SessionTemplateVisitOrderRestrictionType.VO_PVO)
    val voBalance = VisitOrderPrisonerBalanceDto(prisonerId = "test", voBalance = -1, pvoBalance = 1)

    sessionConflictsUtil.addSessionConflicts(
      session = session,
      nonAssociationConflictSessions = emptyList(),
      doubleBookingConflictSessions = emptyList(),
      limitReachedSessions = emptyList(),
      prisonExcludeDates = emptyList(),
      sessionExcludeDates = emptyList(),
      voBalance = voBalance,
    )
    assertThat(session.sessionConflicts).isEmpty()
  }

  @Test
  fun `when a session visit order restriction is VO or PVO and prisoner has both VOs and PVOs then session is not marked with any session conflict`() {
    val session = createVisitSessionDto(visitDate, SessionTemplateVisitOrderRestrictionType.VO_PVO)
    val voBalance = VisitOrderPrisonerBalanceDto(prisonerId = "test", voBalance = 1, pvoBalance = 1)

    sessionConflictsUtil.addSessionConflicts(
      session = session,
      nonAssociationConflictSessions = emptyList(),
      doubleBookingConflictSessions = emptyList(),
      limitReachedSessions = emptyList(),
      prisonExcludeDates = emptyList(),
      sessionExcludeDates = emptyList(),
      voBalance = voBalance,
    )
    assertThat(session.sessionConflicts).isEmpty()
  }

  @Test
  fun `when a session visit order restriction is NONE and prisoner does not have any VOs or PVOs - negative VOs and PVOs then session is not marked with any session conflict`() {
    val session = createVisitSessionDto(visitDate, SessionTemplateVisitOrderRestrictionType.NONE)
    val voBalance = VisitOrderPrisonerBalanceDto(prisonerId = "test", voBalance = -2, pvoBalance = -3)

    sessionConflictsUtil.addSessionConflicts(
      session = session,
      nonAssociationConflictSessions = emptyList(),
      doubleBookingConflictSessions = emptyList(),
      limitReachedSessions = emptyList(),
      prisonExcludeDates = emptyList(),
      sessionExcludeDates = emptyList(),
      voBalance = voBalance,
    )
    assertThat(session.sessionConflicts).isEmpty()
  }

  @Test
  fun `when a session visit order restriction is NONE and prisoner does not have any VOs or PVOs - zero VOs and PVOs then session is not marked with any session conflict`() {
    val session = createVisitSessionDto(visitDate, SessionTemplateVisitOrderRestrictionType.NONE)
    val voBalance = VisitOrderPrisonerBalanceDto(prisonerId = "test", voBalance = 0, pvoBalance = 0)

    sessionConflictsUtil.addSessionConflicts(
      session = session,
      nonAssociationConflictSessions = emptyList(),
      doubleBookingConflictSessions = emptyList(),
      limitReachedSessions = emptyList(),
      prisonExcludeDates = emptyList(),
      sessionExcludeDates = emptyList(),
      voBalance = voBalance,
    )
    assertThat(session.sessionConflicts).isEmpty()
  }

  @Test
  fun `when a session visit order restriction is NONE and prisoner has VOs but no PVOs then session is not marked with any session conflict`() {
    val session = createVisitSessionDto(visitDate, SessionTemplateVisitOrderRestrictionType.NONE)
    val voBalance = VisitOrderPrisonerBalanceDto(prisonerId = "test", voBalance = 1, pvoBalance = -3)

    sessionConflictsUtil.addSessionConflicts(
      session = session,
      nonAssociationConflictSessions = emptyList(),
      doubleBookingConflictSessions = emptyList(),
      limitReachedSessions = emptyList(),
      prisonExcludeDates = emptyList(),
      sessionExcludeDates = emptyList(),
      voBalance = voBalance,
    )
    assertThat(session.sessionConflicts).isEmpty()
  }

  @Test
  fun `when a session visit order restriction is NONE and prisoner has PVOs but no VOs then session is not marked with any session conflict`() {
    val session = createVisitSessionDto(visitDate, SessionTemplateVisitOrderRestrictionType.NONE)
    val voBalance = VisitOrderPrisonerBalanceDto(prisonerId = "test", voBalance = -1, pvoBalance = 1)

    sessionConflictsUtil.addSessionConflicts(
      session = session,
      nonAssociationConflictSessions = emptyList(),
      doubleBookingConflictSessions = emptyList(),
      limitReachedSessions = emptyList(),
      prisonExcludeDates = emptyList(),
      sessionExcludeDates = emptyList(),
      voBalance = voBalance,
    )
    assertThat(session.sessionConflicts).isEmpty()
  }

  @Test
  fun `when a session visit order restriction is NONE and prisoner has both VOs and PVOs then session is not marked with any session conflict`() {
    val session = createVisitSessionDto(visitDate, SessionTemplateVisitOrderRestrictionType.NONE)
    val voBalance = VisitOrderPrisonerBalanceDto(prisonerId = "test", voBalance = 1, pvoBalance = 1)

    sessionConflictsUtil.addSessionConflicts(
      session = session,
      nonAssociationConflictSessions = emptyList(),
      doubleBookingConflictSessions = emptyList(),
      limitReachedSessions = emptyList(),
      prisonExcludeDates = emptyList(),
      sessionExcludeDates = emptyList(),
      voBalance = voBalance,
    )
    assertThat(session.sessionConflicts).isEmpty()
  }
}
