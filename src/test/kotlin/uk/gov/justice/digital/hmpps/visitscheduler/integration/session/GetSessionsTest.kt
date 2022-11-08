package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.SessionConflict
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CHANGING
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Get /visit-sessions for prisons without wing based bookings")
class GetSessionsTest(@Autowired private val objectMapper: ObjectMapper) : IntegrationTestBase() {

  @Autowired
  private lateinit var sessionTemplateRepository: SessionTemplateRepository

  @Autowired
  private lateinit var visitRepository: VisitRepository

  @AfterEach
  internal fun deleteAllSessionTemplates() = sessionTemplateEntityHelper.deleteAll()

  @AfterEach
  internal fun deleteAllVisitSessions() = visitEntityHelper.deleteAll()

  private val internalLocation = "HEI-C-1-007"
  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  @Test
  fun `visit sessions are returned for a prison for a single schedule`() {
    // Given

    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate = sessionTemplate(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek
    )

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

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
  fun `visit sessions are returned for a prison for a weekly schedule`() {
    // Given
    val nextAllowedDay = getNextAllowedDay()
    val dayAfterNextAllowedDay = nextAllowedDay.plusDays(1)

    val nextDaySessionTemplate = sessionTemplate(
      validFromDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek
    )

    val dayAfterNextSessionTemplate = sessionTemplate(
      validFromDate = dayAfterNextAllowedDay,
      startTime = LocalTime.parse("10:30"),
      endTime = LocalTime.parse("11:30"),
      dayOfWeek = dayAfterNextAllowedDay.dayOfWeek,
    )

    sessionTemplateRepository.saveAndFlush(nextDaySessionTemplate)
    sessionTemplateRepository.saveAndFlush(dayAfterNextSessionTemplate)

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

    val sessionTemplate = sessionTemplate(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      dayOfWeek = nextAllowedDay.dayOfWeek,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00")
    )

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

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

    sessionTemplateRepository.saveAndFlush(
      sessionTemplate(
        validFromDate = nextAllowedDay,
        validToDate = nextAllowedDay,
        dayOfWeek = previousDay.dayOfWeek,
        startTime = LocalTime.parse("09:00"),
        endTime = LocalTime.parse("10:00")
      )
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

    sessionTemplateRepository.saveAndFlush(sessionTemplate(validFromDate = nextAllowedDay))

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

    sessionTemplateRepository.saveAndFlush(sessionTemplate(validFromDate = LocalDate.now().plusDays(2)))

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

    sessionTemplateRepository.saveAndFlush(sessionTemplate(validFromDate = nextAllowedDay, validToDate = nextAllowedDay))

    // When
    val responseSpec = callGetSessions(policyNoticeDaysMin, policyNoticeDaysMax)

    // Then
    assertNoResponse(responseSpec)
  }

  @Test
  fun `visit sessions that are no longer valid are not returned`() {
    // Given
    val sessionTemplate = sessionTemplate(
      validFromDate = LocalDate.now().minusDays(1),
      validToDate = LocalDate.now().minusDays(1)
    )

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    // When
    val responseSpec = callGetSessions()

    // Then
    assertNoResponse(responseSpec)
  }

  @Test
  fun `sessions that start after the max policy notice days after current date are not returned`() {
    // Given
    val sessionTemplate = sessionTemplate(
      validFromDate = LocalDate.now().plusMonths(6),
      validToDate = LocalDate.now().plusMonths(10)
    )
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

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

    val sessionTemplate = sessionTemplate(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = startTime,
      endTime = endTime.toLocalTime(),
      dayOfWeek = nextAllowedDay.dayOfWeek
    )
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit1 = Visit(
      prisonerId = "AF12345G",
      prisonId = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime,
      visitEnd = endTime,
      visitType = SOCIAL,
      visitStatus = RESERVED,
      visitRestriction = OPEN
    )

    val visit2 = visit1.copy(visitStatus = BOOKED)
    val visit3 = visit1.copy(visitStatus = CANCELLED)

    visitRepository.saveAndFlush(visit1)
    visitRepository.saveAndFlush(visit2)
    visitRepository.saveAndFlush(visit3)

    // When
    val responseSpec = callGetSessions()

    // Then
    assertBookCounts(responseSpec, 2, 0)
  }

  @Test
  fun `visit sessions exclude visits with changing status in visit count`() {

    // Given
    val nextAllowedDay = this.getNextAllowedDay()
    val dateTime = nextAllowedDay.atTime(9, 0)
    val startTime = dateTime.toLocalTime()
    val endTime = dateTime.plusHours(1)

    val sessionTemplate = sessionTemplate(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = startTime,
      endTime = endTime.toLocalTime(),
      dayOfWeek = nextAllowedDay.dayOfWeek
    )
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit1 = Visit(
      prisonerId = "AF12345G",
      prisonId = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime,
      visitEnd = endTime,
      visitType = SOCIAL,
      visitStatus = CHANGING,
      visitRestriction = OPEN
    )

    val visit2 = Visit(
      prisonerId = "AF12345G",
      prisonId = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime.plusWeeks(1),
      visitEnd = endTime,
      visitType = SOCIAL,
      visitStatus = CHANGING,
      visitRestriction = CLOSED
    )

    visitRepository.saveAndFlush(visit1)
    visitRepository.saveAndFlush(visit2)

    // When
    val responseSpec = callGetSessions()

    // Then
    assertBookCounts(responseSpec, 0, 0)
  }

  @Test
  fun `visit sessions include visit count one matching room name`() {

    // Given
    val nextAllowedDay = this.getNextAllowedDay()
    val dateTime = nextAllowedDay.atTime(9, 0)
    val startTime = dateTime.toLocalTime()
    val endTime = dateTime.plusHours(1)

    val sessionTemplate = sessionTemplate(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = startTime,
      endTime = endTime.toLocalTime(),
      dayOfWeek = nextAllowedDay.dayOfWeek
    )

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit1 = Visit(
      prisonerId = "AF12345G",
      prisonId = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime,
      visitEnd = endTime,
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    val visit2 = visit1.copy(visitRoom = sessionTemplate.visitRoom + "Anythingwilldo")

    visitRepository.saveAndFlush(visit1)
    visitRepository.saveAndFlush(visit2)

    // When
    val responseSpec = callGetSessions()

    // Then
    assertBookCounts(responseSpec, 1, 0)
  }

  @Test
  fun `visit sessions include reserved and booked closed visit count`() {

    // Given
    val nextAllowedDay = this.getNextAllowedDay()
    val dateTime = nextAllowedDay.atTime(9, 0)
    val startTime = dateTime.toLocalTime()
    val endTime = dateTime.plusHours(1)

    val sessionTemplate = sessionTemplate(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = startTime,
      endTime = endTime.toLocalTime(),
      dayOfWeek = nextAllowedDay.dayOfWeek
    )

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit1 = Visit(
      prisonerId = "AF12345G",
      prisonId = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime,
      visitEnd = endTime,
      visitType = SOCIAL,
      visitStatus = RESERVED,
      visitRestriction = CLOSED
    )

    val visit2 = visit1.copy(visitStatus = BOOKED, visitRestriction = CLOSED)
    val visit3 = visit1.copy(visitStatus = CANCELLED, visitRestriction = CLOSED)

    visitRepository.saveAndFlush(visit1)
    visitRepository.saveAndFlush(visit2)
    visitRepository.saveAndFlush(visit3)

    // When
    val responseSpec = callGetSessions()

    // Then
    assertBookCounts(responseSpec, 0, 2)
  }

  @Test
  fun `visit sessions visit count includes only visits within session period`() {

    // Given
    val nextAllowedDay = this.getNextAllowedDay()
    val dateTime = nextAllowedDay.atTime(9, 0)
    val startTime = dateTime.toLocalTime()
    val endTime = dateTime.plusHours(1)

    val sessionTemplate = sessionTemplate(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = startTime,
      endTime = endTime.toLocalTime(),
      dayOfWeek = nextAllowedDay.dayOfWeek
    )

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit1 = Visit(
      prisonerId = "AF12345G",
      prisonId = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime.minusHours(1),
      visitEnd = endTime,
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    val visit2 = visit1.copy(visitStart = dateTime, visitEnd = dateTime.plusMinutes(30))
    val visit3 = visit1.copy(visitStart = dateTime.plusMinutes(30), visitEnd = dateTime.plusHours(1))
    val visit4 = visit1.copy(visitStart = dateTime.plusHours(1), visitEnd = dateTime.plusHours(2))

    visitRepository.saveAndFlush(visit1)
    visitRepository.saveAndFlush(visit2)
    visitRepository.saveAndFlush(visit3)
    visitRepository.saveAndFlush(visit4)

    // When
    val responseSpec = callGetSessions()

    // Then
    assertBookCounts(responseSpec, 2, 0)
  }

  @Test
  fun `visit sessions are returned for a prisoner without any non-associations`() {
    // Given
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val validFromDate = this.getNextAllowedDay()

    val sessionTemplate = sessionTemplate(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerId)

    prisonApiMockServer.stubGetPrisonerDetails(
      prisonerId, internalLocation
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    assertResponseLength(responseSpec, 4)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association without a booking`() {

    // Given
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val associationId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()
    val sessionTemplate = sessionTemplate(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationId,
      validFromDate.toString()
    )

    prisonApiMockServer.stubGetPrisonerDetails(
      prisonerId, internalLocation
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    assertResponseLength(responseSpec, 4)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a future non-association with a booked visit`() {

    // Given
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val associationId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()
    val sessionTemplate = sessionTemplate(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visitBooked = Visit(
      prisonerId = prisonerId,
      prisonId = prisonId,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = validFromDate.atTime(9, 0),
      visitEnd = validFromDate.atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    visitRepository.saveAndFlush(visitBooked)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationId,
      validFromDate.plusMonths(6).toString()
    )

    prisonApiMockServer.stubGetPrisonerDetails(
      prisonerId, internalLocation
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
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
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val associationId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()
    val sessionTemplate = sessionTemplate(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visitReserved = Visit(
      prisonerId = prisonerId,
      prisonId = prisonId,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = validFromDate.atTime(9, 0),
      visitEnd = validFromDate.atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = RESERVED,
      visitRestriction = OPEN
    )

    visitRepository.saveAndFlush(visitReserved)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationId,
      validFromDate.plusMonths(6).toString()
    )

    prisonApiMockServer.stubGetPrisonerDetails(
      prisonerId, internalLocation
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
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
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val associationId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()
    val sessionTemplate = sessionTemplate(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit = Visit(
      prisonerId = prisonerId,
      prisonId = prisonId,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = validFromDate.atTime(9, 0),
      visitEnd = validFromDate.atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    visitRepository.saveAndFlush(visit)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationId,
      validFromDate.minusMonths(6).toString(),
      validFromDate.minusMonths(1).toString()
    )

    prisonApiMockServer.stubGetPrisonerDetails(
      prisonerId, internalLocation
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    assertResponseLength(responseSpec, 4)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking`() {

    // Given
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val associationPrisonerId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()
    val sessionTemplate = sessionTemplate(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit = Visit(
      prisonerId = associationPrisonerId,
      prisonId = prisonId,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = validFromDate.atTime(9, 0),
      visitEnd = validFromDate.atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    visitRepository.saveAndFlush(visit)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationPrisonerId,
      validFromDate.minusMonths(6).toString(),
      validFromDate.plusMonths(1).toString()
    )

    prisonApiMockServer.stubGetPrisonerDetails(
      prisonerId, internalLocation
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    assertResponseLength(responseSpec, 3)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking CANCELLED`() {

    // Given
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val associationPrisonerId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()

    val sessionTemplate = sessionTemplate(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit = Visit(
      prisonerId = associationPrisonerId,
      prisonId = prisonId,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = validFromDate.atTime(9, 0),
      visitEnd = validFromDate.atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = CANCELLED,
      visitRestriction = OPEN
    )

    visitRepository.saveAndFlush(visit)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationPrisonerId,
      validFromDate.minusMonths(6).toString(),
      validFromDate.plusMonths(1).toString()
    )

    prisonApiMockServer.stubGetPrisonerDetails(
      prisonerId, internalLocation
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    assertResponseLength(responseSpec, 4)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking in the past`() {
    // Given
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val associationPrisonerId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()
    val sessionTemplate = sessionTemplate(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit = Visit(
      prisonerId = associationPrisonerId,
      prisonId = prisonId,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = validFromDate.minusMonths(6).atTime(9, 0),
      visitEnd = validFromDate.minusMonths(6).atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    visitRepository.saveAndFlush(visit)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationPrisonerId,
      validFromDate.minusMonths(6).toString(),
      validFromDate.plusMonths(1).toString()
    )

    prisonApiMockServer.stubGetPrisonerDetails(
      prisonerId, internalLocation
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    assertResponseLength(responseSpec, 4)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking in the future`() {
    // Given
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val associationPrisonerId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()
    val sessionTemplate = sessionTemplate(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit = Visit(
      prisonerId = associationPrisonerId,
      prisonId = prisonId,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = validFromDate.plusMonths(6).atTime(9, 0),
      visitEnd = validFromDate.plusMonths(6).atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    visitRepository.saveAndFlush(visit)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationPrisonerId,
      validFromDate.minusYears(1).toString(),
      validFromDate.plusYears(1).toString()
    )

    prisonApiMockServer.stubGetPrisonerDetails(
      prisonerId, internalLocation
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    assertResponseLength(responseSpec, 4)
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

  private fun assertBookCounts(responseSpec: ResponseSpec, openCount: Int, closeCount: Int) {
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(1)
    Assertions.assertThat(visitSessionResults[0].openVisitBookedCount).isEqualTo(openCount)
    Assertions.assertThat(visitSessionResults[0].closedVisitBookedCount).isEqualTo(closeCount)
  }

  private fun getResults(returnResult: BodyContentSpec): Array<VisitSessionDto> {
    return objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)
  }
}
