package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_SESSIONS_AVAILABLE_CONTROLLER_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_SESSION_CONTROLLER_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonUserClientDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UserClientDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import java.time.DayOfWeek
import java.time.LocalDate

@DisplayName("GET $VISIT_SESSION_CONTROLLER_PATH and GET $VISIT_SESSIONS_AVAILABLE_CONTROLLER_PATH with userType - to check booking window changes")
class GetSessionsByUserTypeBookingWindowsTest : IntegrationTestBase() {
  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  private val prisonerId = "A0000001"

  private val prisonCode = "STC"

  private lateinit var authHttpHeaders: (HttpHeaders) -> Unit

  @BeforeEach
  internal fun setUpTests() {
    val staffClient = PrisonUserClientDto(policyNoticeDaysMin = 0, policyNoticeDaysMax = 21, userType = STAFF, active = true)
    val publicClient = PrisonUserClientDto(policyNoticeDaysMin = 5, policyNoticeDaysMax = 29, userType = PUBLIC, active = true)
    authHttpHeaders = setAuthorisation(roles = requiredRole)
    prison = prisonEntityHelper.create(prisonCode = prisonCode, clients = listOf(staffClient, publicClient))
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)

    // session available to STAFF and PUBLIC
    for (dayOfWeek in DayOfWeek.entries) {
      createSessionTemplate(dayOfWeek)
    }
  }

  @Test
  fun `when get visit sessions called with userType STAFF only visit sessions for STAFF are returned`() {
    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)
    val expectedSessionDates = (1L..21L).map { LocalDate.now().plusDays(it) }.toList()

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getVisitSessionResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(21)
    assertThat(visitSessionResults[0].startTimestamp.toLocalDate()).isEqualTo(LocalDate.now().plusDays(1))
    assertThat(visitSessionResults.last().startTimestamp.toLocalDate()).isEqualTo(LocalDate.now().plusDays(21))
    assertThat(visitSessionResults.map { it.startTimestamp.toLocalDate() }.toList()).isEqualTo(expectedSessionDates)
  }

  private fun createSessionTemplate(dayOfWeek: DayOfWeek) = sessionTemplateEntityHelper.create(
    validFromDate = LocalDate.now().minusMonths(1),
    dayOfWeek = dayOfWeek,
    prisonCode = prisonCode,
    clients = listOf(UserClientDto(STAFF, true), UserClientDto(PUBLIC, true)),
    visitRoom = "Visits Main Hall",
  )

  private fun getVisitSessionResults(returnResult: BodyContentSpec): Array<VisitSessionDto> = objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)
}
