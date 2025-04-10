package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.DOUBLE_BOOKING_OR_RESERVATION
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate

@DisplayName("Get /visit-sessions - Tests to check for DOUBLE_BOOKING_OR_RESERVATION flag.")
class GetSessionsDoubleBookingTest : IntegrationTestBase() {

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  private val prisonerId = "A0000001"

  private val prisonCode = "STC"

  private lateinit var sessionTemplate: SessionTemplate

  private lateinit var visitDate: LocalDate

  @BeforeEach
  internal fun setUpTests() {
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
    val responseSpec = callGetSessions(prisonCode, prisonerId)

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
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, username = null)

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
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, username = currentUser)

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
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, username = currentUser)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertThat(visitSessionResults[0].sessionConflicts.isEmpty())
  }

  private fun callGetSessions(
    prisonCode: String,
    prisonerId: String,
    policyNoticeDaysMin: Int = 2,
    policyNoticeDaysMax: Int = 28,
    username: String? = null,
    userType: UserType = UserType.STAFF,
  ): ResponseSpec {
    val uriQueryParams = mutableListOf(
      "prisonId=$prisonCode",
      "prisonerId=$prisonerId",
      "min=$policyNoticeDaysMin",
      "max=$policyNoticeDaysMax",
      "userType=$userType",
    ).also { params ->
      username?.let {
        params.add("username=$username")
      }
    }.joinToString("&")

    return webTestClient.get().uri("/visit-sessions?$uriQueryParams")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()
  }

  private fun getNextAllowedDay(): LocalDate = LocalDate.now().plusDays(3)

  private fun getResults(returnResult: BodyContentSpec): Array<VisitSessionDto> = objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)
}
