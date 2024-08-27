package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.AvailableVisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.submitApplication
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.service.DateRange
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

@DisplayName("Get /visit-sessions/available")
class GetAvailableSessionsTest : IntegrationTestBase() {

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  private val prisonerId = "A0000001"

  private val prisonCode = "STC"

  @BeforeEach
  internal fun setUpTests() {
    prison = prisonEntityHelper.create(prisonCode = prisonCode)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
  }

  @Test
  fun `all available sessions are returned by OPEN restriction`() {
    // Given
    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 10,
      closedCapacity = 0,
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate, SessionRestriction.OPEN)
  }

  @Test
  fun `all available sessions are returned by CLOSED restriction`() {
    // Given
    val nextAllowedDay = getNextAllowedDay()

    sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 10,
      closedCapacity = 0,
    )

    val closedSessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("12:00"),
      endTime = LocalTime.parse("13:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 5,
      closedCapacity = 10,
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.CLOSED,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], nextAllowedDay, closedSessionTemplate, SessionRestriction.CLOSED)
  }

  @Test
  fun `when exclude current application reference is given for an existing open application, then capacity count is not affected`() {
    // Given
    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 1,
      closedCapacity = 0,
    )

    val reservedApplication = this.applicationEntityHelper.create(
      prisonCode = prisonCode,
      prisonerId = prisonerId,
      slotDate = nextAllowedDay,
      sessionTemplate = sessionTemplate1,
      completed = false,
      reservedSlot = true,
      visitRestriction = VisitRestriction.OPEN,
    )

    // When
    val responseSpec = callGetAvailableSessions(prisonCode, prisonerId, SessionRestriction.OPEN, 2, 28, excludedApplicationReference = reservedApplication.reference)

    // Then
    responseSpec.expectStatus().isOk
    val returnResult = responseSpec.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate1, SessionRestriction.OPEN)
  }

  @Test
  fun `when exclude current application reference is given for an existing open reservation, then double reservations conflict does not occur`() {
    // Given
    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 2,
      closedCapacity = 0,
    )

    val reservedApplication = this.applicationEntityHelper.create(
      prisonCode = prisonCode,
      prisonerId = prisonerId,
      slotDate = nextAllowedDay,
      sessionTemplate = sessionTemplate1,
      completed = false,
      reservedSlot = true,
      visitRestriction = VisitRestriction.OPEN,
    )

    // When
    val responseSpec = callGetAvailableSessions(prisonCode, prisonerId, SessionRestriction.OPEN, 1, 7, excludedApplicationReference = reservedApplication.reference)

    // Then
    responseSpec.expectStatus().isOk
    val returnResult = responseSpec.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate1, SessionRestriction.OPEN)
  }

  @Test
  fun `when existing open application for slot, then double reservations conflict occurs`() {
    // Given
    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate1 = sessionTemplateEntityHelper.create(

      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 2,
      closedCapacity = 0,
    )

    this.applicationEntityHelper.create(
      prisonCode = prisonCode,
      prisonerId = prisonerId,
      slotDate = nextAllowedDay,
      sessionTemplate = sessionTemplate1,
      completed = false,
      reservedSlot = true,
      visitRestriction = VisitRestriction.OPEN,
    )

    // When
    val responseSpec = callGetAvailableSessions(prisonCode, prisonerId, SessionRestriction.OPEN, 1, 7)

    // Then
    responseSpec.expectStatus().isOk
    val returnResult = responseSpec.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(0)
  }

  @Test
  fun `when exclude current application reference is given for an existing closed application, then capacity count is not affected `() {
    // Given
    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 0,
      closedCapacity = 1,
    )

    val reservedApplication = this.applicationEntityHelper.create(
      prisonCode = prisonCode,
      prisonerId = prisonerId,
      slotDate = nextAllowedDay,
      sessionTemplate = sessionTemplate1,
      completed = false,
      reservedSlot = true,
      visitRestriction = VisitRestriction.CLOSED,
    )

    // When
    val responseSpec = callGetAvailableSessions(prisonCode, prisonerId, SessionRestriction.CLOSED, 2, 28, excludedApplicationReference = reservedApplication.reference)

    // Then
    responseSpec.expectStatus().isOk
    val returnResult = responseSpec.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate1, SessionRestriction.CLOSED)
  }

  @Test
  fun `when exclude current application reference is given for an existing closed application, then double reservations conflict does not occur`() {
    // Given
    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate1 = sessionTemplateEntityHelper.create(

      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 0,
      closedCapacity = 2,
    )

    val reservedApplication = this.applicationEntityHelper.create(
      prisonCode = prisonCode,
      prisonerId = prisonerId,
      slotDate = nextAllowedDay,
      sessionTemplate = sessionTemplate1,
      completed = false,
      reservedSlot = true,
      visitRestriction = VisitRestriction.CLOSED,
    )

    // When
    val responseSpec = callGetAvailableSessions(prisonCode, prisonerId, SessionRestriction.CLOSED, 1, 7, excludedApplicationReference = reservedApplication.reference)

    // Then
    responseSpec.expectStatus().isOk
    val returnResult = responseSpec.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate1, SessionRestriction.CLOSED)
  }

  @Test
  fun `when existing closed application for slot, then double reservations conflict occurs`() {
    // Given
    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate1 = sessionTemplateEntityHelper.create(

      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 0,
      closedCapacity = 2,
    )

    this.applicationEntityHelper.create(
      prisonCode = prisonCode,
      prisonerId = prisonerId,
      slotDate = nextAllowedDay,
      sessionTemplate = sessionTemplate1,
      completed = false,
      reservedSlot = true,
      visitRestriction = VisitRestriction.CLOSED,
    )

    // When
    val responseSpec = callGetAvailableSessions(prisonCode, prisonerId, SessionRestriction.CLOSED, 1, 7)

    // Then
    responseSpec.expectStatus().isOk
    val returnResult = responseSpec.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(0)
  }

  @Test
  fun `fully booked sessions are not returned when visits are fully booked`() {
    // Given
    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 0,
      closedCapacity = 1,
    )

    val sessionTemplate2 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("12:00"),
      endTime = LocalTime.parse("13:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 0,
      closedCapacity = 1,
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      sessionTemplate = sessionTemplate1,
      visitRestriction = VisitRestriction.CLOSED,
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.CLOSED,
      2,
      28,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate2, SessionRestriction.CLOSED)
  }

  @Test
  fun `when reserved and booked sessions equals max capacity these sessions are not returned`() {
    // Given
    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 0,
      closedCapacity = 2,
    )

    val sessionTemplate2 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("12:00"),
      endTime = LocalTime.parse("13:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 0,
      closedCapacity = 1,
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      sessionTemplate = sessionTemplate1,
      visitRestriction = VisitRestriction.CLOSED,
    )

    this.applicationEntityHelper.create(
      prisonerId = "ABC2345D",
      sessionTemplate = sessionTemplate1,
      visitRestriction = VisitRestriction.CLOSED,
      slotDate = nextAllowedDay,
      completed = false,
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.CLOSED,
      2,
      28,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate2, SessionRestriction.CLOSED)
  }

  @Test
  fun `when reserved (excluding username) and booked sessions is less than max capacity these sessions are returned`() {
    // Given
    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 0,
      closedCapacity = 2,
    )

    val sessionTemplate2 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("12:00"),
      endTime = LocalTime.parse("13:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 0,
      closedCapacity = 1,
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      sessionTemplate = sessionTemplate1,
      visitRestriction = VisitRestriction.CLOSED,
    )

    this.applicationEntityHelper.create(
      prisonerId = "ABC2345D",
      sessionTemplate = sessionTemplate1,
      visitRestriction = VisitRestriction.CLOSED,
      slotDate = nextAllowedDay,
      completed = false,
      createdBy = "username",
    )

    this.applicationEntityHelper.create(
      prisonerId = "ABC2345D",
      sessionTemplate = sessionTemplate1,
      visitRestriction = VisitRestriction.CLOSED,
      slotDate = nextAllowedDay,
      completed = false,
      createdBy = "username",
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.CLOSED,
      2,
      28,
      username = "username",
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(2)
  }

  @Test
  fun `when reserved and booked sessions is over max capacity these sessions are not returned`() {
    // Given
    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 0,
      closedCapacity = 1,
    )

    val sessionTemplate2 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("12:00"),
      endTime = LocalTime.parse("13:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 0,
      closedCapacity = 1,
    )

    // 1 booked visit
    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      sessionTemplate = sessionTemplate1,
      visitRestriction = VisitRestriction.CLOSED,
    )

    // 1 application in progress
    this.applicationEntityHelper.create(
      prisonerId = "ABC2345D",
      sessionTemplate = sessionTemplate1,
      visitRestriction = VisitRestriction.CLOSED,
      slotDate = nextAllowedDay,
      completed = false,
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.CLOSED,
      2,
      28,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate2, SessionRestriction.CLOSED)
  }

  @Test
  fun `when prisoner already has as visit booked for a session with same restriction these sessions are not returned`() {
    // Given
    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 10,
      closedCapacity = 5,
    )

    val sessionTemplate2 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("12:00"),
      endTime = LocalTime.parse("13:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 10,
      closedCapacity = 3,
    )

    // 1 booked visit for prisoner in same session
    this.visitEntityHelper.create(
      prisonerId = prisonerId,
      sessionTemplate = sessionTemplate1,
      slotDate = nextAllowedDay,
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
      2,
      28,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate2, SessionRestriction.OPEN)
  }

  @Test
  fun `when prisoner already has as visit booked for a session with different restriction these sessions are still not returned`() {
    // Given
    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 10,
      closedCapacity = 10,
    )

    val sessionTemplate2 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("12:00"),
      endTime = LocalTime.parse("13:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 10,
      closedCapacity = 3,
    )

    // 1 booked visit for prisoner in same session but for CLOSED restriction
    this.visitEntityHelper.create(
      prisonerId = prisonerId,
      sessionTemplate = sessionTemplate1,
      slotDate = nextAllowedDay,
      visitRestriction = VisitRestriction.CLOSED,
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate2, SessionRestriction.OPEN)
  }

  @Test
  fun `visit sessions are not returned for a day when prisoner's non-association has a booking on the same day`() {
    // Given
    val nonAssociationId = "B1234BB"
    val nextAllowedDay = getNextAllowedDay()

    // session 1 on same day when non association prisoner is booked
    val sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 10,
      closedCapacity = 10,
    )

    // session 2 on same day but no bookings here
    sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("12:00"),
      endTime = LocalTime.parse("13:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 10,
      closedCapacity = 3,
    )

    val sessionTemplate3 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay.plusDays(1),
      validToDate = nextAllowedDay.plusDays(1),
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("11:32"),
      dayOfWeek = nextAllowedDay.plusDays(1).dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 10,
      closedCapacity = 3,
    )

    // 1 booked visit for non association prisoner
    this.visitEntityHelper.create(
      prisonerId = nonAssociationId,
      sessionTemplate = sessionTemplate1,
      slotDate = nextAllowedDay,
      visitRestriction = VisitRestriction.CLOSED,
    )

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerId,
      nonAssociationId,
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)

    // only session template 3 needs to be returned as it's on the next day
    assertSession(visitSessionResults[0], nextAllowedDay.plusDays(1), sessionTemplate3, SessionRestriction.OPEN)
  }

  @Test
  fun `visit sessions are not returned for a day when prisoner's non-association has an ongoing application on the same day`() {
    // Given
    val nonAssociationId = "B1234BB"
    val nextAllowedDay = getNextAllowedDay()

    // session 1 on same day when non association prisoner is booked
    val sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 10,
      closedCapacity = 10,
    )

    // session 2 on same day but no bookings here
    sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("12:00"),
      endTime = LocalTime.parse("13:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 10,
      closedCapacity = 3,
    )

    val sessionTemplate3 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay.plusDays(1),
      validToDate = nextAllowedDay.plusDays(1),
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("11:32"),
      dayOfWeek = nextAllowedDay.plusDays(1).dayOfWeek,
      prisonCode = prisonCode,
      openCapacity = 10,
      closedCapacity = 3,
    )

    // 1 booked visit for non association prisoner
    this.applicationEntityHelper.create(
      prisonerId = nonAssociationId,
      sessionTemplate = sessionTemplate1,
      slotDate = nextAllowedDay,
      visitRestriction = VisitRestriction.CLOSED,
      completed = false,
    )

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerId,
      nonAssociationId,
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)

    // only session template 3 needs to be returned as it's on the next day even though the other visit has not been booked
    assertSession(visitSessionResults[0], nextAllowedDay.plusDays(1), sessionTemplate3, SessionRestriction.OPEN)
  }

  @Test
  fun `all available visit sessions are returned using prison booking min and max days`() {
    // Given
    val prisonCode = "TST"
    val policyNoticeDaysMin = 2
    val policyNoticeDaysMax = 7
    prisonEntityHelper.create(prisonCode = prisonCode, policyNoticeDaysMin = policyNoticeDaysMin, policyNoticeDaysMax = policyNoticeDaysMax)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)

    val now = LocalDate.now()

    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = now,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = now.dayOfWeek,
      prisonCode = prisonCode,
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode = prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
      policyNoticeDaysMin = policyNoticeDaysMin,
      policyNoticeDaysMax = policyNoticeDaysMax,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], now.plusWeeks(1), sessionTemplate, SessionRestriction.OPEN)
  }

  @Test
  fun `all available visit sessions are returned for a prison without excluded dates`() {
    // Given

    val nextAllowedDay = getNextAllowedDay()
    val nextWeek = nextAllowedDay.plusDays(7)
    val prisonCode = "AWE"
    prisonEntityHelper.create(prisonCode = prisonCode, excludeDates = listOf(nextWeek))
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)

    val sessionTemplate = sessionTemplateEntityHelper.create(
      prisonCode = "AWE",
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay.plusDays(10),
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode = "AWE",
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate, SessionRestriction.OPEN)
  }

  @Test
  fun `only active and available visit sessions are returned for a prison`() {
    // Given

    val nextAllowedDay = getNextAllowedDay()

    // active session 1
    val session1 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
    )

    // active session 2
    val session2 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("11:00"),
      endTime = LocalTime.parse("12:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
    )

    // active session 3
    val session3 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("12:00"),
      endTime = LocalTime.parse("13:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
    )

    // inactive session
    sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("13:00"),
      endTime = LocalTime.parse("14:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      isActive = false,
      prisonCode = prisonCode,
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(3)
    assertSession(visitSessionResults[0], nextAllowedDay, session1, SessionRestriction.OPEN)
    assertSession(visitSessionResults[1], nextAllowedDay, session2, SessionRestriction.OPEN)
    assertSession(visitSessionResults[2], nextAllowedDay, session3, SessionRestriction.OPEN)
  }

  @Test
  fun `available visit sessions are returned for enhanced prisoner when prison has enhanced schedule`() {
    // Given
    val prisonerId = "A1234AA"
    val enhancedIncentiveLevelGroup = "ENH Incentive Level Group"
    val incentiveLevelList = listOf(
      IncentiveLevel.ENHANCED,
    )
    val incentiveLevelGroup = sessionPrisonerIncentiveLevelHelper.create(enhancedIncentiveLevelGroup, prisonCode, incentiveLevelList)

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, IncentiveLevel.ENHANCED)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")

    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate = sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      permittedIncentiveLevels = mutableListOf(incentiveLevelGroup),
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate, SessionRestriction.OPEN)
  }

  @Test
  fun `non enhanced and available visit sessions are returned for a prisoner with any incentive level`() {
    // Given
    val prisonerId = "A1234AA"

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, IncentiveLevel.STANDARD)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")

    val nextAllowedDay = getNextAllowedDay()

    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      permittedIncentiveLevels = mutableListOf(),
    )

    // When

    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
  }

  @Test
  fun `non enhanced and available visit sessions are returned for a prisoner with null incentive level`() {
    // Given
    val prisonerId = "A1234AA"

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, null)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")

    val nextAllowedDay = getNextAllowedDay()

    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      permittedIncentiveLevels = mutableListOf(),
    )

    // When

    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
  }

  @Test
  fun `no visit sessions are returned for a standard incentive level prisoner for a schedule that is enhanced`() {
    // Given
    val prisonerId = "A1234AA"
    val enhancedIncentiveLevelGroup = "ENH Incentive Level Group"

    val incentiveLevelList = listOf(
      IncentiveLevel.ENHANCED,
    )
    val incentiveLevelGroup = sessionPrisonerIncentiveLevelHelper.create(enhancedIncentiveLevelGroup, prisonCode, incentiveLevelList)

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, IncentiveLevel.STANDARD)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)

    val nextAllowedDay = getNextAllowedDay()

    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      permittedIncentiveLevels = mutableListOf(incentiveLevelGroup),
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(0)
  }

  @Test
  fun `no visit sessions are returned for a null incentive level prisoner for a schedule that is enhanced`() {
    // Given
    val prisonerId = "A1234AA"
    val enhancedIncentiveLevelGroup = "ENH Incentive Level Group"

    val incentiveLevelList = listOf(
      IncentiveLevel.ENHANCED,
    )
    val incentiveLevelGroup = sessionPrisonerIncentiveLevelHelper.create(enhancedIncentiveLevelGroup, prisonCode, incentiveLevelList)

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, null)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)

    val nextAllowedDay = getNextAllowedDay()

    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      permittedIncentiveLevels = mutableListOf(incentiveLevelGroup),
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(0)
  }

  @Test
  fun `when a session template is allowed for a category group then session template is returned for prisoner category in same group`() {
    // Given
    val prisonerId = "A1234AA"
    val categoryA = "Category A"
    val categoryAList = listOf(
      PrisonerCategoryType.A_HIGH,
      PrisonerCategoryType.A_PROVISIONAL,
      PrisonerCategoryType.A_EXCEPTIONAL,
      PrisonerCategoryType.A_STANDARD,
    )

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, IncentiveLevel.STANDARD, category = PrisonerCategoryType.A_EXCEPTIONAL.code)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")

    val categoryInc1 = sessionPrisonerCategoryHelper.create(name = categoryA, prisonerCategories = categoryAList)

    val nextAllowedDay = getNextAllowedDay()

    // this session is only available to Category A prisoners
    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      dayOfWeek = nextAllowedDay.dayOfWeek,
      permittedIncentiveLevels = mutableListOf(),
      permittedCategories = mutableListOf(categoryInc1),
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
  }

  @Test
  fun `when a session template is allowed for a category group then session template is not returned for prisoner category not in same category group`() {
    // Given
    val prisonerId = "A1234AA"
    val categoryA = "Category A"
    val categoryAList = listOf(
      PrisonerCategoryType.A_HIGH,
      PrisonerCategoryType.A_PROVISIONAL,
      PrisonerCategoryType.A_EXCEPTIONAL,
      PrisonerCategoryType.A_STANDARD,
    )

    // prisoner is in category B while the session only allows category As
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, IncentiveLevel.STANDARD, category = PrisonerCategoryType.B.code)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)

    val categoryInc1 = sessionPrisonerCategoryHelper.create(name = categoryA, prisonerCategories = categoryAList)

    val nextAllowedDay = getNextAllowedDay()

    // this session is only available for Category A prisoners
    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      dayOfWeek = nextAllowedDay.dayOfWeek,
      permittedIncentiveLevels = mutableListOf(),
      permittedCategories = mutableListOf(categoryInc1),
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(0)
  }

  @Test
  fun `when a session template does not have a category group then session template is returned for all prisoners`() {
    // Given
    val prisonerId = "A1234AA"
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, IncentiveLevel.STANDARD, category = PrisonerCategoryType.A_EXCEPTIONAL.code)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")

    val nextAllowedDay = getNextAllowedDay()

    // this session is available to all prisoners
    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      dayOfWeek = nextAllowedDay.dayOfWeek,
      permittedIncentiveLevels = mutableListOf(),
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )
    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
  }

  @Test
  fun `when a session template is allowed for multiple category groups then session template is returned for prisoner category in any group`() {
    // Given
    val prisonerId = "A1234AA"
    val categoryAHighs = "Category A Highs"
    val categoryANonHighs = "Category A Non Highs"

    val categoryAListHigh = listOf(
      PrisonerCategoryType.A_PROVISIONAL,
      PrisonerCategoryType.A_EXCEPTIONAL,
      PrisonerCategoryType.A_STANDARD,
    )

    val categoryAListNonHigh = listOf(
      PrisonerCategoryType.A_HIGH,
    )

    // prisoner is in category A standard - so should be included
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, IncentiveLevel.STANDARD, category = PrisonerCategoryType.A_STANDARD.code)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")

    val categoryIncAHighs = sessionPrisonerCategoryHelper.create(name = categoryAHighs, prisonerCategories = categoryAListHigh)
    val categoryIncNonAHighs = sessionPrisonerCategoryHelper.create(name = categoryANonHighs, prisonerCategories = categoryAListNonHigh)

    val nextAllowedDay = getNextAllowedDay()

    // this session is available to Category A prisoners
    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      dayOfWeek = nextAllowedDay.dayOfWeek,
      permittedIncentiveLevels = mutableListOf(),
      permittedCategories = mutableListOf(categoryIncAHighs, categoryIncNonAHighs),
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
  }

  @Test
  fun `when a session template is allowed for multiple category groups then session template is not returned if prisoner category is not in any group`() {
    // Given
    val prisonerId = "A1234AA"
    val categoryAHighs = "Category A Highs"
    val categoryANonHighs = "Category A Non Highs"

    val categoryAListHigh = listOf(
      PrisonerCategoryType.A_PROVISIONAL,
      PrisonerCategoryType.A_EXCEPTIONAL,
      PrisonerCategoryType.A_STANDARD,
    )

    val categoryAListNonHigh = listOf(
      PrisonerCategoryType.A_HIGH,
    )

    // prisoner is in category B
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, IncentiveLevel.STANDARD, category = PrisonerCategoryType.B.code)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)

    val categoryIncAHighs = sessionPrisonerCategoryHelper.create(name = categoryAHighs, prisonerCategories = categoryAListHigh)
    val categoryIncNonAHighs = sessionPrisonerCategoryHelper.create(name = categoryANonHighs, prisonerCategories = categoryAListNonHigh)

    val nextAllowedDay = getNextAllowedDay()

    // this session is available to Category A prisoners - 2 groups
    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      dayOfWeek = nextAllowedDay.dayOfWeek,
      permittedIncentiveLevels = mutableListOf(),
      permittedCategories = mutableListOf(categoryIncAHighs, categoryIncNonAHighs),
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(0)
  }

  @Test
  fun `bi weekly schedule - test for sunday change boundary`() {
    val today = LocalDate.now()

    // Given
    val startFromWeek1 = today.with(TemporalAdjusters.next(MONDAY)).minusWeeks(1)
    sessionTemplateEntityHelper.create(
      validFromDate = startFromWeek1,
      visitRoom = "Alternate 1",
      dayOfWeek = SUNDAY,
      weeklyFrequency = 2,
      prisonCode = prisonCode,
    )

    val startFromWeek2 = LocalDate.now().with(TemporalAdjusters.next(MONDAY)).minusWeeks(2)

    sessionTemplateEntityHelper.create(
      validFromDate = startFromWeek2,
      visitRoom = "Alternate 2",
      dayOfWeek = SUNDAY,
      weeklyFrequency = 2,
      prisonCode = prisonCode,
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()

    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isGreaterThan(2)
  }

  @Test
  fun `available visit sessions are returned for a prison for a weekly schedule`() {
    // Given
    val nextAllowedDay = getNextAllowedDay()
    val dayAfterNextAllowedDay = nextAllowedDay.plusDays(1)

    val nextDaySessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
    )

    val dayAfterNextSessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = dayAfterNextAllowedDay,
      startTime = LocalTime.parse("10:30"),
      endTime = LocalTime.parse("11:30"),
      dayOfWeek = dayAfterNextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
      0,
      28,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()

    val visitSessionResults = getResults(returnResult)

    assertThat(visitSessionResults.size).isEqualTo(8)
    assertSession(visitSessionResults[0], nextAllowedDay, nextDaySessionTemplate, SessionRestriction.OPEN)
    assertSession(visitSessionResults[1], dayAfterNextAllowedDay, dayAfterNextSessionTemplate, SessionRestriction.OPEN)
    assertSession(visitSessionResults[2], nextAllowedDay.plusWeeks(1), nextDaySessionTemplate, SessionRestriction.OPEN)
    assertSession(visitSessionResults[3], dayAfterNextAllowedDay.plusWeeks(1), dayAfterNextSessionTemplate, SessionRestriction.OPEN)
    assertSession(visitSessionResults[4], nextAllowedDay.plusWeeks(2), nextDaySessionTemplate, SessionRestriction.OPEN)
    assertSession(visitSessionResults[5], dayAfterNextAllowedDay.plusWeeks(2), dayAfterNextSessionTemplate, SessionRestriction.OPEN)
    assertSession(visitSessionResults[6], nextAllowedDay.plusWeeks(3), nextDaySessionTemplate, SessionRestriction.OPEN)
    assertSession(visitSessionResults[7], dayAfterNextAllowedDay.plusWeeks(3), dayAfterNextSessionTemplate, SessionRestriction.OPEN)
  }

  @Test
  fun `available visit sessions are returned for a prison when day of week and schedule starts and ends on the same Day`() {
    // Given
    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      dayOfWeek = nextAllowedDay.dayOfWeek,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      prisonCode = prisonCode,
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()

    val visitSessionResults = getResults(returnResult)

    assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate, SessionRestriction.OPEN)
  }

  @Test
  fun `available visit sessions are not returned for a prison when schedule starts and ends on the previous day`() {
    // Given

    val nextAllowedDay = getNextAllowedDay()
    val previousDay = nextAllowedDay.minusDays(1)

    sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      dayOfWeek = previousDay.dayOfWeek,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    assertNoResponse(responseSpec)
  }

  @Test
  fun `available visit sessions are not returned when policy notice min days is greater than max without valid to date`() {
    // Given
    val policyNoticeDaysMin = 14
    val policyNoticeDaysMax = 1

    prison = prisonEntityHelper.create(prisonCode = "TST", policyNoticeDaysMin = policyNoticeDaysMin, policyNoticeDaysMax = policyNoticeDaysMax)
    val prisonCode = prison.code
    val nextAllowedDay = getNextAllowedDay()

    sessionTemplateEntityHelper.create(validFromDate = nextAllowedDay)

    // When
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
      policyNoticeDaysMin,
      policyNoticeDaysMax,
    )

    // Then
    assertNoResponse(responseSpec)
  }

  @Test
  fun `available visit sessions are not returned when start date is after policy notice min and max days`() {
    // Given
    val policyNoticeDaysMin = 0
    val policyNoticeDaysMax = 1
    prison = prisonEntityHelper.create(prisonCode = "TST", policyNoticeDaysMin = policyNoticeDaysMin, policyNoticeDaysMax = policyNoticeDaysMax)
    val prisonCode = prison.code

    sessionTemplateEntityHelper.create(validFromDate = LocalDate.now().plusDays(2))

    // When
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
      policyNoticeDaysMin,
      policyNoticeDaysMax,
    )

    // Then
    assertNoResponse(responseSpec)
  }

  @Test
  fun `available visit sessions are not returned when policy notice min days is greater than max with valid to date`() {
    // Given
    val policyNoticeDaysMin = 14
    val policyNoticeDaysMax = 1

    val nextAllowedDay = getNextAllowedDay()

    sessionTemplateEntityHelper.create(validFromDate = nextAllowedDay, validToDate = nextAllowedDay)

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
      policyNoticeDaysMin,
      policyNoticeDaysMax,
    )

    // Then
    assertNoResponse(responseSpec)
  }

  @Test
  fun `available visit sessions that are no longer valid are not returned`() {
    // Given
    sessionTemplateEntityHelper.create(
      validFromDate = LocalDate.now().minusDays(1),
      validToDate = LocalDate.now().minusDays(1),
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    assertNoResponse(responseSpec)
  }

  @Test
  fun `sessions that start after the max policy notice days after current date are not returned`() {
    // Given
    sessionTemplateEntityHelper.create(
      validFromDate = LocalDate.now().plusMonths(6),
      validToDate = LocalDate.now().plusMonths(10),
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    assertNoResponse(responseSpec)
  }

  @Test
  fun `visit sessions include reserved applications`() {
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
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
    )

    this.applicationEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = prisonCode,
      slotDate = dateTime.toLocalDate(),
      visitStart = dateTime.toLocalTime(),
      visitEnd = endTime.toLocalTime(),
      visitType = SOCIAL,
      visitRestriction = VisitRestriction.OPEN,
      sessionTemplate = sessionTemplate,
      completed = false,
      reservedSlot = true,
    )

    // This should not be included since this application is not reserved
    this.applicationEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = prisonCode,
      slotDate = dateTime.toLocalDate(),
      visitStart = dateTime.toLocalTime(),
      visitEnd = endTime.toLocalTime(),
      visitType = SOCIAL,
      visitRestriction = VisitRestriction.OPEN,
      sessionTemplate = sessionTemplate,
      completed = false,
      reservedSlot = false,
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      slotDate = dateTime.toLocalDate(),
      visitStart = dateTime.toLocalTime(),
      visitEnd = endTime.toLocalTime(),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = VisitRestriction.OPEN,
      sessionTemplate = sessionTemplate,
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      slotDate = dateTime.toLocalDate(),
      visitStart = dateTime.toLocalTime(),
      visitEnd = endTime.toLocalTime(),
      visitType = SOCIAL,
      visitStatus = CANCELLED,
      visitRestriction = VisitRestriction.OPEN,
      sessionTemplate = sessionTemplate,
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
  }

  @Test
  fun `visit sessions exclude applications that are changing in visit count`() {
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
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
    )

    this.applicationEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = prisonCode,
      slotDate = dateTime.toLocalDate(),
      visitStart = dateTime.toLocalTime(),
      visitEnd = endTime.toLocalTime(),
      visitType = SOCIAL,
      visitRestriction = VisitRestriction.OPEN,
      sessionTemplate = sessionTemplate,
      reservedSlot = false,
    )

    this.applicationEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = prisonCode,
      slotDate = dateTime.toLocalDate(),
      visitStart = dateTime.toLocalTime(),
      visitEnd = endTime.toLocalTime(),
      visitType = SOCIAL,
      visitRestriction = VisitRestriction.CLOSED,
      sessionTemplate = sessionTemplate,
      reservedSlot = false,
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
  }

  @Test
  fun `visit sessions include closed reserved application and closed booked visits count`() {
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
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
    )

    // not included ot not reserved
    this.applicationEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = prisonCode,
      slotDate = dateTime.toLocalDate(),
      visitStart = dateTime.toLocalTime(),
      visitEnd = endTime.toLocalTime(),
      visitType = SOCIAL,
      visitRestriction = VisitRestriction.CLOSED,
      sessionTemplate = sessionTemplate,
      completed = true,
      reservedSlot = false,
    )

    // not included it is complete therefore a visit exists
    this.applicationEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = prisonCode,
      slotDate = dateTime.toLocalDate(),
      visitStart = dateTime.toLocalTime(),
      visitEnd = endTime.toLocalTime(),
      visitType = SOCIAL,
      visitRestriction = VisitRestriction.CLOSED,
      sessionTemplate = sessionTemplate,
      completed = true,
      reservedSlot = true,
    )

    this.applicationEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = prisonCode,
      slotDate = dateTime.toLocalDate(),
      visitStart = dateTime.toLocalTime(),
      visitEnd = endTime.toLocalTime(),
      visitType = SOCIAL,
      visitRestriction = VisitRestriction.CLOSED,
      sessionTemplate = sessionTemplate,
      completed = false,
      reservedSlot = true,
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      slotDate = dateTime.toLocalDate(),
      visitStart = dateTime.toLocalTime(),
      visitEnd = endTime.toLocalTime(),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = VisitRestriction.CLOSED,
      sessionTemplate = sessionTemplate,
    )

    // not included as it's a cancelled visit
    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      slotDate = dateTime.toLocalDate(),
      visitStart = dateTime.toLocalTime(),
      visitEnd = endTime.toLocalTime(),
      visitType = SOCIAL,
      visitStatus = CANCELLED,
      visitRestriction = VisitRestriction.CLOSED,
      sessionTemplate = sessionTemplate,
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
  }

  @Test
  fun `visit sessions visit count includes only visits within session template period`() {
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
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      slotDate = dateTime.toLocalDate().minusDays(1),
      visitStart = dateTime.toLocalTime().minusHours(1),
      visitEnd = endTime.toLocalTime(),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = VisitRestriction.OPEN,
      sessionTemplate = sessionTemplate,
    )

    this.applicationEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = prisonCode,
      slotDate = dateTime.toLocalDate(),
      visitStart = dateTime.toLocalTime(),
      visitEnd = endTime.toLocalTime(),
      visitType = SOCIAL,
      visitRestriction = VisitRestriction.CLOSED,
      sessionTemplate = sessionTemplate,
      completed = false,
      reservedSlot = true,
    )

    this.applicationEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = prisonCode,
      slotDate = dateTime.toLocalDate(),
      visitStart = dateTime.toLocalTime(),
      visitEnd = endTime.toLocalTime(),
      visitType = SOCIAL,
      visitRestriction = VisitRestriction.CLOSED,
      sessionTemplate = sessionTemplate,
      completed = true,
      reservedSlot = true,
    )

    this.applicationEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = prisonCode,
      slotDate = dateTime.toLocalDate(),
      visitStart = dateTime.toLocalTime(),
      visitEnd = endTime.toLocalTime(),
      visitType = SOCIAL,
      visitRestriction = VisitRestriction.CLOSED,
      sessionTemplate = sessionTemplate,
      completed = true,
      reservedSlot = false,
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      slotDate = dateTime.toLocalDate(),
      visitStart = dateTime.toLocalTime().minusHours(1),
      visitEnd = endTime.toLocalTime(),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = VisitRestriction.OPEN,
      sessionTemplate = sessionTemplate,
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      slotDate = dateTime.toLocalDate(),
      visitStart = dateTime.toLocalTime(),
      visitEnd = endTime.toLocalTime().minusHours(1),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = VisitRestriction.OPEN,
      sessionTemplate = sessionTemplate,
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      slotDate = dateTime.toLocalDate(),
      visitStart = dateTime.toLocalTime().plusMinutes(30),
      visitEnd = endTime.toLocalTime().plusHours(1),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = VisitRestriction.OPEN,
      sessionTemplate = sessionTemplate,
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      slotDate = dateTime.toLocalDate(),
      visitStart = dateTime.toLocalTime().plusMinutes(1),
      visitEnd = endTime.toLocalTime().plusHours(2),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = VisitRestriction.OPEN,
      sessionTemplate = sessionTemplate,
    )

    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      slotDate = dateTime.toLocalDate(),
      visitStart = dateTime.toLocalTime(),
      visitEnd = endTime.toLocalTime(),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = VisitRestriction.OPEN,
      sessionTemplate = sessionTemplate,
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
  }

  @Test
  fun `visit sessions are returned for a prisoner without any non-associations`() {
    // Given
    val prisonerId = "A1234AA"
    val validFromDate = this.getNextAllowedDay()

    sessionTemplateEntityHelper.create(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek, prisonCode = prisonCode)

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )
    // Then
    assertResponseLength(responseSpec, 4)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association without a booking`() {
    // Given
    val prisonerId = "A1234AA"
    val associationId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()
    sessionTemplateEntityHelper.create(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek, prisonCode = prisonCode)

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerId,
      associationId,
    )

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    assertResponseLength(responseSpec, 4)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a future non-association with a booked visit`() {
    // Given
    val prisonerId = "A1234AA"
    val associationId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()
    val sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek, prisonCode = prisonCode)

    this.visitEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      slotDate = validFromDate,
      visitStart = LocalTime.of(9, 0),
      visitEnd = LocalTime.of(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = VisitRestriction.OPEN,
      sessionTemplate = sessionTemplate,
    )

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerId,
      associationId,
    )

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSessionDtos = getResults(returnResult)

    assertThat(visitSessionDtos).hasSize(3)
    assertSession(visitSessionDtos[0], validFromDate.plusWeeks(1), sessionTemplate, SessionRestriction.OPEN)
    assertSession(visitSessionDtos[1], validFromDate.plusWeeks(2), sessionTemplate, SessionRestriction.OPEN)
    assertSession(visitSessionDtos[2], validFromDate.plusWeeks(3), sessionTemplate, SessionRestriction.OPEN)
  }

  @Test
  fun `visit sessions are returned prisoner with an existing application and booking that has a non-association prisoner with no bookings`() {
    // Given
    val prisonerId = "A1234AA"
    val associationId = "B1234BB"
    val validFromDate = LocalDate.now()

    val sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek, prisonCode = prisonCode)
    val nextBooking = this.sessionDatesUtil.getFirstBookableSessionDay(sessionTemplate)

    this.applicationEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      slotDate = nextBooking,
      visitStart = LocalTime.of(9, 0),
      visitEnd = LocalTime.of(9, 30),
      visitType = SOCIAL,
      visitRestriction = VisitRestriction.OPEN,
      sessionTemplate = sessionTemplate,
      reservedSlot = true,
      completed = false,
    )

    this.applicationEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      slotDate = nextBooking.plusWeeks(1),
      visitStart = LocalTime.of(9, 0),
      visitEnd = LocalTime.of(9, 30),
      visitType = SOCIAL,
      visitRestriction = VisitRestriction.OPEN,
      sessionTemplate = sessionTemplate,
      reservedSlot = true,
      completed = false,
    )

    this.visitEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      slotDate = nextBooking.plusWeeks(2),
      visitStart = LocalTime.of(9, 0),
      visitEnd = LocalTime.of(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = VisitRestriction.OPEN,
      sessionTemplate = sessionTemplate,
    )

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerId,
      associationId,
    )

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionDtos = getResults(returnResult)

    assertThat(visitSessionDtos).hasSize(1)
    assertSession(visitSessionDtos[0], validFromDate.plusWeeks(4), sessionTemplate, SessionRestriction.OPEN)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking`() {
    // Given
    val prisonerId = "A1234AA"
    val associationPrisonerId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()
    val sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek, prisonCode = prisonCode)

    this.visitEntityHelper.create(
      prisonerId = associationPrisonerId,
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      slotDate = validFromDate,
      visitStart = LocalTime.of(9, 0),
      visitEnd = LocalTime.of(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = VisitRestriction.OPEN,
      sessionTemplate = sessionTemplate,
    )

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerId,
      associationPrisonerId,
    )

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    assertResponseLength(responseSpec, 3)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking CANCELLED`() {
    // Given
    val prisonerId = "A1234AA"
    val associationPrisonerId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()

    val sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek, prisonCode = prisonCode)

    this.visitEntityHelper.create(
      prisonerId = associationPrisonerId,
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      slotDate = validFromDate,
      visitStart = LocalTime.of(9, 0),
      visitEnd = LocalTime.of(9, 30),
      visitType = SOCIAL,
      visitStatus = CANCELLED,
      visitRestriction = VisitRestriction.OPEN,
      sessionTemplate = sessionTemplate,
    )

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerId,
      associationPrisonerId,
    )

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    assertResponseLength(responseSpec, 4)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking in the past`() {
    // Given
    val prisonerId = "A1234AA"
    val associationPrisonerId = "B1234BB"
    val validFromDate = this.getNextAllowedDay().minusMonths(6)
    val sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek, prisonCode = prisonCode)

    this.visitEntityHelper.create(
      prisonerId = associationPrisonerId,
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      slotDate = validFromDate,
      visitStart = LocalTime.of(9, 0),
      visitEnd = LocalTime.of(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = VisitRestriction.OPEN,
      sessionTemplate = sessionTemplate,
    )

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerId,
      associationPrisonerId,
    )

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    assertResponseLength(responseSpec, 4)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking in the future`() {
    // Given
    val prisonerId = "A1234AA"
    val associationPrisonerId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()
    val sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = validFromDate, validToDate = null, dayOfWeek = validFromDate.dayOfWeek, prisonCode = prisonCode)

    this.visitEntityHelper.create(
      prisonerId = associationPrisonerId,
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      slotDate = validFromDate.plusMonths(3),
      visitStart = LocalTime.of(9, 0),
      visitEnd = LocalTime.of(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = VisitRestriction.OPEN,
      sessionTemplate = sessionTemplate,
    )

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prison.code)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerId,
      associationPrisonerId,
    )
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    assertResponseLength(responseSpec, 4)
  }

  @Test
  fun `visit sessions are returned for a prisoner when an abandoned application for the prisoner by the same user exists`() {
    // Given
    val prisonerId = "A1234AA"
    val validFromDate = this.getNextAllowedDay()
    val sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = validFromDate, validToDate = null, dayOfWeek = validFromDate.dayOfWeek, prisonCode = prisonCode)
    val createdByUser = "test-user"

    // an application for the prisoner created by the current user exists
    val reserveVisitSlotDto = createReserveVisitSlotDto(actionedBy = createdByUser, prisonerId = prisonerId, sessionTemplate = sessionTemplate, slotDate = validFromDate, userType = UserType.PUBLIC)
    submitApplication(webTestClient, setAuthorisation(roles = requiredRole), reserveVisitSlotDto)

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prison.code)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")

    // When

    // excludeApplicationsForUser is passed as the same user who reserved an application for the prisoner against the same slot
    val responseSpec = callGetAvailableSessions(
      prisonCode = prisonCode,
      prisonerId = prisonerId,
      sessionRestriction = SessionRestriction.OPEN,
      username = createdByUser,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val sessions = getResults(returnResult)
    assertThat(sessions.size).isEqualTo(4)
    val sessionDates = sessions.map { it.sessionDate }
    assertThat(sessionDates).contains(validFromDate)
  }

  @Test
  fun `visit sessions are not returned for a prisoner when an abandoned application for the prisoner by a different user exists`() {
    // Given
    val prisonerId = "A1234AA"
    val validFromDate = this.getNextAllowedDay()
    val createdByUser = "test-user"
    val sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = validFromDate, validToDate = null, dayOfWeek = validFromDate.dayOfWeek, prisonCode = prisonCode)

    // an application for the prisoner created by the current user exists
    val reserveVisitSlotDto = createReserveVisitSlotDto(actionedBy = createdByUser, prisonerId = prisonerId, sessionTemplate = sessionTemplate, slotDate = validFromDate, userType = UserType.PUBLIC)
    submitApplication(webTestClient, setAuthorisation(roles = requiredRole), reserveVisitSlotDto)

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prison.code)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")

    // When
    // excludeApplicationsForUser is not the same as the user who reserved an application for the prisoner against the same slot
    val responseSpec = callGetAvailableSessions(
      prisonCode = prisonCode,
      prisonerId = prisonerId,
      sessionRestriction = SessionRestriction.OPEN,
      username = "other-user",
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val sessions = getResults(returnResult)
    assertThat(sessions.size).isEqualTo(3)
    val sessionDates = sessions.map { it.sessionDate }
    assertThat(sessionDates).doesNotContain(validFromDate)
  }

  @Test
  fun `visit sessions are not returned for a prisoner when excludeApplicationsForUser is not passed in`() {
    // Given
    val prisonerId = "A1234AA"
    val validFromDate = this.getNextAllowedDay()
    val createdByUser = "test-user"
    val sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = validFromDate, validToDate = null, dayOfWeek = validFromDate.dayOfWeek, prisonCode = prisonCode)

    // an application for the prisoner created by the current user exists
    val reserveVisitSlotDto = createReserveVisitSlotDto(actionedBy = createdByUser, prisonerId = prisonerId, sessionTemplate = sessionTemplate, slotDate = validFromDate, userType = UserType.PUBLIC)
    submitApplication(webTestClient, setAuthorisation(roles = requiredRole), reserveVisitSlotDto)

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prison.code)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")

    // When
    // excludeApplicationsForUser is not the same as the user who reserved an application for the prisoner against the same slot
    val responseSpec = callGetAvailableSessions(
      prisonCode = prisonCode,
      prisonerId = prisonerId,
      sessionRestriction = SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val sessions = getResults(returnResult)
    assertThat(sessions.size).isEqualTo(3)
    val sessionDates = sessions.map { it.sessionDate }
    assertThat(sessionDates).doesNotContain(validFromDate)
  }

  @Test
  fun `when get visit session is called with prison id different to prisoners establishment code bad request error is returned`() {
    val incorrectPrisonCode = "ABC"
    val prisonerId = "A1234AA"

    prisonEntityHelper.create(incorrectPrisonCode)

    // prisoner is in prison with code MDI
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    // When
    // get sessions call is being made with the incorrect prison Code
    val responseSpec = callGetAvailableSessions(
      incorrectPrisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then

    val returnResult = responseSpec.expectStatus().isBadRequest.expectBody()
    val errorResponse = objectMapper.readValue(returnResult.returnResult().responseBody, ErrorResponse::class.java)
    assertThat(errorResponse.developerMessage).isEqualTo("Prisoner with ID - $prisonerId is not in prison - $incorrectPrisonCode but STC")
  }

  @Test
  fun `when get visit session and prisoner details can not be found bad request error is returned`() {
    // Given
    val prisonerId = "A1234AA"
    val enhancedIncentiveLevelGroup = "ENH Incentive Level Group"
    val incentiveLevelList = listOf(
      IncentiveLevel.ENHANCED,
    )
    val incentiveLevelGroup = sessionPrisonerIncentiveLevelHelper.create(enhancedIncentiveLevelGroup, prisonCode, incentiveLevelList)

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, IncentiveLevel.ENHANCED)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, null)
    val nextAllowedDay = getNextAllowedDay()

    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      permittedIncentiveLevels = mutableListOf(incentiveLevelGroup),
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isNotFound.expectBody()
    val errorResponse = objectMapper.readValue(returnResult.returnResult().responseBody, ErrorResponse::class.java)
    assertThat(errorResponse.developerMessage).isEqualTo("Prisoner with prisonNumber - $prisonerId not found on offender search")
  }

  @Test
  fun `when get visit session and prisoner offender search details can not be found an appropriate error is returned`() {
    // Given
    val prisonerId = "A1234AA"
    val enhancedIncentiveLevelGroup = "ENH Incentive Level Group"
    val incentiveLevelList = listOf(
      IncentiveLevel.ENHANCED,
    )
    val incentiveLevelGroup = sessionPrisonerIncentiveLevelHelper.create(enhancedIncentiveLevelGroup, prisonCode, incentiveLevelList)

    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerSearchResultDto = null)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)

    val nextAllowedDay = getNextAllowedDay()

    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      permittedIncentiveLevels = mutableListOf(incentiveLevelGroup),
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    val returnResult = responseSpec.expectStatus().isNotFound.expectBody()
    val errorResponse = objectMapper.readValue(returnResult.returnResult().responseBody, ErrorResponse::class.java)
    assertThat(errorResponse.developerMessage).isEqualTo("Prisoner with prisonNumber - $prisonerId not found on offender search")
  }

  @Test
  fun `when get visit session and prisoner has 404 error on non association no errors are returned`() {
    // Given
    val prisonerId = "A1234AA"
    val enhancedIncentiveLevelGroup = "ENH Incentive Level Group"
    val incentiveLevelList = listOf(
      IncentiveLevel.ENHANCED,
    )
    val incentiveLevelGroup = sessionPrisonerIncentiveLevelHelper.create(enhancedIncentiveLevelGroup, prisonCode, incentiveLevelList)

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, IncentiveLevel.ENHANCED)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(prisonerId, null)

    val nextAllowedDay = getNextAllowedDay()

    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      permittedIncentiveLevels = mutableListOf(incentiveLevelGroup),
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when get visit session and prisoner has non 404 error on non association errors are returned`() {
    // Given
    val prisonerId = "A1234AA"
    val enhancedIncentiveLevelGroup = "ENH Incentive Level Group"
    val incentiveLevelList = listOf(
      IncentiveLevel.ENHANCED,
    )
    val incentiveLevelGroup = sessionPrisonerIncentiveLevelHelper.create(enhancedIncentiveLevelGroup, prisonCode, incentiveLevelList)

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, IncentiveLevel.ENHANCED)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(prisonerId, status = BAD_REQUEST)

    val nextAllowedDay = getNextAllowedDay()

    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      permittedIncentiveLevels = mutableListOf(incentiveLevelGroup),
    )

    // When
    val responseSpec = callGetAvailableSessions(
      prisonCode,
      prisonerId,
      SessionRestriction.OPEN,
    )

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  private fun callGetAvailableSessions(
    prisonCode: String? = "SPC",
    prisonerId: String,
    sessionRestriction: SessionRestriction,
    policyNoticeDaysMin: Int = 2,
    policyNoticeDaysMax: Int = 28,
    excludedApplicationReference: String? = null,
    username: String? = null,
  ): ResponseSpec {
    val today = LocalDate.now()
    val fromDate = today.plusDays(policyNoticeDaysMin.toLong())
    val toDate = today.plusDays(policyNoticeDaysMax.toLong())
    val dateRange = DateRange(fromDate, toDate)

    val uri = "/visit-sessions/available"
    val uriQueryParams = getAvailableSessionsQueryParams(
      prisonCode = prisonCode!!,
      prisonerId = prisonerId,
      sessionRestriction = sessionRestriction,
      dateRange = dateRange,
      policyNoticeDaysMin = policyNoticeDaysMin,
      policyNoticeDaysMax = policyNoticeDaysMax,
      excludedApplicationReference = excludedApplicationReference,
      username = username,
    ).joinToString("&")

    return webTestClient.get().uri("$uri?$uriQueryParams")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()
  }

  private fun getAvailableSessionsQueryParams(
    prisonCode: String,
    prisonerId: String,
    sessionRestriction: SessionRestriction,
    dateRange: DateRange,
    policyNoticeDaysMin: Int,
    policyNoticeDaysMax: Int,
    excludedApplicationReference: String?,
    username: String?,
  ): List<String> {
    val queryParams = ArrayList<String>()
    queryParams.add("prisonId=$prisonCode")
    queryParams.add("prisonerId=$prisonerId")
    queryParams.add("sessionRestriction=$sessionRestriction")
    queryParams.add("fromDate=${dateRange.fromDate}")
    queryParams.add("toDate=${dateRange.toDate}")
    queryParams.add("policyNoticeDaysMin=$policyNoticeDaysMin")
    queryParams.add("policyNoticeDaysMax=$policyNoticeDaysMax")

    excludedApplicationReference?.let {
      queryParams.add("excludedApplicationReference=$excludedApplicationReference")
    }

    username?.let {
      queryParams.add("username=$username")
    }
    return queryParams
  }
  private fun getNextAllowedDay(): LocalDate {
    // The 3 days is based on the default SessionService.policyNoticeDaysMin
    return LocalDate.now().plusDays(3)
  }

  private fun assertSession(
    visitSession: AvailableVisitSessionDto,
    expectedDate: LocalDate,
    expectedSessionTemplate: SessionTemplate,
    sessionRestriction: SessionRestriction,
  ) {
    assertThat(visitSession.sessionTemplateReference).isEqualTo(expectedSessionTemplate.reference)
    assertThat(visitSession.sessionDate).isEqualTo(expectedDate)
    assertThat(visitSession.sessionTimeSlot.startTime).isEqualTo(expectedSessionTemplate.startTime)
    assertThat(visitSession.sessionTimeSlot.endTime).isEqualTo(expectedSessionTemplate.endTime)
    assertThat(visitSession.sessionRestriction).isEqualTo(sessionRestriction)
  }

  private fun assertResponseLength(responseSpec: ResponseSpec, length: Int) {
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(length)
  }

  private fun assertNoResponse(responseSpec: ResponseSpec) {
    assertResponseLength(responseSpec, 0)
  }

  private fun getResults(returnResult: BodyContentSpec): Array<AvailableVisitSessionDto> {
    return objectMapper.readValue(returnResult.returnResult().responseBody, Array<AvailableVisitSessionDto>::class.java)
  }
}
