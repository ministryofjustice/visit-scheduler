package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.DOUBLE_BOOKING_OR_RESERVATION
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.NON_ASSOCIATION
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.PRISON_DATE_BLOCKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.SESSION_DATE_BLOCKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionTemplateVisitOrderRestrictionType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionConflictDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import java.time.LocalDate
import java.util.*

class SessionCountsUtilTest {
  val sessionCountsUtil = SessionCountsUtil()

  private fun createVisitSessionDto(
    visitDate: LocalDate,
    sessionConflicts: List<SessionConflict>,
    openVisitCapacity: Int = 10,
    openVisitBookedCount: Int = 1,
    closedVisitCapacity: Int = 10,
    closedVisitBookedCount: Int = 1,
  ): VisitSessionDto = VisitSessionDto(
    sessionTemplateReference = UUID.randomUUID().toString(),
    visitRoom = "visit-room",
    visitType = VisitType.SOCIAL,
    prisonCode = "HEI",
    openVisitCapacity = openVisitCapacity,
    openVisitBookedCount = openVisitBookedCount,
    closedVisitCapacity = closedVisitCapacity,
    closedVisitBookedCount = closedVisitBookedCount,
    startTimestamp = visitDate.atTime(10, 0),
    endTimestamp = visitDate.atTime(11, 0),
    sessionConflicts = createSessionConflictDto(sessionConflicts),
    visitOrderRestriction = SessionTemplateVisitOrderRestrictionType.NONE,
    isAgeRestricted = false,
    ageRestriction = 18,
  )

  private fun createSessionConflictDto(sessionConflicts: List<SessionConflict>): MutableList<SessionConflictDto> {
    val sessionConflictDtos = mutableListOf<SessionConflictDto>()

    sessionConflicts.forEach { sessionConflict ->
      sessionConflictDtos.add(SessionConflictDto(sessionConflict, emptyList()))
    }

    return sessionConflictDtos
  }

  @Test
  fun `when sessions are available with session conflicts then counts are reflected correctly`() {
    val visitDate = LocalDate.now().plusDays(1)

    // available - adds to total
    val session1 = createVisitSessionDto(visitDate, sessionConflicts = emptyList())
    // unavailable
    val session2 = createVisitSessionDto(visitDate, sessionConflicts = listOf(PRISON_DATE_BLOCKED))
    // unavailable
    val session3 = createVisitSessionDto(visitDate, sessionConflicts = listOf(SESSION_DATE_BLOCKED))
    // available - adds to total and already booked
    val session4 = createVisitSessionDto(visitDate, sessionConflicts = listOf(DOUBLE_BOOKING_OR_RESERVATION))
    // available - adds to total and non association count
    val session5 = createVisitSessionDto(visitDate, sessionConflicts = listOf(NON_ASSOCIATION))
    // available - adds to total and already booked
    val session6 = createVisitSessionDto(visitDate, sessionConflicts = listOf(DOUBLE_BOOKING_OR_RESERVATION))
    // available - adds to total and already booked
    val session7 = createVisitSessionDto(visitDate, sessionConflicts = listOf(DOUBLE_BOOKING_OR_RESERVATION))

    val sessions = listOf(session1, session2, session3, session4, session5, session6, session7)

    val counts = sessionCountsUtil.getSessionCounts(sessions)
    assertThat(counts[SessionCountType.TOTAL_SESSIONS]).isEqualTo(5)
    assertThat(counts[SessionCountType.UNAVAILABLE_ALREADY_BOOKED]).isEqualTo(3)
    assertThat(counts[SessionCountType.UNAVAILABLE_FULLY_BOOKED]).isEqualTo(0)
    assertThat(counts[SessionCountType.UNAVAILABLE_NON_ASSOCIATION]).isEqualTo(1)
  }

  @Test
  fun `when sessions are available with multiple session conflicts then counts are reflected correctly`() {
    val visitDate = LocalDate.now().plusDays(1)

    // available - adds to total
    val session1 = createVisitSessionDto(visitDate, sessionConflicts = emptyList())
    // unavailable
    val session2 = createVisitSessionDto(visitDate, sessionConflicts = listOf(PRISON_DATE_BLOCKED))
    // unavailable
    val session3 = createVisitSessionDto(visitDate, sessionConflicts = listOf(SESSION_DATE_BLOCKED))
    // available - adds to total and already booked
    val session4 = createVisitSessionDto(visitDate, sessionConflicts = listOf(DOUBLE_BOOKING_OR_RESERVATION))
    // available - adds to total and non association count
    val session5 = createVisitSessionDto(visitDate, sessionConflicts = listOf(NON_ASSOCIATION))
    // available - adds to total and already booked, not to non-associations count
    val session6 = createVisitSessionDto(visitDate, sessionConflicts = listOf(DOUBLE_BOOKING_OR_RESERVATION, NON_ASSOCIATION))
    // available - adds to total and already booked, not to non-associations count
    val session7 = createVisitSessionDto(visitDate, sessionConflicts = listOf(DOUBLE_BOOKING_OR_RESERVATION, NON_ASSOCIATION))

    val sessions = listOf(session1, session2, session3, session4, session5, session6, session7)

    val counts = sessionCountsUtil.getSessionCounts(sessions)
    assertThat(counts[SessionCountType.TOTAL_SESSIONS]).isEqualTo(5)
    assertThat(counts[SessionCountType.UNAVAILABLE_ALREADY_BOOKED]).isEqualTo(3)
    assertThat(counts[SessionCountType.UNAVAILABLE_FULLY_BOOKED]).isEqualTo(0)
    assertThat(counts[SessionCountType.UNAVAILABLE_NON_ASSOCIATION]).isEqualTo(1)
  }
}
