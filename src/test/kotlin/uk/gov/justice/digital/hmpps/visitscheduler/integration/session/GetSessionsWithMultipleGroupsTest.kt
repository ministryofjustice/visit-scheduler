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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerCategoryType.A_EXCEPTIONAL
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerCategoryType.A_HIGH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerCategoryType.A_PROVISIONAL
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerCategoryType.A_STANDARD
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerCategoryType.B
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate

@DisplayName("GET $VISIT_SESSION_CONTROLLER_PATH where sessions are restricted by multiple groups (included / excluded)")
class GetSessionsWithMultipleGroupsTest : IntegrationTestBase() {

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
  fun `when session is restricted by included location, included category and included incentive then prisoner who is in the included location, category and incentive is allowed on the session`() {
    // Given
    val prisonerId = "A1234AA"
    val locationGroupName = "C Location"
    val enhancedIncentiveLevelGroupName = "ENH Incentive Level Group"
    val categoryAGroupName = "Category A"
    val allowedLocations = listOf(AllowedSessionLocationHierarchy("C"))
    val incentiveLevelList = listOf(ENHANCED)
    val categoryAList = listOf(A_HIGH, A_PROVISIONAL, A_EXCEPTIONAL, A_STANDARD)

    val locationGroup = sessionLocationGroupHelper.create(
      name = locationGroupName,
      prisonCode = prisonCode,
      prisonHierarchies = allowedLocations,
    )

    val incentiveLevelGroup = sessionPrisonerIncentiveLevelHelper.create(enhancedIncentiveLevelGroupName, prisonCode, incentiveLevelList)

    val categoryAGroup = sessionPrisonerCategoryHelper.create(name = categoryAGroupName, prisonerCategories = categoryAList)

    // prisoner is ENHANCED and CATEGORY A HIGH
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, incentiveLevelCode = ENHANCED, category = A_HIGH.code)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)

    // prisoner is in C location
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")

    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate = sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      dayOfWeek = nextAllowedDay.dayOfWeek,
      permittedLocationGroups = mutableListOf(locationGroup),
      includeLocationGroupType = true,
      permittedCategories = mutableListOf(categoryAGroup),
      includeCategoryGroupType = true,
      permittedIncentiveLevels = mutableListOf(incentiveLevelGroup),
      includeIncentiveGroupType = true,
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
  fun `when session is restricted by included location, included category and included incentive when prisoner is in different location then he is not allowed on the session`() {
    // Given
    val prisonerId = "A1234AA"
    val locationGroupName = "C Location"
    val enhancedIncentiveLevelGroupName = "ENH Incentive Level Group"
    val categoryAGroupName = "Category A"
    val allowedLocations = listOf(AllowedSessionLocationHierarchy("C"))
    val incentiveLevelList = listOf(ENHANCED)
    val categoryAList = listOf(A_HIGH, A_PROVISIONAL, A_EXCEPTIONAL, A_STANDARD)

    val locationGroup = sessionLocationGroupHelper.create(
      name = locationGroupName,
      prisonCode = prisonCode,
      prisonHierarchies = allowedLocations,
    )

    val incentiveLevelGroup = sessionPrisonerIncentiveLevelHelper.create(enhancedIncentiveLevelGroupName, prisonCode, incentiveLevelList)

    val categoryAGroup = sessionPrisonerCategoryHelper.create(name = categoryAGroupName, prisonerCategories = categoryAList)

    // prisoner is ENHANCED and CATEGORY A HIGH
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, incentiveLevelCode = ENHANCED, category = A_HIGH.code)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)

    // prisoner is in D location and hence the session shouldn't be available
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-D-1-C001")

    val nextAllowedDay = getNextAllowedDay()

    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      dayOfWeek = nextAllowedDay.dayOfWeek,
      permittedLocationGroups = mutableListOf(locationGroup),
      includeLocationGroupType = true,
      permittedCategories = mutableListOf(categoryAGroup),
      includeCategoryGroupType = true,
      permittedIncentiveLevels = mutableListOf(incentiveLevelGroup),
      includeIncentiveGroupType = true,
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(0)
  }

  @Test
  fun `when session is restricted by included location, included category and included incentive when prisoner has a different category then he is not allowed on the session`() {
    // Given
    val prisonerId = "A1234AA"
    val locationGroupName = "C Location"
    val enhancedIncentiveLevelGroupName = "ENH Incentive Level Group"
    val categoryAGroupName = "Category A"
    val allowedLocations = listOf(AllowedSessionLocationHierarchy("C"))
    val incentiveLevelList = listOf(ENHANCED)
    val categoryAList = listOf(A_HIGH, A_PROVISIONAL, A_EXCEPTIONAL, A_STANDARD)

    val locationGroup = sessionLocationGroupHelper.create(
      name = locationGroupName,
      prisonCode = prisonCode,
      prisonHierarchies = allowedLocations,
    )

    val incentiveLevelGroup = sessionPrisonerIncentiveLevelHelper.create(enhancedIncentiveLevelGroupName, prisonCode, incentiveLevelList)

    val categoryAGroup = sessionPrisonerCategoryHelper.create(name = categoryAGroupName, prisonerCategories = categoryAList)

    // prisoner is ENHANCED
    // prisoner is Category B - hence not allowed on the session
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, incentiveLevelCode = ENHANCED, category = B.code)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)

    // prisoner is in C location
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")

    val nextAllowedDay = getNextAllowedDay()

    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      dayOfWeek = nextAllowedDay.dayOfWeek,
      permittedLocationGroups = mutableListOf(locationGroup),
      includeLocationGroupType = true,
      permittedCategories = mutableListOf(categoryAGroup),
      includeCategoryGroupType = true,
      permittedIncentiveLevels = mutableListOf(incentiveLevelGroup),
      includeIncentiveGroupType = true,
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(0)
  }

  @Test
  fun `when session is restricted by included location, included category and included incentive when prisoner has a different incentive then he is not allowed on the session`() {
    // Given
    val prisonerId = "A1234AA"
    val locationGroupName = "C Location"
    val enhancedIncentiveLevelGroupName = "ENH Incentive Level Group"
    val categoryAGroupName = "Category A"
    val allowedLocations = listOf(AllowedSessionLocationHierarchy("C"))
    val incentiveLevelList = listOf(ENHANCED)
    val categoryAList = listOf(A_HIGH, A_PROVISIONAL, A_EXCEPTIONAL, A_STANDARD)

    val locationGroup = sessionLocationGroupHelper.create(
      name = locationGroupName,
      prisonCode = prisonCode,
      prisonHierarchies = allowedLocations,
    )

    val incentiveLevelGroup = sessionPrisonerIncentiveLevelHelper.create(enhancedIncentiveLevelGroupName, prisonCode, incentiveLevelList)

    val categoryAGroup = sessionPrisonerCategoryHelper.create(name = categoryAGroupName, prisonerCategories = categoryAList)

    // prisoner has STANDARD incentive level
    // prisoner has CATEGORY A HIGH
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, incentiveLevelCode = STANDARD, category = A_HIGH.code)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)

    // prisoner is in C location
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")

    val nextAllowedDay = getNextAllowedDay()

    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      dayOfWeek = nextAllowedDay.dayOfWeek,
      permittedLocationGroups = mutableListOf(locationGroup),
      includeLocationGroupType = true,
      permittedCategories = mutableListOf(categoryAGroup),
      includeCategoryGroupType = true,
      permittedIncentiveLevels = mutableListOf(incentiveLevelGroup),
      includeIncentiveGroupType = true,
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(0)
  }

  @Test
  fun `when session is restricted by excluded location, included category and included incentive when prisoner in in the excluded location then he is not allowed on the session`() {
    // Given
    val prisonerId = "A1234AA"
    val locationGroupName = "C Location"
    val enhancedIncentiveLevelGroupName = "ENH Incentive Level Group"
    val categoryAGroupName = "Category A"
    val allowedLocations = listOf(AllowedSessionLocationHierarchy("C"))
    val incentiveLevelList = listOf(ENHANCED)
    val categoryAList = listOf(A_HIGH, A_PROVISIONAL, A_EXCEPTIONAL, A_STANDARD)

    val locationGroup = sessionLocationGroupHelper.create(
      name = locationGroupName,
      prisonCode = prisonCode,
      prisonHierarchies = allowedLocations,
    )

    val incentiveLevelGroup = sessionPrisonerIncentiveLevelHelper.create(enhancedIncentiveLevelGroupName, prisonCode, incentiveLevelList)

    val categoryAGroup = sessionPrisonerCategoryHelper.create(name = categoryAGroupName, prisonerCategories = categoryAList)

    // prisoner has STANDARD incentive level
    // prisoner has CATEGORY A HIGH
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, incentiveLevelCode = STANDARD, category = A_HIGH.code)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)

    // prisoner is in C location but this location is excluded
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")

    val nextAllowedDay = getNextAllowedDay()

    // session excludes location C
    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      dayOfWeek = nextAllowedDay.dayOfWeek,
      permittedLocationGroups = mutableListOf(locationGroup),
      includeLocationGroupType = false,
      permittedCategories = mutableListOf(categoryAGroup),
      includeCategoryGroupType = true,
      permittedIncentiveLevels = mutableListOf(incentiveLevelGroup),
      includeIncentiveGroupType = true,
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(0)
  }

  @Test
  fun `when session is restricted by included location, excluded category and included incentive when prisoner has the excluded category then he is not allowed on the session`() {
    // Given
    val prisonerId = "A1234AA"
    val locationGroupName = "C Location"
    val enhancedIncentiveLevelGroupName = "ENH Incentive Level Group"
    val categoryAGroupName = "Category A"
    val allowedLocations = listOf(AllowedSessionLocationHierarchy("C"))
    val incentiveLevelList = listOf(ENHANCED)
    val categoryAList = listOf(A_HIGH, A_PROVISIONAL, A_EXCEPTIONAL, A_STANDARD)

    val locationGroup = sessionLocationGroupHelper.create(
      name = locationGroupName,
      prisonCode = prisonCode,
      prisonHierarchies = allowedLocations,
    )

    val incentiveLevelGroup = sessionPrisonerIncentiveLevelHelper.create(enhancedIncentiveLevelGroupName, prisonCode, incentiveLevelList)

    val categoryAGroup = sessionPrisonerCategoryHelper.create(name = categoryAGroupName, prisonerCategories = categoryAList)

    // prisoner has STANDARD incentive level
    // prisoner has CATEGORY B - hence not allowed on this session
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, incentiveLevelCode = STANDARD, category = B.code)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)

    // prisoner is in C location but this location is excluded
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")

    val nextAllowedDay = getNextAllowedDay()

    // session excludes Category A
    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      dayOfWeek = nextAllowedDay.dayOfWeek,
      permittedLocationGroups = mutableListOf(locationGroup),
      includeLocationGroupType = true,
      permittedCategories = mutableListOf(categoryAGroup),
      includeCategoryGroupType = false,
      permittedIncentiveLevels = mutableListOf(incentiveLevelGroup),
      includeIncentiveGroupType = true,
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(0)
  }

  @Test
  fun `when session is restricted by included location, included category and excluded incentive when prisoner has the excluded incentive level then he is not allowed on the session`() {
    // Given
    val prisonerId = "A1234AA"
    val locationGroupName = "C Location"
    val enhancedIncentiveLevelGroupName = "ENH Incentive Level Group"
    val categoryAGroupName = "Category A"
    val allowedLocations = listOf(AllowedSessionLocationHierarchy("C"))
    val incentiveLevelList = listOf(ENHANCED)
    val categoryAList = listOf(A_HIGH, A_PROVISIONAL, A_EXCEPTIONAL, A_STANDARD)

    val locationGroup = sessionLocationGroupHelper.create(
      name = locationGroupName,
      prisonCode = prisonCode,
      prisonHierarchies = allowedLocations,
    )

    val incentiveLevelGroup = sessionPrisonerIncentiveLevelHelper.create(enhancedIncentiveLevelGroupName, prisonCode, incentiveLevelList)

    val categoryAGroup = sessionPrisonerCategoryHelper.create(name = categoryAGroupName, prisonerCategories = categoryAList)

    // prisoner has ENHANCED incentive level - hence not allowed on this session
    // prisoner has CATEGORY A_HIGH
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, incentiveLevelCode = ENHANCED, category = A_HIGH.code)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)

    // prisoner is in C location but this location is excluded
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-C-1-C001")

    val nextAllowedDay = getNextAllowedDay()

    // session excludes ENHANCED incentive levels
    sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      dayOfWeek = nextAllowedDay.dayOfWeek,
      permittedLocationGroups = mutableListOf(locationGroup),
      includeLocationGroupType = true,
      permittedCategories = mutableListOf(categoryAGroup),
      includeCategoryGroupType = true,
      permittedIncentiveLevels = mutableListOf(incentiveLevelGroup),
      includeIncentiveGroupType = false,
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(0)
  }

  @Test
  fun `when session is restricted by excluded location, excluded category and excluded incentive and prisoner is not in any of the excluded location, category or incentive then he is allowed on the session`() {
    // Given
    val prisonerId = "A1234AA"
    val locationGroupName = "C Location"
    val enhancedIncentiveLevelGroupName = "ENH Incentive Level Group"
    val categoryAGroupName = "Category A"
    val allowedLocations = listOf(AllowedSessionLocationHierarchy("C"))
    val incentiveLevelList = listOf(ENHANCED)
    val categoryAList = listOf(A_HIGH, A_PROVISIONAL, A_EXCEPTIONAL, A_STANDARD)

    val locationGroup = sessionLocationGroupHelper.create(
      name = locationGroupName,
      prisonCode = prisonCode,
      prisonHierarchies = allowedLocations,
    )

    val incentiveLevelGroup = sessionPrisonerIncentiveLevelHelper.create(enhancedIncentiveLevelGroupName, prisonCode, incentiveLevelList)

    val categoryAGroup = sessionPrisonerCategoryHelper.create(name = categoryAGroupName, prisonerCategories = categoryAList)

    // prisoner has STANDARD incentive level
    // prisoner has CATEGORY B
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, incentiveLevelCode = STANDARD, category = B.code)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)

    // prisoner is in D location
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "${prison.code}-D-1-C001")

    val nextAllowedDay = getNextAllowedDay()

    // session is not available to ENHANCED, category A or D location
    val sessionTemplate = sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      dayOfWeek = nextAllowedDay.dayOfWeek,
      permittedLocationGroups = mutableListOf(locationGroup),
      includeLocationGroupType = false,
      permittedCategories = mutableListOf(categoryAGroup),
      includeCategoryGroupType = false,
      permittedIncentiveLevels = mutableListOf(incentiveLevelGroup),
      includeIncentiveGroupType = false,
    )

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate)
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
