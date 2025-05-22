package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_SESSION_CONTROLLER_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel.ENHANCED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel.STANDARD
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("GET $VISIT_SESSION_CONTROLLER_PATH where sessions are restricted by incentive groups (included / excluded)")
class GetSessionsWithIncentivesTest : IntegrationTestBase() {

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  private val prisonerId = "A0000001"

  private val prisonCode = "STC"

  private lateinit var authHttpHeaders: (HttpHeaders) -> Unit

  @BeforeEach
  internal fun setUpTests() {
    authHttpHeaders = setAuthorisation(roles = requiredRole)
    prison = prisonEntityHelper.create(prisonCode = prisonCode)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
  }

  @Test
  fun `visit sessions are returned for enhanced prisoner when prison has enhanced schedule`() {
    // Given
    val prisonerId = "A1234AA"
    val enhancedIncentiveLevelGroup = "ENH Incentive Level Group"
    val incentiveLevelList = listOf(
      ENHANCED,
    )
    val incentiveLevelGroup = sessionPrisonerIncentiveLevelHelper.create(enhancedIncentiveLevelGroup, prisonCode, incentiveLevelList)

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, ENHANCED)
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
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

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

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, STANDARD)
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

    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

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

    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

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
      ENHANCED,
    )
    val incentiveLevelGroup = sessionPrisonerIncentiveLevelHelper.create(enhancedIncentiveLevelGroup, prisonCode, incentiveLevelList)

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, STANDARD)
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
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

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
      ENHANCED,
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
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(0)
  }

  private fun getNextAllowedDay(): LocalDate {
    // The 3 days is based on the default SessionService.policyNoticeDaysMin
    return LocalDate.now().plusDays(3)
  }

  private fun getResults(returnResult: BodyContentSpec): Array<VisitSessionDto> = objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)

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
}
