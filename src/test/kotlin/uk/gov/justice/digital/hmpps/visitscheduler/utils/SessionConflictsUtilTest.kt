package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.DOUBLE_BOOKING_OR_RESERVATION
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.NON_ASSOCIATION
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.PRISON_DATE_BLOCKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.SESSION_DATE_BLOCKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.AdditionalSessionConflictInfoDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.NonAssociationConflictSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.NonAssociationConflictType
import java.time.DayOfWeek.MONDAY
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@ExtendWith(MockitoExtension::class)
class SessionConflictsUtilTest {

  val sessionConflictsUtil = SessionConflictsUtil()

  val visitDate: LocalDate = LocalDate.now().with(TemporalAdjusters.next(MONDAY))

  fun createVisitSessionDto(visitDate: LocalDate): VisitSessionDto = VisitSessionDto(
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
  )

  val session = createVisitSessionDto(visitDate)

  @Test
  fun `when non associations exists then session is marked with non-association session conflict`() {
    val nonAssociationSessionsList = listOf(
      NonAssociationConflictSessionDto("non-association-1", NonAssociationConflictType.VISIT, "ref2", visitDate),
    )
    sessionConflictsUtil.addSessionConflicts(session, nonAssociationSessionsList, emptyList(), emptyList(), emptyList())
    assertThat(session.sessionConflicts.size).isEqualTo(1)
    assertThat(session.sessionConflicts[0].sessionConflict).isEqualTo(NON_ASSOCIATION)
    assertThat(session.sessionConflicts.map { it.additionalAttributes }.flatten()).containsAll(
      listOf(
        listOf(
          AdditionalSessionConflictInfoDto("prisonerId", "non-association-1"),
          AdditionalSessionConflictInfoDto("type", "VISIT"),
          AdditionalSessionConflictInfoDto("reference", "ref2"),
        ),
      ),
    )
  }

  @Test
  fun `when non associations exists but not for same date then session is marked with non-association session conflict`() {
    val nonAssociationSessionsList = listOf(
      NonAssociationConflictSessionDto("non-association-1", NonAssociationConflictType.VISIT, "ref2", visitDate.plusDays(1)),
    )
    sessionConflictsUtil.addSessionConflicts(session, nonAssociationSessionsList, emptyList(), emptyList(), emptyList())
    assertThat(session.sessionConflicts.size).isEqualTo(0)
  }

  @Test
  fun `when non associations do not exist then session is not marked with non-association session conflict`() {
    val nonAssociationSessionsList = emptyList<NonAssociationConflictSessionDto>()
    sessionConflictsUtil.addSessionConflicts(session, nonAssociationSessionsList, emptyList(), emptyList(), emptyList())
    assertThat(session.sessionConflicts.size).isEqualTo(0)
  }

  @Test
  fun `when booking for session does not exist then session is not marked with double booking session conflict`() {
    val doubleBookingSessionList = emptyList<VisitSessionDto>()
    sessionConflictsUtil.addSessionConflicts(session, emptyList(), doubleBookingSessionList, emptyList(), emptyList())
    assertThat(session.sessionConflicts.size).isEqualTo(0)
  }

  @Test
  fun `when booking exists for session but not for same date then session is not marked with double booking session conflict`() {
    val doubleBookingSessionList = listOf(createVisitSessionDto(visitDate.plusDays(1)))
    sessionConflictsUtil.addSessionConflicts(session, emptyList(), doubleBookingSessionList, emptyList(), emptyList())
    assertThat(session.sessionConflicts.size).isEqualTo(0)
  }

  @Test
  fun `when booking for session exists then session is marked with double booking session conflict`() {
    val doubleBookingSessionList = listOf(createVisitSessionDto(visitDate))
    sessionConflictsUtil.addSessionConflicts(session, emptyList(), doubleBookingSessionList, emptyList(), emptyList())
    assertThat(session.sessionConflicts.size).isEqualTo(1)
    assertThat(session.sessionConflicts[0].sessionConflict).isEqualTo(DOUBLE_BOOKING_OR_RESERVATION)
  }

  @Test
  fun `when date is not blocked by prison then session is not marked with prison blocked session conflict`() {
    val prisonBlockedList = emptyList<LocalDate>()
    sessionConflictsUtil.addSessionConflicts(session, emptyList(), emptyList(), prisonBlockedList, emptyList())
    assertThat(session.sessionConflicts.size).isEqualTo(0)
  }

  @Test
  fun `when same date is not blocked by prison then session is not marked with prison blocked session conflict`() {
    val prisonBlockedList = listOf<LocalDate>(visitDate.plusDays(1))
    sessionConflictsUtil.addSessionConflicts(session, emptyList(), emptyList(), prisonBlockedList, emptyList())
    assertThat(session.sessionConflicts.size).isEqualTo(0)
  }

  @Test
  fun `when date is blocked for by prison then session is marked with prison blocked session conflict`() {
    val prisonBlockedList = listOf(visitDate)
    sessionConflictsUtil.addSessionConflicts(session, emptyList(), emptyList(), prisonBlockedList, emptyList())
    assertThat(session.sessionConflicts.size).isEqualTo(1)
    assertThat(session.sessionConflicts[0].sessionConflict).isEqualTo(PRISON_DATE_BLOCKED)
  }

  @Test
  fun `when session is not blocked then session is not marked with session blocked session conflict`() {
    val sessionBlockedList = emptyList<LocalDate>()
    sessionConflictsUtil.addSessionConflicts(session, emptyList(), emptyList(), emptyList(), sessionBlockedList)
    assertThat(session.sessionConflicts.size).isEqualTo(0)
  }

  @Test
  fun `when session is not blocked for same date then session is not marked with session blocked session conflict`() {
    val sessionBlockedList = listOf<LocalDate>(visitDate.plusDays(1))
    sessionConflictsUtil.addSessionConflicts(session, emptyList(), emptyList(), emptyList(), sessionBlockedList)
    assertThat(session.sessionConflicts.size).isEqualTo(0)
  }

  @Test
  fun `when session is blocked for same date then session is marked with session blocked session conflict`() {
    val sessionBlockedList = listOf(visitDate)
    sessionConflictsUtil.addSessionConflicts(session, emptyList(), emptyList(), emptyList(), sessionBlockedList)
    assertThat(session.sessionConflicts.size).isEqualTo(1)
    assertThat(session.sessionConflicts[0].sessionConflict).isEqualTo(SESSION_DATE_BLOCKED)
  }

  @Test
  fun `when multiple session conflicts then session is marked with all flagged session conflicts`() {
    val nonAssociationSessionsList = listOf(
      NonAssociationConflictSessionDto("non-association-1", NonAssociationConflictType.VISIT, "ref2", visitDate),
    )
    val doubleBookingSessionList = listOf(createVisitSessionDto(visitDate))
    val prisonBlockedList = listOf(visitDate)
    val sessionBlockedList = listOf(visitDate)

    sessionConflictsUtil.addSessionConflicts(session, nonAssociationSessionsList, doubleBookingSessionList, prisonBlockedList, sessionBlockedList)
    assertThat(session.sessionConflicts.size).isEqualTo(4)
    assertThat(session.sessionConflicts.map { it.sessionConflict }).containsAll(listOf(SESSION_DATE_BLOCKED, NON_ASSOCIATION, DOUBLE_BOOKING_OR_RESERVATION, PRISON_DATE_BLOCKED))
  }
}
