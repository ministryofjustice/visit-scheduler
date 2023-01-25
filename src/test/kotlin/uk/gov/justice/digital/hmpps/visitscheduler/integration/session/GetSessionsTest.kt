package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.SessionConflict
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CHANGING
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.DayOfWeek
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

@DisplayName("Get /visit-sessions")
class GetSessionsTest(@Autowired private val objectMapper: ObjectMapper) : IntegrationTestBase() {

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  private val prison: Prison = Prison(code = "MDI", active = true)

  @BeforeEach
  internal fun setUpTests() {
  }

// PrisonOffenderSearchMockServer
  @Test
  fun `visit sessions are returned for a prison for a single schedule`() {
    // Given

    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek
    )

    // When
    val responseSpec = callGetSessions()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate)
  }

  @Test
  fun `visit sessions are returned for enhanced prisoner a prison for a single schedule`() {
    // Given
    val prisonCode = "MDI"
    val prisonerId = "A1234AA"

    prisonOffenderSearchMockServer.stubGetPrisonerIncentiveLevel(prisonerId, prisonCode, "ENH")
    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prisonCode)

    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate = sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      enhanced = true
    )

    // When

    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate)
  }

  @Test
  fun `visit sessions are returned for a standard prisoner for a schedule that is not enhanced`() {
    // Given
    val prisonCode = "MDI"
    val prisonerId = "A1234AA"

    prisonOffenderSearchMockServer.stubGetPrisonerIncentiveLevel(prisonerId, prisonCode, "STD")
    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prisonCode)

    val nextAllowedDay = getNextAllowedDay()

    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      enhanced = false
    )

    // When

    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(1)
  }

  @Test
  fun `no visit sessions are returned for a standard prisoner for a schedule that is enhanced`() {
    // Given
    val prisonCode = "MDI"
    val prisonerId = "A1234AA"

    prisonOffenderSearchMockServer.stubGetPrisonerIncentiveLevel(prisonerId, prisonCode, "STD")
    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prisonCode)

    val nextAllowedDay = getNextAllowedDay()

    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      enhanced = true
    )

    // When

    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(0)
  }

  @Test
  fun `bi weekly schedule - test for sunday change boundary`() {

    val today = LocalDate.now()
    val todayIsTheWeekEnd = today.dayOfWeek in listOf<DayOfWeek>(SUNDAY, SATURDAY)

    // Given
    val startFromWeek1 = today.with(TemporalAdjusters.next(MONDAY)).minusWeeks(1)
    sessionTemplateEntityHelper.create(
      validFromDate = startFromWeek1,
      visitRoom = "Alternate 1",
      dayOfWeek = SUNDAY,
      biWeekly = true
    )

    val startFromWeek2 = LocalDate.now().with(TemporalAdjusters.next(MONDAY)).minusWeeks(2)

    sessionTemplateEntityHelper.create(
      validFromDate = startFromWeek2,
      visitRoom = "Alternate 2",
      dayOfWeek = SUNDAY,
      biWeekly = true
    )

    // When
    val responseSpec = callGetSessions()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()

    val visitSessionResults = getResults(returnResult)
    if (todayIsTheWeekEnd) {
      // On the weekend it skips to the other session template / schedule because we cannot book with in 24 hrs
      Assertions.assertThat(visitSessionResults[0].visitRoomName).isEqualTo("Alternate 2")
      Assertions.assertThat(visitSessionResults[1].visitRoomName).isEqualTo("Alternate 1")
    } else {
      Assertions.assertThat(visitSessionResults[0].visitRoomName).isEqualTo("Alternate 1")
      Assertions.assertThat(visitSessionResults[1].visitRoomName).isEqualTo("Alternate 2")
    }
  }

  @Test
  fun `visit sessions are returned for a prison for a weekly schedule`() {
    // Given
    val nextAllowedDay = getNextAllowedDay()
    val dayAfterNextAllowedDay = nextAllowedDay.plusDays(1)

    val nextDaySessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek
    )

    val dayAfterNextSessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = dayAfterNextAllowedDay,
      startTime = LocalTime.parse("10:30"),
      endTime = LocalTime.parse("11:30"),
      dayOfWeek = dayAfterNextAllowedDay.dayOfWeek,
    )

    // When
    val responseSpec = callGetSessions(0, 28)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()

    val visitSessionResults = getResults(returnResult)

    Assertions.assertThat(visitSessionResults.size).isEqualTo(8)
    assertSession(visitSessionResults[0], nextAllowedDay, nextDaySessionTemplate)
    assertSession(visitSessionResults[1], dayAfterNextAllowedDay, dayAfterNextSessionTemplate)
    assertSession(visitSessionResults[2], nextAllowedDay.plusWeeks(1), nextDaySessionTemplate)
    assertSession(visitSessionResults[3], dayAfterNextAllowedDay.plusWeeks(1), dayAfterNextSessionTemplate)
    assertSession(visitSessionResults[4], nextAllowedDay.plusWeeks(2), nextDaySessionTemplate)
    assertSession(visitSessionResults[5], dayAfterNextAllowedDay.plusWeeks(2), dayAfterNextSessionTemplate)
    assertSession(visitSessionResults[6], nextAllowedDay.plusWeeks(3), nextDaySessionTemplate)
    assertSession(visitSessionResults[7], dayAfterNextAllowedDay.plusWeeks(3), dayAfterNextSessionTemplate)
  }

  @Test
  fun `visit sessions are returned for a prison when day of week and schedule starts and ends on the same Day`() {
    // Given
    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      dayOfWeek = nextAllowedDay.dayOfWeek,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00")
    )

    // When
    val responseSpec = callGetSessions()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()

    val visitSessionResults = getResults(returnResult)

    Assertions.assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate)
  }

  @Test
  fun `visit sessions are not returned for a prison when schedule starts and ends on the previous day`() {
    // Given

    val nextAllowedDay = getNextAllowedDay()
    val previousDay = nextAllowedDay.minusDays(1)

    sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      dayOfWeek = previousDay.dayOfWeek,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00")
    )

    // When
    val responseSpec = callGetSessions()

    // Then
    assertNoResponse(responseSpec)
  }

  @Test
  fun `visit sessions are not returned when policy notice min days is greater than max without valid to date`() {
    // Given
    val policyNoticeDaysMin = 14
    val policyNoticeDaysMax = 1

    val nextAllowedDay = getNextAllowedDay()

    sessionTemplateEntityHelper.create(validFromDate = nextAllowedDay)

    // When
    val responseSpec = callGetSessions(policyNoticeDaysMin, policyNoticeDaysMax)

    // Then
    assertNoResponse(responseSpec)
  }

  @Test
  fun `visit sessions are not returned when start date is after policy notice min and max days`() {
    // Given
    val policyNoticeDaysMin = 0
    val policyNoticeDaysMax = 1

    sessionTemplateEntityHelper.create(validFromDate = LocalDate.now().plusDays(2))

    // When
    val responseSpec = callGetSessions(policyNoticeDaysMin, policyNoticeDaysMax)

    // Then
    assertNoResponse(responseSpec)
  }

  @Test
  fun `visit sessions are not returned when policy notice min days is greater than max with valid to date`() {
    // Given
    val policyNoticeDaysMin = 14
    val policyNoticeDaysMax = 1

    val nextAllowedDay = getNextAllowedDay()

    sessionTemplateEntityHelper.create(validFromDate = nextAllowedDay, validToDate = nextAllowedDay)

    // When
    val responseSpec = callGetSessions(policyNoticeDaysMin, policyNoticeDaysMax)

    // Then
    assertNoResponse(responseSpec)
  }

  @Test
  fun `visit sessions that are no longer valid are not returned`() {
    // Given
    sessionTemplateEntityHelper.create(
      validFromDate = LocalDate.now().minusDays(1),
      validToDate = LocalDate.now().minusDays(1)
    )

    // When
    val responseSpec = callGetSessions()

    // Then
    assertNoResponse(responseSpec)
  }

  @Test
  fun `sessions that start after the max policy notice days after current date are not returned`() {
    // Given
    sessionTemplateEntityHelper.create(
      validFromDate = LocalDate.now().plusMonths(6),
      validToDate = LocalDate.now().plusMonths(10)
    )

    // When
    val responseSpec = callGetSessions()

    // Then
    assertNoResponse(responseSpec)
  }

  @Test
  fun `visit sessions include reserved and booked open visit count`() {

    // Given
    val nextAllowedDay = this.getNextAllowedDay()
    val dateTime = nextAllowedDay.atTime(9, 0)
    val startTime = dateTime.toLocalTime()
    val endTime = dateTime.plusHours(1)

    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = startTime,
      endTime = endTime.toLocalTime(),
      dayOfWeek = nextAllowedDay.dayOfWeek
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime,
      visitEnd = endTime,
      visitType = SOCIAL,
      visitStatus = RESERVED,
      visitRestriction = OPEN
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime,
      visitEnd = endTime,
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime,
      visitEnd = endTime,
      visitType = SOCIAL,
      visitStatus = CANCELLED,
      visitRestriction = OPEN
    )

    // When
    val responseSpec = callGetSessions()

    // Then
    assertBookCounts(responseSpec, openCount = 2, closeCount = 0)
  }

  @Test
  fun `visit sessions exclude visits with changing status in visit count`() {

    // Given
    val nextAllowedDay = this.getNextAllowedDay()
    val dateTime = nextAllowedDay.atTime(9, 0)
    val startTime = dateTime.toLocalTime()
    val endTime = dateTime.plusHours(1)

    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = startTime,
      endTime = endTime.toLocalTime(),
      dayOfWeek = nextAllowedDay.dayOfWeek
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime,
      visitEnd = endTime,
      visitType = SOCIAL,
      visitStatus = CHANGING,
      visitRestriction = OPEN
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime.plusWeeks(1),
      visitEnd = endTime,
      visitType = SOCIAL,
      visitStatus = CHANGING,
      visitRestriction = CLOSED
    )

    // When
    val responseSpec = callGetSessions()

    // Then
    assertBookCounts(responseSpec, openCount = 0, closeCount = 0)
  }

  @Test
  fun `visit sessions include reserved and booked closed visit count`() {

    // Given
    val nextAllowedDay = this.getNextAllowedDay()
    val dateTime = nextAllowedDay.atTime(9, 0)
    val startTime = dateTime.toLocalTime()
    val endTime = dateTime.plusHours(1)

    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = startTime,
      endTime = endTime.toLocalTime(),
      dayOfWeek = nextAllowedDay.dayOfWeek
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime,
      visitEnd = endTime,
      visitType = SOCIAL,
      visitStatus = RESERVED,
      visitRestriction = CLOSED
    )
    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime,
      visitEnd = endTime,
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = CLOSED
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime,
      visitEnd = endTime,
      visitType = SOCIAL,
      visitStatus = CANCELLED,
      visitRestriction = CLOSED
    )

    // When
    val responseSpec = callGetSessions()

    // Then
    assertBookCounts(responseSpec, openCount = 0, closeCount = 2)
  }

  @Test
  fun `visit sessions visit count includes only visits within session period`() {

    // Given
    val nextAllowedDay = this.getNextAllowedDay()
    val dateTime = nextAllowedDay.atTime(9, 0)
    val startTime = dateTime.toLocalTime()
    val endTime = dateTime.plusHours(1)

    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = startTime,
      endTime = endTime.toLocalTime(),
      dayOfWeek = nextAllowedDay.dayOfWeek
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime.minusHours(1),
      visitEnd = endTime,
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime, visitEnd = dateTime.plusMinutes(30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime.plusMinutes(30),
      visitEnd = dateTime.plusHours(1),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime.plusHours(1), visitEnd = dateTime.plusHours(2),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    // When
    val responseSpec = callGetSessions()

    // Then
    assertBookCounts(responseSpec, openCount = 2, closeCount = 0)
  }

  @Test
  fun `visit sessions are returned for a prisoner without any non-associations`() {
    // Given
    val prisonCode = "MDI"
    val prisonerId = "A1234AA"
    val validFromDate = this.getNextAllowedDay()

    sessionTemplateEntityHelper.create(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)

    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerId)

    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prisonCode)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    assertResponseLength(responseSpec, 4)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association without a booking`() {

    // Given
    val prisonCode = "MDI"
    val prisonerId = "A1234AA"
    val associationId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()
    sessionTemplateEntityHelper.create(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationId,
      validFromDate.toString()
    )

    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prison.code)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    assertResponseLength(responseSpec, 4)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a future non-association with a booked visit`() {

    // Given
    val prisonCode = "MDI"
    val prisonerId = "A1234AA"
    val associationId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()
    val sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)

    this.visitEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = validFromDate.atTime(9, 0),
      visitEnd = validFromDate.atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationId,
      validFromDate.plusMonths(6).toString()
    )

    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prison.code)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSessionDtos =
      objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)

    Assertions.assertThat(visitSessionDtos).hasSize(4)
    Assertions.assertThat(visitSessionDtos[0].sessionConflicts).hasSize(1)
    Assertions.assertThat(visitSessionDtos[0].sessionConflicts).contains(SessionConflict.DOUBLE_BOOKED)
    Assertions.assertThat(visitSessionDtos[1].sessionConflicts).isEmpty()
    Assertions.assertThat(visitSessionDtos[2].sessionConflicts).isEmpty()
    Assertions.assertThat(visitSessionDtos[3].sessionConflicts).isEmpty()
  }

  @Test
  fun `visit sessions are returned for a prisoner with a future non-association with a reserved visit`() {

    // Given
    val prisonCode = "MDI"
    val prisonerId = "A1234AA"
    val associationId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()
    val sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)

    this.visitEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = validFromDate.atTime(9, 0),
      visitEnd = validFromDate.atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = RESERVED,
      visitRestriction = OPEN
    )

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationId,
      validFromDate.plusMonths(6).toString()
    )

    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prison.code)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSessionDtos =
      objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)

    Assertions.assertThat(visitSessionDtos).hasSize(4)
    Assertions.assertThat(visitSessionDtos[0].sessionConflicts).hasSize(1)
    Assertions.assertThat(visitSessionDtos[0].sessionConflicts).contains(SessionConflict.DOUBLE_BOOKED)
    Assertions.assertThat(visitSessionDtos[1].sessionConflicts).isEmpty()
    Assertions.assertThat(visitSessionDtos[2].sessionConflicts).isEmpty()
    Assertions.assertThat(visitSessionDtos[3].sessionConflicts).isEmpty()
  }

  @Test
  fun `visit sessions are returned for a prisoner with an expired non-association with a booking`() {
    // Given
    val prisonCode = "MDI"
    val prisonerId = "A1234AA"
    val associationId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()
    val sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)

    this.visitEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = validFromDate.atTime(9, 0),
      visitEnd = validFromDate.atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationId,
      validFromDate.minusMonths(6).toString(),
      validFromDate.minusMonths(1).toString()
    )

    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prison.code)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    assertResponseLength(responseSpec, 4)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking`() {

    // Given
    val prisonCode = "MDI"
    val prisonerId = "A1234AA"
    val associationPrisonerId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()
    val sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)

    this.visitEntityHelper.create(
      prisonerId = associationPrisonerId,
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = validFromDate.atTime(9, 0),
      visitEnd = validFromDate.atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationPrisonerId,
      validFromDate.minusMonths(6).toString(),
      validFromDate.plusMonths(1).toString()
    )

    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prison.code)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    assertResponseLength(responseSpec, 3)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking CANCELLED`() {

    // Given
    val prisonCode = "MDI"
    val prisonerId = "A1234AA"
    val associationPrisonerId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()

    val sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)

    this.visitEntityHelper.create(
      prisonerId = associationPrisonerId,
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = validFromDate.atTime(9, 0),
      visitEnd = validFromDate.atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = CANCELLED,
      visitRestriction = OPEN
    )

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationPrisonerId,
      validFromDate.minusMonths(6).toString(),
      validFromDate.plusMonths(1).toString()
    )

    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prison.code)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    assertResponseLength(responseSpec, 4)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking in the past`() {
    // Given
    val prisonCode = "MDI"
    val prisonerId = "A1234AA"
    val associationPrisonerId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()
    val sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)

    this.visitEntityHelper.create(
      prisonerId = associationPrisonerId,
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = validFromDate.minusMonths(6).atTime(9, 0),
      visitEnd = validFromDate.minusMonths(6).atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationPrisonerId,
      validFromDate.minusMonths(6).toString(),
      validFromDate.plusMonths(1).toString()
    )

    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prison.code)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    assertResponseLength(responseSpec, 4)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking in the future`() {
    // Given
    val prisonCode = "MDI"
    val prisonerId = "A1234AA"
    val associationPrisonerId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()
    val sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)

    this.visitEntityHelper.create(
      prisonerId = associationPrisonerId,
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = validFromDate.plusMonths(6).atTime(9, 0),
      visitEnd = validFromDate.plusMonths(6).atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    prisonOffenderSearchMockServer.stubGetPrisonerIncentiveLevel(prisonerId, prison.code, "")
    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationPrisonerId,
      validFromDate.minusYears(1).toString(),
      validFromDate.plusYears(1).toString()
    )
    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prison.code)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    assertResponseLength(responseSpec, 4)
  }

  @Test
  fun `when get visit session is called with prison id different to prisoners establishment code bad request error is returned`() {
    val prisonCode = "MDI"
    val incorrectPrisonCode = "ABC"
    val prisonerId = "A1234AA"

    // prisoner is in prison with code MDI
    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prisonCode)

    // When
    // get sessions call is being made with the incorrect prison Code
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$incorrectPrisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").isEqualTo("Validation failure: prisoner's establishment and prison code passed do not match")
      .jsonPath("$.developerMessage").isEqualTo("Prisoner with ID - $prisonerId is not in prison - $incorrectPrisonCode")
  }

  private fun callGetSessions(
    policyNoticeDaysMin: Int,
    policyNoticeDaysMax: Int
  ): ResponseSpec {
    return webTestClient.get().uri("/visit-sessions?prisonId=MDI&min=$policyNoticeDaysMin&max=$policyNoticeDaysMax")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()
  }

  private fun callGetSessions(): ResponseSpec {
    return webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()
  }

  private fun getNextAllowedDay(): LocalDate {
    // The two days is based on the default SessionService.policyNoticeDaysMin
    return LocalDate.now().plusDays(2)
  }

  private fun assertSession(
    visitSessionResult: VisitSessionDto,
    testDate: LocalDate,
    expectedSessionTemplate: SessionTemplate
  ) {
    Assertions.assertThat(visitSessionResult.startTimestamp)
      .isEqualTo(testDate.atTime(expectedSessionTemplate.startTime))
    Assertions.assertThat(visitSessionResult.endTimestamp).isEqualTo(testDate.atTime(expectedSessionTemplate.endTime))
    Assertions.assertThat(visitSessionResult.startTimestamp.dayOfWeek).isEqualTo(expectedSessionTemplate.dayOfWeek)
    Assertions.assertThat(visitSessionResult.endTimestamp.dayOfWeek).isEqualTo(expectedSessionTemplate.dayOfWeek)
  }

  private fun assertResponseLength(responseSpec: ResponseSpec, length: Int) {
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(length)
  }

  private fun assertNoResponse(responseSpec: ResponseSpec) {
    assertResponseLength(responseSpec, 0)
  }

  private fun assertBookCounts(responseSpec: ResponseSpec, resultSize: Int? = 1, openCount: Int, closeCount: Int) {
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(resultSize)
    Assertions.assertThat(visitSessionResults[0].openVisitBookedCount).isEqualTo(openCount)
    Assertions.assertThat(visitSessionResults[0].closedVisitBookedCount).isEqualTo(closeCount)
    if (resultSize == 2) {
      Assertions.assertThat(visitSessionResults[1].openVisitBookedCount).isEqualTo(openCount)
      Assertions.assertThat(visitSessionResults[1].closedVisitBookedCount).isEqualTo(closeCount)
    }
  }

  private fun getResults(returnResult: BodyContentSpec): Array<VisitSessionDto> {
    return objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)
  }
}
