package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_SESSION_CONTROLLER_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.REMAND_VISITS_LIMIT_REACHED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@DisplayName("GET $VISIT_SESSION_CONTROLLER_PATH - Tests to check for REMAND_VISITS_LIMIT_REACHED flag.")
class GetSessionsRemandLimitTest : IntegrationTestBase() {

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  private val remandPrisonerId = "A0000001"
  private val convictedPrisonerId = "A0000002"

  private val prisonCode = "STC"

  private lateinit var authHttpHeaders: (HttpHeaders) -> Unit

  private lateinit var sessionTemplates: List<SessionTemplate>

  @BeforeEach
  internal fun setUpTests() {
    authHttpHeaders = setAuthorisation(roles = requiredRole)
    prison = prisonEntityHelper.create(prisonCode = prisonCode, policyNoticeDaysMin = 0, policyNoticeDaysMax = 14, remandVisitLimitPerWeek = 2, weekStartDay = DayOfWeek.MONDAY)
    sessionTemplates = createSessionTemplates()
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId = remandPrisonerId, prisonCode = prisonCode, convictedStatus = "REMAND")
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId = convictedPrisonerId, prisonCode = prisonCode, convictedStatus = "CONVICTED")
  }

  @Test
  fun `when remand prisoner has visits booked and weekly limit has been reached then REMAND_VISITS_LIMIT_REACHED flag is set for all non booked sessions`() {
    // Given
    val startDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val week1VisitDays = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
    val week2VisitDays = listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    val week3VisitDays = listOf(DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)
    createVisits(remandPrisonerId, week1VisitDays, startDate)
    createVisits(remandPrisonerId, week2VisitDays, startDate.plusWeeks(1))
    createVisits(remandPrisonerId, week3VisitDays, startDate.plusWeeks(2))

    // When
    val responseSpec = callGetSessions(prisonCode, remandPrisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(14)
    val week1StartDate = startDate
    val week1EndDate = startDate.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
    val week2StartDate = week1EndDate.plusDays(1)
    val week2EndDate = week1EndDate.plusWeeks(1)
    val week3StartDate = week2StartDate.plusWeeks(1)
    val week3EndDate = week2EndDate.plusWeeks(1)

    val week1RemandVisits = visitSessionResults.filter { it.startTimestamp.toLocalDate() in week1StartDate..week1EndDate }
      .filter { !week1VisitDays.contains(it.startTimestamp.toLocalDate().dayOfWeek) }
    assertThat(week1RemandVisits).allMatch { it.sessionConflicts.contains(REMAND_VISITS_LIMIT_REACHED) }

    val week2RemandVisits = visitSessionResults.filter { it.startTimestamp.toLocalDate() in week2StartDate..week2EndDate }
      .filter { !week2VisitDays.contains(it.startTimestamp.toLocalDate().dayOfWeek) }
    assertThat(week2RemandVisits).allMatch { it.sessionConflicts.contains(REMAND_VISITS_LIMIT_REACHED) }

    val week3RemandVisits = visitSessionResults.filter { it.startTimestamp.toLocalDate() in week3StartDate..week3EndDate }
      .filter { !week3VisitDays.contains(it.startTimestamp.toLocalDate().dayOfWeek) }
    assertThat(week3RemandVisits).allMatch { it.sessionConflicts.contains(REMAND_VISITS_LIMIT_REACHED) }
  }

  @Test
  fun `when remand prisoner has visits booked but weekly limit has not been reached then REMAND_VISITS_LIMIT_REACHED flag is not set for non booked sessions`() {
    // Given
    val startDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val week1VisitDays = listOf(DayOfWeek.MONDAY)
    val week2VisitDays = listOf(DayOfWeek.WEDNESDAY)
    val week3VisitDays = emptyList<DayOfWeek>()
    createVisits(remandPrisonerId, week1VisitDays, startDate)
    createVisits(remandPrisonerId, week2VisitDays, startDate.plusWeeks(1))
    createVisits(remandPrisonerId, week3VisitDays, startDate.plusWeeks(2))

    // When
    val responseSpec = callGetSessions(prisonCode, remandPrisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(14)
    val week1StartDate = startDate
    val week1EndDate = startDate.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
    val week2StartDate = week1StartDate.plusWeeks(1)
    val week2EndDate = week1EndDate.plusWeeks(1)
    val week3StartDate = week2StartDate.plusWeeks(1)
    val week3EndDate = week2EndDate.plusWeeks(1)

    val week1RemandVisits = visitSessionResults.filter { it.startTimestamp.toLocalDate() in week1StartDate..week1EndDate }
      .filter { week1VisitDays.contains(it.startTimestamp.toLocalDate().dayOfWeek) }
    assertThat(week1RemandVisits).noneMatch { it.sessionConflicts.contains(REMAND_VISITS_LIMIT_REACHED) }

    val week2RemandVisits = visitSessionResults.filter { it.startTimestamp.toLocalDate() in week2StartDate..week2EndDate }
      .filter { week2VisitDays.contains(it.startTimestamp.toLocalDate().dayOfWeek) }
    assertThat(week2RemandVisits).noneMatch { it.sessionConflicts.contains(REMAND_VISITS_LIMIT_REACHED) }

    val week3RemandVisits = visitSessionResults.filter { it.startTimestamp.toLocalDate() in week3StartDate..week3EndDate }
      .filter { week3VisitDays.contains(it.startTimestamp.toLocalDate().dayOfWeek) }
    assertThat(week3RemandVisits).noneMatch { it.sessionConflicts.contains(REMAND_VISITS_LIMIT_REACHED) }
  }

  @Test
  fun `when remand prisoner has no visits booked then REMAND_VISITS_LIMIT_REACHED flag is not set for non booked sessions`() {
    // Given
    // no visits booked

    // When
    val responseSpec = callGetSessions(prisonCode, remandPrisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(14)
    assertThat(visitSessionResults).noneMatch { it.sessionConflicts.contains(REMAND_VISITS_LIMIT_REACHED) }
  }

  @Test
  fun `when prisoner is not on REMAND the REMAND_VISITS_LIMIT_REACHED flag is not set for any session`() {
    // Given
    val startDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val week1VisitDays = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
    val week2VisitDays = listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    val week3VisitDays = listOf(DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)
    createVisits(convictedPrisonerId, week1VisitDays, startDate)
    createVisits(convictedPrisonerId, week2VisitDays, startDate.plusWeeks(1))
    createVisits(convictedPrisonerId, week3VisitDays, startDate.plusWeeks(2))

    // When
    val responseSpec = callGetSessions(prisonCode, convictedPrisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(14)
    assertThat(visitSessionResults).noneMatch { it.sessionConflicts.contains(REMAND_VISITS_LIMIT_REACHED) }
  }

  private fun getResults(returnResult: BodyContentSpec): Array<VisitSessionDto> = objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)

  private fun createSessionTemplates(): List<SessionTemplate> {
    val sessionTemplates = mutableListOf<SessionTemplate>()
    for (dayOfWeek in DayOfWeek.entries) {
      sessionTemplates.add(
        sessionTemplateEntityHelper.create(
          validFromDate = LocalDate.now().minusMonths(1),
          dayOfWeek = dayOfWeek,
          prisonCode = prisonCode,
        ),
      )
    }

    return sessionTemplates.toList()
  }

  private fun createVisits(prisonerId: String, dayOfWeeks: List<DayOfWeek>, date: LocalDate): MutableList<Visit> {
    val visits = mutableListOf<Visit>()

    dayOfWeeks.forEach { dayOfWeek ->
      val sessionTemplate = sessionTemplates.first { it.dayOfWeek == dayOfWeek }
      val visitDate = date.with(TemporalAdjusters.nextOrSame(dayOfWeek))
      visits.add(
        this.visitEntityHelper.create(
          prisonerId = prisonerId,
          prisonCode = prisonCode,
          visitRoom = sessionTemplate.visitRoom,
          slotDate = visitDate,
          visitStart = sessionTemplate.startTime,
          visitEnd = sessionTemplate.endTime,
          visitStatus = VisitStatus.BOOKED,
          sessionTemplate = sessionTemplate,
        ),
      )
    }
    return visits
  }
}
