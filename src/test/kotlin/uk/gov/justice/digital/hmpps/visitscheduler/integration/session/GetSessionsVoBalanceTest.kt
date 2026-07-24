package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import uk.gov.justice.digital.hmpps.visitscheduler.client.VisitAllocationApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_SESSION_CONTROLLER_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionTemplateVisitOrderRestrictionType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visit.allocation.VisitOrderPrisonerBalanceDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@DisplayName("GET $VISIT_SESSION_CONTROLLER_PATH - Tests to check for NO_VOS, NO_PVOS or NO_VO_OR_PVOS flag.")
class GetSessionsVoBalanceTest : IntegrationTestBase() {

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  private val remandPrisonerId = "A0000001"
  private val convictedPrisonerId = "A0000002"

  private val prisonCode = "STC"
  private val policyNoticeDaysMin = 0

  private lateinit var authHttpHeaders: (HttpHeaders) -> Unit

  private fun firstBookableMonday(): LocalDate = LocalDate.now()
    .plusDays((policyNoticeDaysMin + 1).toLong())
    .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))

  @MockitoSpyBean
  private lateinit var visitAllocationApiClientSpy: VisitAllocationApiClient

  @BeforeEach
  internal fun setUpTests() {
    authHttpHeaders = setAuthorisation(roles = requiredRole)
    prison = prisonEntityHelper.create(prisonCode = prisonCode, policyNoticeDaysMin = policyNoticeDaysMin, policyNoticeDaysMax = 14, remandVisitLimitPerWeek = 2, weekStartDay = DayOfWeek.MONDAY)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId = remandPrisonerId, prisonCode = prisonCode, convictedStatus = "REMAND")
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId = convictedPrisonerId, prisonCode = prisonCode, convictedStatus = "CONVICTED")
  }

  @Test
  fun `when prisoner is on remand then VO or PVO balance is not checked for that prisoner`() {
    // Given
    val today = LocalDate.now()
    val sessionDate = firstBookableMonday()
    val sessionTemplate = sessionTemplateEntityHelper.create(visitOrderRestrictionType = SessionTemplateVisitOrderRestrictionType.VO, prisonCode = prisonCode, weeklyFrequency = 1, validFromDate = today.minusMonths(1), dayOfWeek = DayOfWeek.MONDAY)
    val prisonerBalance = null
    visitAllocationApiMockServer.stubGetPrisonerVOBalance(prisonerId = remandPrisonerId, prisonerBalance)
    // When
    val responseSpec = callGetSessions(prisonCode, remandPrisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResultForSessionDate = getResults(returnResult).first { it.sessionTemplateReference == sessionTemplate.reference && it.startTimestamp.toLocalDate() == sessionDate }
    assertThat(visitSessionResultForSessionDate).isNotNull
    assertThat(visitSessionResultForSessionDate.sessionConflicts).isEmpty()
    verify(visitAllocationApiClientSpy, times(0)).getPrisonerVOBalance(any())
  }

  @Test
  fun `when prisoner is convicted and session restriction is VO only then a NO_VO conflict is added if VO balance is negative`() {
    // Given
    val today = LocalDate.now()
    val sessionDate = firstBookableMonday()
    val sessionTemplate = sessionTemplateEntityHelper.create(visitOrderRestrictionType = SessionTemplateVisitOrderRestrictionType.VO, prisonCode = prisonCode, weeklyFrequency = 1, validFromDate = today.minusMonths(1), dayOfWeek = DayOfWeek.MONDAY)
    val prisonerBalance = VisitOrderPrisonerBalanceDto(prisonerId = convictedPrisonerId, voBalance = -1, pvoBalance = 3)
    visitAllocationApiMockServer.stubGetPrisonerVOBalance(prisonerId = convictedPrisonerId, prisonerBalance)
    // When
    val responseSpec = callGetSessions(prisonCode, convictedPrisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResultForSessionDate = getResults(returnResult).first { it.sessionTemplateReference == sessionTemplate.reference && it.startTimestamp.toLocalDate() == sessionDate }
    assertThat(visitSessionResultForSessionDate).isNotNull
    assertThat(visitSessionResultForSessionDate.sessionConflicts).hasSize(1)
    assertThat(visitSessionResultForSessionDate.sessionConflicts.map { it.sessionConflict }).containsOnly(SessionConflict.NO_VO_BALANCE)
    verify(visitAllocationApiClientSpy, times(1)).getPrisonerVOBalance(convictedPrisonerId)
  }

  @Test
  fun `when prisoner is convicted and session restriction is VO only then a NO_VO conflict is added if VO balance is zero`() {
    // Given
    val today = LocalDate.now()
    val sessionDate = firstBookableMonday()
    val sessionTemplate = sessionTemplateEntityHelper.create(visitOrderRestrictionType = SessionTemplateVisitOrderRestrictionType.VO, prisonCode = prisonCode, weeklyFrequency = 1, validFromDate = today.minusMonths(1), dayOfWeek = DayOfWeek.MONDAY)
    val prisonerBalance = VisitOrderPrisonerBalanceDto(prisonerId = convictedPrisonerId, voBalance = 0, pvoBalance = 3)
    visitAllocationApiMockServer.stubGetPrisonerVOBalance(prisonerId = convictedPrisonerId, prisonerBalance)
    // When
    val responseSpec = callGetSessions(prisonCode, convictedPrisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResultForSessionDate = getResults(returnResult).first { it.sessionTemplateReference == sessionTemplate.reference && it.startTimestamp.toLocalDate() == sessionDate }
    assertThat(visitSessionResultForSessionDate).isNotNull
    assertThat(visitSessionResultForSessionDate.sessionConflicts).hasSize(1)
    assertThat(visitSessionResultForSessionDate.sessionConflicts.map { it.sessionConflict }).containsOnly(SessionConflict.NO_VO_BALANCE)
    verify(visitAllocationApiClientSpy, times(1)).getPrisonerVOBalance(convictedPrisonerId)
  }

  @Test
  fun `when prisoner is convicted and session restriction is VO only then no conflict is added if VO balance is available`() {
    // Given
    val today = LocalDate.now()
    val sessionDate = firstBookableMonday()
    val sessionTemplate = sessionTemplateEntityHelper.create(visitOrderRestrictionType = SessionTemplateVisitOrderRestrictionType.VO, prisonCode = prisonCode, weeklyFrequency = 1, validFromDate = today.minusMonths(1), dayOfWeek = DayOfWeek.MONDAY)
    val prisonerBalance = VisitOrderPrisonerBalanceDto(prisonerId = convictedPrisonerId, voBalance = 1, pvoBalance = 0)
    visitAllocationApiMockServer.stubGetPrisonerVOBalance(prisonerId = convictedPrisonerId, prisonerBalance)
    // When
    val responseSpec = callGetSessions(prisonCode, convictedPrisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResultForSessionDate = getResults(returnResult).first { it.sessionTemplateReference == sessionTemplate.reference && it.startTimestamp.toLocalDate() == sessionDate }
    assertThat(visitSessionResultForSessionDate).isNotNull
    assertThat(visitSessionResultForSessionDate.sessionConflicts).isEmpty()
    verify(visitAllocationApiClientSpy, times(1)).getPrisonerVOBalance(convictedPrisonerId)
  }

  @Test
  fun `when prisoner is convicted and session restriction is PVO only then a NO_PVO conflict is added if PVO balance is negative`() {
    // Given
    val today = LocalDate.now()
    val sessionDate = firstBookableMonday()
    val sessionTemplate = sessionTemplateEntityHelper.create(visitOrderRestrictionType = SessionTemplateVisitOrderRestrictionType.PVO, prisonCode = prisonCode, weeklyFrequency = 1, validFromDate = today.minusMonths(1), dayOfWeek = DayOfWeek.MONDAY)
    val prisonerBalance = VisitOrderPrisonerBalanceDto(prisonerId = convictedPrisonerId, voBalance = 1, pvoBalance = -3)
    visitAllocationApiMockServer.stubGetPrisonerVOBalance(prisonerId = convictedPrisonerId, prisonerBalance)
    // When
    val responseSpec = callGetSessions(prisonCode, convictedPrisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResultForSessionDate = getResults(returnResult).first { it.sessionTemplateReference == sessionTemplate.reference && it.startTimestamp.toLocalDate() == sessionDate }
    assertThat(visitSessionResultForSessionDate).isNotNull
    assertThat(visitSessionResultForSessionDate.sessionConflicts).hasSize(1)
    assertThat(visitSessionResultForSessionDate.sessionConflicts.map { it.sessionConflict }).containsOnly(SessionConflict.NO_PVO_BALANCE)
    verify(visitAllocationApiClientSpy, times(1)).getPrisonerVOBalance(convictedPrisonerId)
  }

  @Test
  fun `when prisoner is convicted and session restriction is PVO only then a NO_PVO conflict is added if PVO balance is zero`() {
    // Given
    val today = LocalDate.now()
    val sessionDate = firstBookableMonday()
    val sessionTemplate = sessionTemplateEntityHelper.create(visitOrderRestrictionType = SessionTemplateVisitOrderRestrictionType.PVO, prisonCode = prisonCode, weeklyFrequency = 1, validFromDate = today.minusMonths(1), dayOfWeek = DayOfWeek.MONDAY)
    val prisonerBalance = VisitOrderPrisonerBalanceDto(prisonerId = convictedPrisonerId, voBalance = 4, pvoBalance = 0)
    visitAllocationApiMockServer.stubGetPrisonerVOBalance(prisonerId = convictedPrisonerId, prisonerBalance)
    // When
    val responseSpec = callGetSessions(prisonCode, convictedPrisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResultForSessionDate = getResults(returnResult).first { it.sessionTemplateReference == sessionTemplate.reference && it.startTimestamp.toLocalDate() == sessionDate }
    assertThat(visitSessionResultForSessionDate).isNotNull
    assertThat(visitSessionResultForSessionDate.sessionConflicts).hasSize(1)
    assertThat(visitSessionResultForSessionDate.sessionConflicts.map { it.sessionConflict }).containsOnly(SessionConflict.NO_PVO_BALANCE)
    verify(visitAllocationApiClientSpy, times(1)).getPrisonerVOBalance(convictedPrisonerId)
  }

  @Test
  fun `when prisoner is convicted and session restriction is PVO only then no conflict is added if PVO balance is available`() {
    // Given
    val today = LocalDate.now()
    val sessionDate = firstBookableMonday()
    val sessionTemplate = sessionTemplateEntityHelper.create(visitOrderRestrictionType = SessionTemplateVisitOrderRestrictionType.PVO, prisonCode = prisonCode, weeklyFrequency = 1, validFromDate = today.minusMonths(1), dayOfWeek = DayOfWeek.MONDAY)
    val prisonerBalance = VisitOrderPrisonerBalanceDto(prisonerId = convictedPrisonerId, voBalance = 0, pvoBalance = 3)
    visitAllocationApiMockServer.stubGetPrisonerVOBalance(prisonerId = convictedPrisonerId, prisonerBalance)
    // When
    val responseSpec = callGetSessions(prisonCode, convictedPrisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResultForSessionDate = getResults(returnResult).first { it.sessionTemplateReference == sessionTemplate.reference && it.startTimestamp.toLocalDate() == sessionDate }
    assertThat(visitSessionResultForSessionDate).isNotNull
    assertThat(visitSessionResultForSessionDate.sessionConflicts).isEmpty()
    verify(visitAllocationApiClientSpy, times(1)).getPrisonerVOBalance(convictedPrisonerId)
  }

  @Test
  fun `when prisoner is convicted and session restriction is VO or PVO then a NO_VO_OR_PVOS conflict is added if neither VO or PVO balance exists - zero balances`() {
    // Given
    val today = LocalDate.now()
    val sessionDate = firstBookableMonday()
    val sessionTemplate = sessionTemplateEntityHelper.create(visitOrderRestrictionType = SessionTemplateVisitOrderRestrictionType.VO_PVO, prisonCode = prisonCode, weeklyFrequency = 1, validFromDate = today.minusMonths(1), dayOfWeek = DayOfWeek.MONDAY)
    val prisonerBalance = VisitOrderPrisonerBalanceDto(prisonerId = convictedPrisonerId, voBalance = 0, pvoBalance = 0)
    visitAllocationApiMockServer.stubGetPrisonerVOBalance(prisonerId = convictedPrisonerId, prisonerBalance)
    // When
    val responseSpec = callGetSessions(prisonCode, convictedPrisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResultForSessionDate = getResults(returnResult).first { it.sessionTemplateReference == sessionTemplate.reference && it.startTimestamp.toLocalDate() == sessionDate }
    assertThat(visitSessionResultForSessionDate).isNotNull
    assertThat(visitSessionResultForSessionDate.sessionConflicts).hasSize(1)
    assertThat(visitSessionResultForSessionDate.sessionConflicts.map { it.sessionConflict }).containsOnly(SessionConflict.NO_VO_OR_PVO_BALANCE)
    verify(visitAllocationApiClientSpy, times(1)).getPrisonerVOBalance(convictedPrisonerId)
  }

  @Test
  fun `when prisoner is convicted and session restriction is VO or PVO then a NO_VO_OR_PVOS conflict is added if neither VO or PVO balance exists - negative balances`() {
    // Given
    val today = LocalDate.now()
    val sessionDate = firstBookableMonday()
    val sessionTemplate = sessionTemplateEntityHelper.create(visitOrderRestrictionType = SessionTemplateVisitOrderRestrictionType.VO_PVO, prisonCode = prisonCode, weeklyFrequency = 1, validFromDate = today.minusMonths(1), dayOfWeek = DayOfWeek.MONDAY)
    val prisonerBalance = VisitOrderPrisonerBalanceDto(prisonerId = convictedPrisonerId, voBalance = -1, pvoBalance = -1)
    visitAllocationApiMockServer.stubGetPrisonerVOBalance(prisonerId = convictedPrisonerId, prisonerBalance)
    // When
    val responseSpec = callGetSessions(prisonCode, convictedPrisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResultForSessionDate = getResults(returnResult).first { it.sessionTemplateReference == sessionTemplate.reference && it.startTimestamp.toLocalDate() == sessionDate }
    assertThat(visitSessionResultForSessionDate).isNotNull
    assertThat(visitSessionResultForSessionDate.sessionConflicts).hasSize(1)
    assertThat(visitSessionResultForSessionDate.sessionConflicts.map { it.sessionConflict }).containsOnly(SessionConflict.NO_VO_OR_PVO_BALANCE)
    verify(visitAllocationApiClientSpy, times(1)).getPrisonerVOBalance(convictedPrisonerId)
  }

  @Test
  fun `when prisoner is convicted and session restriction is VO or PVO then a NO_VO_OR_PVOS conflict is not added if VO balance exists`() {
    // Given
    val today = LocalDate.now()
    val sessionDate = firstBookableMonday()
    val sessionTemplate = sessionTemplateEntityHelper.create(visitOrderRestrictionType = SessionTemplateVisitOrderRestrictionType.VO_PVO, prisonCode = prisonCode, weeklyFrequency = 1, validFromDate = today.minusMonths(1), dayOfWeek = DayOfWeek.MONDAY)
    val prisonerBalance = VisitOrderPrisonerBalanceDto(prisonerId = convictedPrisonerId, voBalance = 1, pvoBalance = 0)
    visitAllocationApiMockServer.stubGetPrisonerVOBalance(prisonerId = convictedPrisonerId, prisonerBalance)
    // When
    val responseSpec = callGetSessions(prisonCode, convictedPrisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResultForSessionDate = getResults(returnResult).first { it.sessionTemplateReference == sessionTemplate.reference && it.startTimestamp.toLocalDate() == sessionDate }
    assertThat(visitSessionResultForSessionDate).isNotNull
    assertThat(visitSessionResultForSessionDate.sessionConflicts).isEmpty()
    verify(visitAllocationApiClientSpy, times(1)).getPrisonerVOBalance(convictedPrisonerId)
  }

  @Test
  fun `when prisoner is convicted and session restriction is VO or PVO then a NO_VO_OR_PVOS conflict is not added if PVO balance exists`() {
    // Given
    val today = LocalDate.now()
    val sessionDate = firstBookableMonday()
    val sessionTemplate = sessionTemplateEntityHelper.create(visitOrderRestrictionType = SessionTemplateVisitOrderRestrictionType.VO_PVO, prisonCode = prisonCode, weeklyFrequency = 1, validFromDate = today.minusMonths(1), dayOfWeek = DayOfWeek.MONDAY)
    val prisonerBalance = VisitOrderPrisonerBalanceDto(prisonerId = convictedPrisonerId, voBalance = -2, pvoBalance = 1)
    visitAllocationApiMockServer.stubGetPrisonerVOBalance(prisonerId = convictedPrisonerId, prisonerBalance)
    // When
    val responseSpec = callGetSessions(prisonCode, convictedPrisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResultForSessionDate = getResults(returnResult).first { it.sessionTemplateReference == sessionTemplate.reference && it.startTimestamp.toLocalDate() == sessionDate }
    assertThat(visitSessionResultForSessionDate).isNotNull
    assertThat(visitSessionResultForSessionDate.sessionConflicts).isEmpty()
    verify(visitAllocationApiClientSpy, times(1)).getPrisonerVOBalance(convictedPrisonerId)
  }

  @Test
  fun `when prisoner is convicted and session restriction is NONE then no session conflict is added even if no VO or PVO balance exists - negative balances`() {
    // Given
    val today = LocalDate.now()
    val sessionDate = firstBookableMonday()
    val sessionTemplate = sessionTemplateEntityHelper.create(visitOrderRestrictionType = SessionTemplateVisitOrderRestrictionType.NONE, prisonCode = prisonCode, weeklyFrequency = 1, validFromDate = today.minusMonths(1), dayOfWeek = DayOfWeek.MONDAY)
    val prisonerBalance = VisitOrderPrisonerBalanceDto(prisonerId = convictedPrisonerId, voBalance = -2, pvoBalance = -4)
    visitAllocationApiMockServer.stubGetPrisonerVOBalance(prisonerId = convictedPrisonerId, prisonerBalance)
    // When
    val responseSpec = callGetSessions(prisonCode, convictedPrisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResultForSessionDate = getResults(returnResult).first { it.sessionTemplateReference == sessionTemplate.reference && it.startTimestamp.toLocalDate() == sessionDate }
    assertThat(visitSessionResultForSessionDate).isNotNull
    assertThat(visitSessionResultForSessionDate.sessionConflicts).isEmpty()
    verify(visitAllocationApiClientSpy, times(1)).getPrisonerVOBalance(convictedPrisonerId)
  }

  @Test
  fun `when prisoner is convicted and session restriction is NONE then no session conflict is added even if no VO or PVO balance exists - zero balances`() {
    // Given
    val today = LocalDate.now()
    val sessionDate = firstBookableMonday()
    val sessionTemplate = sessionTemplateEntityHelper.create(visitOrderRestrictionType = SessionTemplateVisitOrderRestrictionType.NONE, prisonCode = prisonCode, weeklyFrequency = 1, validFromDate = today.minusMonths(1), dayOfWeek = DayOfWeek.MONDAY)
    val prisonerBalance = VisitOrderPrisonerBalanceDto(prisonerId = convictedPrisonerId, voBalance = 0, pvoBalance = 0)
    visitAllocationApiMockServer.stubGetPrisonerVOBalance(prisonerId = convictedPrisonerId, prisonerBalance)
    // When
    val responseSpec = callGetSessions(prisonCode, convictedPrisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResultForSessionDate = getResults(returnResult).first { it.sessionTemplateReference == sessionTemplate.reference && it.startTimestamp.toLocalDate() == sessionDate }
    assertThat(visitSessionResultForSessionDate).isNotNull
    assertThat(visitSessionResultForSessionDate.sessionConflicts).isEmpty()
    verify(visitAllocationApiClientSpy, times(1)).getPrisonerVOBalance(convictedPrisonerId)
  }

  @Test
  fun `when prisoner is convicted and call to visit allocation returns a null then no session conflicts are returned`() {
    // Given
    val today = LocalDate.now()
    val sessionDate = firstBookableMonday()
    val sessionTemplate = sessionTemplateEntityHelper.create(visitOrderRestrictionType = SessionTemplateVisitOrderRestrictionType.VO, prisonCode = prisonCode, weeklyFrequency = 1, validFromDate = today.minusMonths(1), dayOfWeek = DayOfWeek.MONDAY)
    visitAllocationApiMockServer.stubGetPrisonerVOBalance(prisonerId = convictedPrisonerId, null)

    // When
    val responseSpec = callGetSessions(prisonCode, convictedPrisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResultForSessionDate = getResults(returnResult).first { it.sessionTemplateReference == sessionTemplate.reference && it.startTimestamp.toLocalDate() == sessionDate }
    assertThat(visitSessionResultForSessionDate).isNotNull
    assertThat(visitSessionResultForSessionDate.sessionConflicts).isEmpty()
    verify(visitAllocationApiClientSpy, times(1)).getPrisonerVOBalance(convictedPrisonerId)
  }

  @Test
  fun `when prisoner is convicted and call to visit allocation fails with a NOT_FOUND error then no session conflicts are returned`() {
    // Given
    val today = LocalDate.now()
    val sessionDate = firstBookableMonday()
    val sessionTemplate = sessionTemplateEntityHelper.create(visitOrderRestrictionType = SessionTemplateVisitOrderRestrictionType.VO, prisonCode = prisonCode, weeklyFrequency = 1, validFromDate = today.minusMonths(1), dayOfWeek = DayOfWeek.MONDAY)
    visitAllocationApiMockServer.stubGetPrisonerVOBalance(prisonerId = convictedPrisonerId, null, HttpStatus.NOT_FOUND)

    // When
    val responseSpec = callGetSessions(prisonCode, convictedPrisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResultForSessionDate = getResults(returnResult).first { it.sessionTemplateReference == sessionTemplate.reference && it.startTimestamp.toLocalDate() == sessionDate }
    assertThat(visitSessionResultForSessionDate).isNotNull
    assertThat(visitSessionResultForSessionDate.sessionConflicts).isEmpty()
    verify(visitAllocationApiClientSpy, times(1)).getPrisonerVOBalance(convictedPrisonerId)
  }

  @Test
  fun `when prisoner is convicted and call to visit allocation fails with an INTERNAL_SERVER_ERROR error then no session conflicts are returned`() {
    // Given
    val today = LocalDate.now()
    sessionTemplateEntityHelper.create(visitOrderRestrictionType = SessionTemplateVisitOrderRestrictionType.VO, prisonCode = prisonCode, weeklyFrequency = 1, validFromDate = today.minusMonths(1), dayOfWeek = DayOfWeek.MONDAY)
    visitAllocationApiMockServer.stubGetPrisonerVOBalance(prisonerId = convictedPrisonerId, null, HttpStatus.INTERNAL_SERVER_ERROR)

    // When
    val responseSpec = callGetSessions(prisonCode, convictedPrisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    responseSpec.expectStatus().is5xxServerError
    verify(visitAllocationApiClientSpy, times(1)).getPrisonerVOBalance(convictedPrisonerId)
  }

  private fun getResults(returnResult: BodyContentSpec): Array<VisitSessionDto> = objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)
}
