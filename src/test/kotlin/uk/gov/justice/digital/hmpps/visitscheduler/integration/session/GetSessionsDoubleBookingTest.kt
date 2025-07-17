package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_SESSION_CONTROLLER_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus.ACCEPTED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.DOUBLE_BOOKING_OR_RESERVATION
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate

@DisplayName("GET $VISIT_SESSION_CONTROLLER_PATH - Tests to check for DOUBLE_BOOKING_OR_RESERVATION flag.")
class GetSessionsDoubleBookingTest : IntegrationTestBase() {

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  private val prisonerId = "A0000001"

  private val prisonCode = "STC"

  private lateinit var sessionTemplate: SessionTemplate

  private lateinit var visitDate: LocalDate

  private lateinit var authHttpHeaders: (org.springframework.http.HttpHeaders) -> Unit

  @BeforeEach
  internal fun setUpTests() {
    authHttpHeaders = setAuthorisation(roles = requiredRole)
    visitDate = getNextAllowedDay()
    prison = prisonEntityHelper.create(prisonCode = prisonCode)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = visitDate,
      validToDate = visitDate,
      dayOfWeek = visitDate.dayOfWeek,
      prisonCode = prisonCode,
    )
  }

  @Test
  fun `visit sessions have double booking flagged when a visit already exists for a session slot`() {
    // Given
    this.visitEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      slotDate = visitDate,
      visitStart = sessionTemplate.startTime,
      visitEnd = sessionTemplate.endTime,
      visitStatus = VisitStatus.BOOKED,
      sessionTemplate = sessionTemplate,
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertThat(visitSessionResults[0].sessionConflicts.contains(DOUBLE_BOOKING_OR_RESERVATION))
  }

  @Test
  fun `visit sessions have double booking flagged when an application already exists for a session slot and username passed is null`() {
    // Given
    this.applicationEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      slotDate = visitDate,
      visitStart = sessionTemplate.startTime,
      visitEnd = sessionTemplate.endTime,
      sessionTemplate = sessionTemplate,
      createdBy = "TEST-USER",
      applicationStatus = ACCEPTED,
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userName = null, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertThat(visitSessionResults[0].sessionConflicts.contains(DOUBLE_BOOKING_OR_RESERVATION))
  }

  @Test
  fun `visit sessions have double booking flagged when an application created by a different user already exists for the same session slot`() {
    // Given
    val currentUser = "CURRENT-USER"

    this.applicationEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      slotDate = visitDate,
      visitStart = sessionTemplate.startTime,
      visitEnd = sessionTemplate.endTime,
      sessionTemplate = sessionTemplate,
      createdBy = "TEST-USER",
      applicationStatus = ACCEPTED,
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userName = currentUser, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertThat(visitSessionResults[0].sessionConflicts.contains(DOUBLE_BOOKING_OR_RESERVATION))
  }

  @Test
  fun `no visit sessions have session conflicts when an application created by same user already exists for the same session slot`() {
    // Given
    val currentUser = "CURRENT-USER"

    this.applicationEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      slotDate = visitDate,
      visitStart = sessionTemplate.startTime,
      visitEnd = sessionTemplate.endTime,
      sessionTemplate = sessionTemplate,
      createdBy = currentUser,
      applicationStatus = ACCEPTED,
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userName = currentUser, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertThat(visitSessionResults[0].sessionConflicts.isEmpty())
  }

  private fun getNextAllowedDay(): LocalDate {
    // VB-5790 - adding 1 day after adding policyNoticeDaysMin as there is a change wherein
    // fix sessions are returned after n whole days and not and not today + n so adding a day
    // e.g if today is WED and policyNoticeDaysMin is 2 sessions need to be returned from SATURDAY and not FRIDAY
    return LocalDate.now().plusDays(1).plusDays(3)
  }

  private fun getResults(returnResult: BodyContentSpec): Array<VisitSessionDto> = objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)
}
