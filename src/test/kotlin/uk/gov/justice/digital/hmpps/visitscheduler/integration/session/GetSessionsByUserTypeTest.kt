package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_SESSIONS_AVAILABLE_CONTROLLER_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_SESSION_CONTROLLER_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UserClientDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.SYSTEM
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.AvailableVisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("GET $VISIT_SESSION_CONTROLLER_PATH and GET $VISIT_SESSIONS_AVAILABLE_CONTROLLER_PATH with userType")
class GetSessionsByUserTypeTest : IntegrationTestBase() {
  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  private val prisonerId = "A0000001"

  private val prisonCode = "STC"

  private lateinit var nextAllowedDay: LocalDate
  private lateinit var sessionTemplate1: SessionTemplate
  private lateinit var sessionTemplate2: SessionTemplate
  private lateinit var sessionTemplate3: SessionTemplate
  private lateinit var sessionTemplate4: SessionTemplate
  private lateinit var sessionTemplate5: SessionTemplate
  private lateinit var authHttpHeaders: (org.springframework.http.HttpHeaders) -> Unit

  @BeforeEach
  internal fun setUpTests() {
    authHttpHeaders = setAuthorisation(roles = requiredRole)
    prison = prisonEntityHelper.create(prisonCode = prisonCode)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nextAllowedDay = getNextAllowedDay()

    // session available to STAFF only
    sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:01"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      clients = listOf(UserClientDto(STAFF, true)),
      visitRoom = "Visits Main Hall",
    )

    // session available to PUBLIC only
    sessionTemplate2 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("11:01"),
      endTime = LocalTime.parse("12:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      clients = listOf(UserClientDto(PUBLIC, true)),
      visitRoom = "Visits Main Hall",
    )

    // session available to STAFF and PUBLIC
    sessionTemplate3 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("12:01"),
      endTime = LocalTime.parse("13:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      clients = listOf(UserClientDto(STAFF, true), UserClientDto(PUBLIC, true)),
      visitRoom = "Visits Main Hall",
    )

    // session not active for STAFF so should not be returned
    sessionTemplate4 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("13:01"),
      endTime = LocalTime.parse("14:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      clients = listOf(UserClientDto(STAFF, false)),
      visitRoom = "Visits Main Hall",
    )

    // session not active for PUBLIC so should not be returned
    sessionTemplate5 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("14:01"),
      endTime = LocalTime.parse("15:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
      clients = listOf(UserClientDto(PUBLIC, false)),
      visitRoom = "Visits Main Hall",
    )
  }

  @Test
  fun `when get visit sessions called with userType STAFF only visit sessions for STAFF are returned`() {
    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSessionResults = getVisitSessionResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(2)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate1)
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplate3)
  }

  @Test
  fun `when get visit sessions called with userType as SYSTEM a BAD_REQUEST error is returned`() {
    // Given
    val prisonerId = "A1234AA"

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = SYSTEM, authHttpHeaders = setAuthorisation(roles = requiredRole))

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `when get visit sessions called with userType as PUBLIC a BAD_REQUEST error is returned`() {
    // Given
    val prisonerId = "A1234AA"

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = PUBLIC, authHttpHeaders = authHttpHeaders)

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `when get available visit sessions called with userType STAFF only available visit sessions for STAFF are returned`() {
    // When
    val responseSpec = callGetAvailableSessions(prisonCode, prisonerId, userType = STAFF, sessionRestriction = OPEN, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getAvailableVisitSessionResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(2)
    assertAvailableVisitSession(visitSessionResults[0], nextAllowedDay, sessionTemplate1)
    assertAvailableVisitSession(visitSessionResults[1], nextAllowedDay, sessionTemplate3)
  }

  @Test
  fun `when get available visit sessions called with userType PUBLIC only available visit sessions for PUBLIC are returned`() {
    // When
    val responseSpec = callGetAvailableSessions(prisonCode, prisonerId, userType = PUBLIC, sessionRestriction = OPEN, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getAvailableVisitSessionResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(2)
    assertAvailableVisitSession(visitSessionResults[0], nextAllowedDay, sessionTemplate2)
    assertAvailableVisitSession(visitSessionResults[1], nextAllowedDay, sessionTemplate3)
  }

  @Test
  fun `when get available visit sessions called with userType SYSTEM no sessions are returned`() {
    // When
    val userType = SYSTEM
    val responseSpec = callGetAvailableSessions(prisonCode, prisonerId, userType = userType, sessionRestriction = OPEN, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getAvailableVisitSessionResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(0)
  }

  private fun getNextAllowedDay(): LocalDate {
    // The 3 days is based on the default SessionService.policyNoticeDaysMin
    // VB-5790 - adding 1 day after adding policyNoticeDaysMin as there is a change wherein
    // fix sessions are returned after n whole days and not and not today + n so adding a day
    // e.g if today is WED and policyNoticeDaysMin is 2 sessions need to be returned from SATURDAY and not FRIDAY
    return LocalDate.now().plusDays(3).plusDays(1)
  }

  private fun assertSession(
    visitSessionResult: VisitSessionDto,
    testDate: LocalDate,
    expectedSessionTemplate: SessionTemplate,
  ) {
    assertThat(visitSessionResult.startTimestamp)
      .isEqualTo(testDate.atTime(expectedSessionTemplate.startTime))
    assertThat(visitSessionResult.endTimestamp).isEqualTo(testDate.atTime(expectedSessionTemplate.endTime))
    assertThat(visitSessionResult.startTimestamp.dayOfWeek).isEqualTo(expectedSessionTemplate.dayOfWeek)
    assertThat(visitSessionResult.endTimestamp.dayOfWeek).isEqualTo(expectedSessionTemplate.dayOfWeek)
  }

  private fun assertAvailableVisitSession(
    visitSession: AvailableVisitSessionDto,
    expectedDate: LocalDate,
    expectedSessionTemplate: SessionTemplate,
  ) {
    assertThat(visitSession.sessionTemplateReference).isEqualTo(expectedSessionTemplate.reference)
    assertThat(visitSession.sessionDate).isEqualTo(expectedDate)
    assertThat(visitSession.sessionTimeSlot.startTime).isEqualTo(expectedSessionTemplate.startTime)
    assertThat(visitSession.sessionTimeSlot.endTime).isEqualTo(expectedSessionTemplate.endTime)
  }

  private fun getVisitSessionResults(returnResult: BodyContentSpec): Array<VisitSessionDto> = objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)

  private fun getAvailableVisitSessionResults(returnResult: BodyContentSpec): Array<AvailableVisitSessionDto> = objectMapper.readValue(returnResult.returnResult().responseBody, Array<AvailableVisitSessionDto>::class.java)
}
