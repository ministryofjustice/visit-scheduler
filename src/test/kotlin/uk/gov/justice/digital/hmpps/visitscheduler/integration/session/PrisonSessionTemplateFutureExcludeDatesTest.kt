package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.SESSION_TEMPLATE_FUTURE_EXCLUDE_DATES_FOR_PRISON_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionScheduleWithDateExclusionsDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGetSessionTemplateFutureExcludeDatesForPrison
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import java.time.LocalDate

@DisplayName("Tests for $SESSION_TEMPLATE_FUTURE_EXCLUDE_DATES_FOR_PRISON_PATH")
class PrisonSessionTemplateFutureExcludeDatesTest : IntegrationTestBase() {
  private val prisonCode = "MDI"

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @BeforeEach
  fun setup() {
    prison = prisonEntityHelper.create(prisonCode = prisonCode)
  }

  @Test
  fun `when get exclude dates called for prison with multiple sessions then only sessions with future exclude dates are returned`() {
    // Given
    val today = LocalDate.now()
    val futureExcludeDate1 = today.plusDays(1)
    val futureExcludeDate2 = today.plusDays(2)
    val pastExcludeDate = today.minusDays(1)

    // session 1 has 2 future excluded dates
    val sessionTemplate1 = sessionTemplateEntityHelper.create(prisonCode = prisonCode, excludeDates = mutableListOf(today, futureExcludeDate1, futureExcludeDate2, pastExcludeDate))
    // session 2 has no excluded dates
    sessionTemplateEntityHelper.create(prisonCode = prisonCode, excludeDates = emptyList())

    val getResponseSpec = callGetSessionTemplateFutureExcludeDatesForPrison(webTestClient, roleVisitSchedulerHttpHeaders, prisonCode = prison.code)
    val result = getResponseSpec.expectStatus().isOk.expectBody()
    val excludedSessions = getSessionSchedulesWithDateExclusions(result)
    Assertions.assertThat(excludedSessions).isNotEmpty
    Assertions.assertThat(excludedSessions.size).isEqualTo(1)
    Assertions.assertThat(excludedSessions[0].sessionScheduleDto.sessionTemplateReference).isEqualTo(sessionTemplate1.reference)
    Assertions.assertThat(excludedSessions[0].excludeDates.map { it.excludeDate }).containsExactlyInAnyOrder(today, futureExcludeDate1, futureExcludeDate2)
  }

  @Test
  fun `when no sessions exist with excluded dates in the future an empty list is returned`() {
    // Given
    val today = LocalDate.now()
    val pastExcludeDate = today.minusDays(1)

    // session 1 has only past excluded dates
    sessionTemplateEntityHelper.create(prisonCode = prisonCode, excludeDates = mutableListOf(pastExcludeDate))
    // session 2 has no past excluded dates
    sessionTemplateEntityHelper.create(prisonCode = prisonCode, excludeDates = emptyList())

    val getResponseSpec = callGetSessionTemplateFutureExcludeDatesForPrison(webTestClient, roleVisitSchedulerHttpHeaders, prisonCode = prison.code)
    val result = getResponseSpec.expectStatus().isOk.expectBody()
    val excludedSessions = getSessionSchedulesWithDateExclusions(result)
    Assertions.assertThat(excludedSessions).isEmpty()
  }

  @Test
  fun `access forbidden when no role`() {
    // When
    val responseSpec = callGetSessionTemplateFutureExcludeDatesForPrison(webTestClient, setAuthorisation(roles = listOf()), prisonCode = "QQQ")

    // Then
    responseSpec.expectStatus().isForbidden
  }

  @Test
  fun `unauthorised when no token`() {
    val responseSpec = webTestClient.get().uri("/admin/session-templates/QQQ/exclude-dates/future").exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  private fun getSessionSchedulesWithDateExclusions(returnResult: BodyContentSpec): Array<SessionScheduleWithDateExclusionsDto> = objectMapper.readValue(returnResult.returnResult().responseBody, Array<SessionScheduleWithDateExclusionsDto>::class.java)
}
