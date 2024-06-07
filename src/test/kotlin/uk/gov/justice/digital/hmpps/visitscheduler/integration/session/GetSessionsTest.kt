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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

@DisplayName("Get /visit-sessions")
class GetSessionsTest : IntegrationTestBase() {

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  private val prisonerId = "A0000001"

  private val prisonCode = "STC"

  @BeforeEach
  internal fun setUpTests() {
    prison = prisonEntityHelper.create(prisonCode = prisonCode)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
  }

  @Test
  fun `visit sessions are returned for a prison for a single schedule`() {
    // Given
    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate)
  }

  @Test
  fun `visit sessions are returned using prison booking min and max days`() {
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
    val responseSpec = callGetSessions(prisonId = prisonCode, prisonerId)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], now.plusWeeks(1), sessionTemplate)
  }

  @Test
  fun `visit sessions are returned for a prison without excluded dates`() {
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
    val responseSpec = callGetSessions(prisonId = "AWE", prisonerId)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate)
  }

  @Test
  fun `only active visit sessions are returned for a prison`() {
    // Given

    val nextAllowedDay = getNextAllowedDay()

    // active session 1
    sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
    )

    // active session 2
    sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("11:00"),
      endTime = LocalTime.parse("12:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
    )

    // active session 3
    sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("12:00"),
      endTime = LocalTime.parse("13:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prisonCode,
    )

    // inactive session
    val sessionTemplateInactive = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("13:00"),
      endTime = LocalTime.parse("14:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      isActive = false,
      prisonCode = prisonCode,
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSessionResults = getResults(returnResult)
    val visitSessionResultsReferences = visitSessionResults.map { it.sessionTemplateReference }.toList()
    assertThat(visitSessionResults.size).isEqualTo(3)
    assertThat(visitSessionResultsReferences).doesNotContain(sessionTemplateInactive.reference)
  }

  @Test
  fun `visit sessions are returned for enhanced prisoner when prison has enhanced schedule`() {
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
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate)
  }

  @Test
  fun `non enhanced visit sessions are returned for a prisoner with any incentive level`() {
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

    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
  }

  @Test
  fun `non enhanced visit sessions are returned for a prisoner with null incentive level`() {
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

    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

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
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

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
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

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
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

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
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

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
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

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
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

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
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(0)
  }

  @Test
  fun `bi weekly schedule - test for sunday change boundary`() {
    val today = LocalDate.now()
    val todayIsTheWeekEnd = today.dayOfWeek in listOf(SUNDAY, SATURDAY)

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
    val responseSpec = callGetSessions(prisonCode, prisonerId)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()

    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isGreaterThan(2)
    if (todayIsTheWeekEnd) {
      // On the weekend it skips to the other session template / schedule because we cannot book with in 24 hrs
      assertThat(visitSessionResults[0].visitRoom).isEqualTo("Alternate 2")
      assertThat(visitSessionResults[1].visitRoom).isEqualTo("Alternate 1")
    } else {
      assertThat(visitSessionResults[0].visitRoom).isEqualTo("Alternate 1")
      assertThat(visitSessionResults[1].visitRoom).isEqualTo("Alternate 2")
    }
  }

  @Test
  fun `Weekly schedule - test`() {
    val today = LocalDate.now()
    val policyNoticeDaysMin = 0
    val policyNoticeDaysMax = 14
    val prisonCode = "TST"

    prison = prisonEntityHelper.create(prisonCode = prisonCode, policyNoticeDaysMin = policyNoticeDaysMin, policyNoticeDaysMax = policyNoticeDaysMax)

    // Given
    val startFromWeek1 = getStartOfWeek(today)
    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = startFromWeek1,
      dayOfWeek = today.dayOfWeek,
      weeklyFrequency = 1,
    )
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, policyNoticeDaysMin = policyNoticeDaysMin, policyNoticeDaysMax = policyNoticeDaysMax)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()

    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(3)
    assertThat(visitSessionResults[0].startTimestamp.toLocalDate()).isEqualTo(today)
    assertThat(visitSessionResults[1].startTimestamp.toLocalDate()).isEqualTo(today.plusWeeks(1))
    assertThat(visitSessionResults[2].startTimestamp.toLocalDate()).isEqualTo(today.plusWeeks(2))
  }

  @Test
  fun `bi weekly schedule - test`() {
    val today = LocalDate.now()
    val policyNoticeDaysMin = 0
    val policyNoticeDaysMax = 28
    val prisonCode = "TST"

    prison = prisonEntityHelper.create(prisonCode = prisonCode, policyNoticeDaysMin = policyNoticeDaysMin, policyNoticeDaysMax = policyNoticeDaysMax)
    // Given
    val startFromWeek1 = getStartOfWeek(today)
    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = startFromWeek1,
      dayOfWeek = today.dayOfWeek,
      weeklyFrequency = 2,
    )

    // When
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    val responseSpec = callGetSessions(prisonCode = prisonCode, prisonerId = prisonerId, policyNoticeDaysMin = policyNoticeDaysMin, policyNoticeDaysMax = policyNoticeDaysMax)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()

    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(3)
    assertThat(visitSessionResults[0].startTimestamp.toLocalDate()).isEqualTo(today)
    assertThat(visitSessionResults[1].startTimestamp.toLocalDate()).isEqualTo(today.plusWeeks(2))
    assertThat(visitSessionResults[2].startTimestamp.toLocalDate()).isEqualTo(today.plusWeeks(4))
  }

  @Test
  fun `three weekly schedule - test`() {
    val today = LocalDate.now()
    val policyNoticeDaysMin = 0
    val policyNoticeDaysMax = 42
    val prisonCode = "TST"
    // Given
    prison = prisonEntityHelper.create(prisonCode = prisonCode, policyNoticeDaysMin = policyNoticeDaysMin, policyNoticeDaysMax = policyNoticeDaysMax)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)

    val startFromWeek1 = getStartOfWeek(today)
    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = startFromWeek1,
      dayOfWeek = today.dayOfWeek,
      weeklyFrequency = 3,
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, policyNoticeDaysMin = policyNoticeDaysMin, policyNoticeDaysMax = policyNoticeDaysMax)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()

    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(3)
    assertThat(visitSessionResults[0].startTimestamp.toLocalDate()).isEqualTo(today)
    assertThat(visitSessionResults[1].startTimestamp.toLocalDate()).isEqualTo(today.plusWeeks(3))
    assertThat(visitSessionResults[2].startTimestamp.toLocalDate()).isEqualTo(today.plusWeeks(6))
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
    val responseSpec = callGetSessions(prisonCode, prisonerId, 0, 28)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()

    val visitSessionResults = getResults(returnResult)

    assertThat(visitSessionResults.size).isEqualTo(8)
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
      endTime = LocalTime.parse("10:00"),
      prisonCode = prisonCode,
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()

    val visitSessionResults = getResults(returnResult)

    assertThat(visitSessionResults.size).isEqualTo(1)
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
      endTime = LocalTime.parse("10:00"),
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId)

    // Then
    assertNoResponse(responseSpec)
  }

  @Test
  fun `visit sessions are not returned when policy notice min days is greater than max without valid to date`() {
    // Given
    val policyNoticeDaysMin = 14
    val policyNoticeDaysMax = 1

    prison = prisonEntityHelper.create(prisonCode = "TST", policyNoticeDaysMin = policyNoticeDaysMin, policyNoticeDaysMax = policyNoticeDaysMax)
    val prisonCode = prison.code
    val nextAllowedDay = getNextAllowedDay()

    sessionTemplateEntityHelper.create(validFromDate = nextAllowedDay)

    // When
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    val responseSpec = callGetSessions(prisonCode, prisonerId, policyNoticeDaysMin, policyNoticeDaysMax)

    // Then
    assertNoResponse(responseSpec)
  }

  @Test
  fun `visit sessions are not returned when start date is after policy notice min and max days`() {
    // Given
    val policyNoticeDaysMin = 0
    val policyNoticeDaysMax = 1
    prison = prisonEntityHelper.create(prisonCode = "TST", policyNoticeDaysMin = policyNoticeDaysMin, policyNoticeDaysMax = policyNoticeDaysMax)
    val prisonCode = prison.code

    sessionTemplateEntityHelper.create(validFromDate = LocalDate.now().plusDays(2))

    // When
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    val responseSpec = callGetSessions(prisonCode, prisonerId, policyNoticeDaysMin, policyNoticeDaysMax)

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
    val responseSpec = callGetSessions(prisonCode, prisonerId, policyNoticeDaysMin, policyNoticeDaysMax)

    // Then
    assertNoResponse(responseSpec)
  }

  @Test
  fun `visit sessions that are no longer valid are not returned`() {
    // Given
    sessionTemplateEntityHelper.create(
      validFromDate = LocalDate.now().minusDays(1),
      validToDate = LocalDate.now().minusDays(1),
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId)

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
    val responseSpec = callGetSessions(prisonCode, prisonerId)

    // Then
    assertNoResponse(responseSpec)
  }

  @Test
  fun `visit sessions include reserved applications and booked open visit count`() {
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
      visitRestriction = OPEN,
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
      visitRestriction = OPEN,
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
      visitRestriction = OPEN,
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
      visitRestriction = OPEN,
      sessionTemplate = sessionTemplate,
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId)

    // Then
    assertBookCounts(responseSpec, openCount = 2, closeCount = 0)
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
      visitRestriction = OPEN,
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
      visitRestriction = CLOSED,
      sessionTemplate = sessionTemplate,
      reservedSlot = false,
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId)

    // Then
    assertBookCounts(responseSpec, openCount = 0, closeCount = 0)
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
      visitRestriction = CLOSED,
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
      visitRestriction = CLOSED,
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
      visitRestriction = CLOSED,
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
      visitRestriction = CLOSED,
      sessionTemplate = sessionTemplate,
    )

    // not included as it's a canceled visit
    this.visitEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = prisonCode,
      visitRoom = sessionTemplate.visitRoom,
      slotDate = dateTime.toLocalDate(),
      visitStart = dateTime.toLocalTime(),
      visitEnd = endTime.toLocalTime(),
      visitType = SOCIAL,
      visitStatus = CANCELLED,
      visitRestriction = CLOSED,
      sessionTemplate = sessionTemplate,
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId)

    // Then
    assertBookCounts(responseSpec, openCount = 0, closeCount = 2)
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
      visitRestriction = OPEN,
      sessionTemplate = sessionTemplate,
    )

    this.applicationEntityHelper.create(
      prisonerId = "AF12345G",
      prisonCode = prisonCode,
      slotDate = dateTime.toLocalDate(),
      visitStart = dateTime.toLocalTime(),
      visitEnd = endTime.toLocalTime(),
      visitType = SOCIAL,
      visitRestriction = CLOSED,
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
      visitRestriction = CLOSED,
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
      visitRestriction = CLOSED,
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
      visitRestriction = OPEN,
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
      visitRestriction = OPEN,
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
      visitRestriction = OPEN,
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
      visitRestriction = OPEN,
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
      visitRestriction = OPEN,
      sessionTemplate = sessionTemplate,
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId)

    // Then
    assertBookCounts(responseSpec, openCount = 5, closeCount = 1)
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
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

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
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

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
      visitRestriction = OPEN,
      sessionTemplate = sessionTemplate,
    )

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerId,
      associationId,
    )

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSessionDtos =
      objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)

    assertThat(visitSessionDtos).hasSize(4)
    assertThat(visitSessionDtos[0].sessionConflicts).hasSize(1)
    assertThat(visitSessionDtos[0].sessionConflicts).contains(SessionConflict.DOUBLE_RESERVATION)
    assertThat(visitSessionDtos[1].sessionConflicts).isEmpty()
    assertThat(visitSessionDtos[2].sessionConflicts).isEmpty()
    assertThat(visitSessionDtos[3].sessionConflicts).isEmpty()
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
      visitRestriction = OPEN,
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
      visitRestriction = OPEN,
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
      visitRestriction = OPEN,
      sessionTemplate = sessionTemplate,
    )

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerId,
      associationId,
    )

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSessionDtos =
      objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)

    assertThat(visitSessionDtos).hasSize(4)
    assertThat(visitSessionDtos[0].sessionConflicts).hasSize(1)
    assertThat(visitSessionDtos[0].sessionConflicts).contains(SessionConflict.DOUBLE_RESERVATION)
    assertThat(visitSessionDtos[1].sessionConflicts).hasSize(1)
    assertThat(visitSessionDtos[1].sessionConflicts).contains(SessionConflict.DOUBLE_RESERVATION)
    assertThat(visitSessionDtos[2].sessionConflicts).hasSize(1)
    assertThat(visitSessionDtos[2].sessionConflicts).contains(SessionConflict.DOUBLE_RESERVATION)
    assertThat(visitSessionDtos[3].sessionConflicts).isEmpty()
  }

  @Test
  fun `visit sessions are returned for a prisoner with an expired non-association with a booking`() {
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
      visitRestriction = OPEN,
      sessionTemplate = sessionTemplate,
    )

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerId,
      associationId,
    )

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)

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
      visitRestriction = OPEN,
      sessionTemplate = sessionTemplate,
    )

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerId,
      associationPrisonerId,
    )

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")

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
      visitRestriction = OPEN,
      sessionTemplate = sessionTemplate,
    )

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerId,
      associationPrisonerId,
    )

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)

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
      visitRestriction = OPEN,
      sessionTemplate = sessionTemplate,
    )

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerId,
      associationPrisonerId,
    )

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)

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
      visitRestriction = OPEN,
      sessionTemplate = sessionTemplate,
    )

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prison.code)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerId,
      associationPrisonerId,
    )
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    assertResponseLength(responseSpec, 4)
  }

  @Test
  fun `visit sessions are not returned for a prisoner with non association visit on same date but on a different session template`() {
    // Given
    // test for scenario where 2 session templates exist on the same day but
    // both sessions are for different wings

    val prisonerId = "A1234AA"
    val associationPrisonerId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()
    val allowedPermittedLocationsForSession1: List<AllowedSessionLocationHierarchy> = listOf(
      AllowedSessionLocationHierarchy("A", null, null, null),
    )
    val allowedPermittedLocationsForSession2: List<AllowedSessionLocationHierarchy> = listOf(
      AllowedSessionLocationHierarchy("B", null, null, null),
    )
    val location1 = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = allowedPermittedLocationsForSession1)
    val location2 = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = allowedPermittedLocationsForSession2)

    // session 1 is only available for prisoners in Wing A
    val sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      dayOfWeek = validFromDate.dayOfWeek,
      prisonCode = prisonCode,
      includeLocationGroupType = true,
      permittedLocationGroups = mutableListOf(location1),
    )

    // session 2 is only available for prisoners in Wing B
    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      dayOfWeek = validFromDate.dayOfWeek,
      prisonCode = prisonCode,
      includeLocationGroupType = true,
      permittedLocationGroups = mutableListOf(location2),
    )

    // visit booked for non association prisoner in A wing
    val visit = this.visitEntityHelper.create(
      prisonerId = associationPrisonerId,
      prisonCode = prisonCode,
      visitRoom = sessionTemplate1.visitRoom,
      slotDate = validFromDate,
      visitStart = LocalTime.of(9, 0),
      visitEnd = LocalTime.of(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN,
      sessionTemplate = sessionTemplate1,
    )

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerId,
      associationPrisonerId,
    )

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)

    // prisoner 1 is in B wing
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-B-1-C001")

    // non association prisoner is in A wing
    prisonApiMockServer.stubGetPrisonerHousingLocation(associationPrisonerId, "${prison.code}-A-1-C001")

    // When
    val responseResult = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange().expectBody()

    // Then
    val sessions = getResults(responseResult)

    assertThat(sessions.map { it.startTimestamp.toLocalDate() }).doesNotContain(visit.sessionSlot.slotDate)
    assertThat(sessions.size).isEqualTo(3)
  }

  @Test
  fun `visit sessions are not returned for a prisoner with non association application on same date but on a different session template`() {
    // Given
    // test for scenario where 2 session templates exist on the same day but
    // both sessions are for different wings

    val prisonerId = "A1234AA"
    val associationPrisonerId = "B1234BB"
    val validFromDate = this.getNextAllowedDay()
    val allowedPermittedLocationsForSession1: List<AllowedSessionLocationHierarchy> = listOf(
      AllowedSessionLocationHierarchy("A", null, null, null),
    )
    val allowedPermittedLocationsForSession2: List<AllowedSessionLocationHierarchy> = listOf(
      AllowedSessionLocationHierarchy("B", null, null, null),
    )
    val location1 = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = allowedPermittedLocationsForSession1)
    val location2 = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = allowedPermittedLocationsForSession2)

    // session 1 is only available for prisoners in Wing A
    val sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      dayOfWeek = validFromDate.dayOfWeek,
      prisonCode = prisonCode,
      includeLocationGroupType = true,
      permittedLocationGroups = mutableListOf(location1),
    )

    // session 2 is only available for prisoners in Wing B
    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      dayOfWeek = validFromDate.dayOfWeek,
      prisonCode = prisonCode,
      includeLocationGroupType = true,
      permittedLocationGroups = mutableListOf(location2),
    )

    // application reserved for non association prisoner in A wing
    val application = this.applicationEntityHelper.create(
      prisonerId = associationPrisonerId,
      prisonCode = prisonCode,
      slotDate = validFromDate,
      visitStart = LocalTime.of(9, 0),
      visitEnd = LocalTime.of(9, 30),
      visitType = SOCIAL,
      visitRestriction = OPEN,
      sessionTemplate = sessionTemplate1,
      reservedSlot = true,
      completed = false,
    )

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerId,
      associationPrisonerId,
    )

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)

    // prisoner 1 is in B wing
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-B-1-C001")

    // non association prisoner is in A wing
    prisonApiMockServer.stubGetPrisonerHousingLocation(associationPrisonerId, "${prison.code}-A-1-C001")

    // When
    val responseResult = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange().expectBody()

    // Then
    val sessions = getResults(responseResult)

    assertThat(sessions.map { it.startTimestamp.toLocalDate() }).doesNotContain(application.sessionSlot.slotDate)
    assertThat(sessions.size).isEqualTo(3)
  }

  @Test
  fun `visit sessions  are returned for a prisoner with non association visit on same date but in a different prison`() {
    // Given
    // test for scenario where 2 session templates exist on the same day but in different prisons

    val prisonerId = "A1234AA"
    val prisonerPrison = prisonCode
    val associationPrisonerId = "B1234BB"

    // non association in different prison
    val associationPrisonerPrison = "MDI"
    val validFromDate = this.getNextAllowedDay()

    val sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      dayOfWeek = validFromDate.dayOfWeek,
      prisonCode = associationPrisonerPrison,
    )

    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      dayOfWeek = validFromDate.dayOfWeek,
      prisonCode = prisonerPrison,
    )

    // visit booked for non association prisoner in different prison on same date
    val visit = this.visitEntityHelper.create(
      prisonerId = associationPrisonerId,
      prisonCode = associationPrisonerPrison,
      visitRoom = sessionTemplate1.visitRoom,
      slotDate = validFromDate,
      visitStart = LocalTime.of(9, 0),
      visitEnd = LocalTime.of(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN,
      sessionTemplate = sessionTemplate1,
    )

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerId,
      associationPrisonerId,
    )

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonerPrison)

    // prisoner 1 is in B wing
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonerPrison-B-1-C001")

    // non association prisoner is in A wing
    prisonApiMockServer.stubGetPrisonerHousingLocation(associationPrisonerId, "$associationPrisonerPrison-A-1-C001")

    // When
    val responseResult = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange().expectBody()

    // Then
    val sessions = getResults(responseResult)

    assertThat(sessions.map { it.startTimestamp.toLocalDate() }).contains(visit.sessionSlot.slotDate)
    assertThat(sessions.size).isEqualTo(4)
  }

  @Test
  fun `visit sessions are returned for a prisoner with non association application on same date but in a different prison`() {
    // Given
    // test for scenario where 2 session templates exist on the same day but in different prisons

    val prisonerId = "A1234AA"
    val prisonerPrison = prisonCode
    val associationPrisonerId = "B1234BB"

    // non association in different prison
    val associationPrisonerPrison = "MDI"
    val validFromDate = this.getNextAllowedDay()

    val sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      dayOfWeek = validFromDate.dayOfWeek,
      prisonCode = associationPrisonerPrison,
    )

    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      dayOfWeek = validFromDate.dayOfWeek,
      prisonCode = prisonerPrison,
    )

    // visit booked for non association prisoner in different prison on same date
    val application = this.applicationEntityHelper.create(
      prisonerId = associationPrisonerId,
      prisonCode = associationPrisonerPrison,
      slotDate = validFromDate,
      visitStart = LocalTime.of(9, 0),
      visitEnd = LocalTime.of(9, 30),
      visitType = SOCIAL,
      visitRestriction = OPEN,
      sessionTemplate = sessionTemplate1,
    )

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerId,
      associationPrisonerId,
    )

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonerPrison)

    // prisoner 1 is in B wing
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonerPrison-B-1-C001")

    // non association prisoner is in A wing
    prisonApiMockServer.stubGetPrisonerHousingLocation(associationPrisonerId, "$associationPrisonerPrison-A-1-C001")

    // When
    val responseResult = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange().expectBody()

    // Then
    val sessions = getResults(responseResult)

    assertThat(sessions.map { it.startTimestamp.toLocalDate() }).contains(application.sessionSlot.slotDate)
    assertThat(sessions.size).isEqualTo(4)
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
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$incorrectPrisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

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
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

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
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

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
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

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
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  private fun callGetSessions(
    prisonCode: String? = "SPC",
    prisonerId: String,
    policyNoticeDaysMin: Int,
    policyNoticeDaysMax: Int,
  ): ResponseSpec {
    return webTestClient.get().uri("/visit-sessions?prisonId=$prisonCode&prisonerId=$prisonerId&min=$policyNoticeDaysMin&max=$policyNoticeDaysMax")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()
  }

  private fun callGetSessions(prisonId: String? = "MDI", prisonerId: String): ResponseSpec {
    return webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()
  }

  private fun getNextAllowedDay(): LocalDate {
    // The 3 days is based on the default SessionService.policyNoticeDaysMin
    return LocalDate.now().plusDays(3)
  }

  private fun getStartOfWeek(today: LocalDate) =
    if (MONDAY == today.dayOfWeek) today else today.with(TemporalAdjusters.previous(MONDAY))

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
    assertThat(visitSessionResults.size).isEqualTo(resultSize)
    assertThat(visitSessionResults[0].openVisitBookedCount).isEqualTo(openCount)
    assertThat(visitSessionResults[0].closedVisitBookedCount).isEqualTo(closeCount)
    if (resultSize == 2) {
      assertThat(visitSessionResults[1].openVisitBookedCount).isEqualTo(openCount)
      assertThat(visitSessionResults[1].closedVisitBookedCount).isEqualTo(closeCount)
    }
  }

  private fun getResults(returnResult: BodyContentSpec): Array<VisitSessionDto> {
    return objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)
  }
}
