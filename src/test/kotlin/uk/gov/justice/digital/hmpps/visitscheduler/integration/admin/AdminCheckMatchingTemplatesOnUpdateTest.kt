package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.FIND_MATCHING_SESSION_TEMPLATES_ON_CREATE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCheckingMatchingTemplatesOnUpdate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createUpdateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionCategoryGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.SessionIncentiveLevelGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Post $FIND_MATCHING_SESSION_TEMPLATES_ON_CREATE")
class AdminCheckMatchingTemplatesOnUpdateTest : IntegrationTestBase() {

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  private var prison: Prison = Prison(code = "MDI", active = true)

  private lateinit var sessionTemplateMondayToBeUpdated: SessionTemplate
  private lateinit var sessionTemplateMondayExisting: SessionTemplate

  private lateinit var sessionTemplateTuesdayWithNullValidToDate: SessionTemplate
  private lateinit var sessionTemplateWednesdayWithWeeklyFreqAs2: SessionTemplate
  private lateinit var sessionTemplateThursdayWithLevel1ALocations: SessionTemplate
  private lateinit var sessionTemplateThursdayWithLevel2Locations: SessionTemplate
  private lateinit var sessionTemplateThursdayWithLevel3Locations: SessionTemplate
  private lateinit var sessionTemplateThursdayWithLevel4Locations: SessionTemplate
  private lateinit var sessionTemplateThursdayWithDifferentLevelLocations: SessionTemplate
  private lateinit var sessionTemplateThursdayWithoutLocations: SessionTemplate
  private lateinit var sessionTemplateFridayCategoryA: SessionTemplate
  private lateinit var sessionTemplateFridayCategoryBCandD: SessionTemplate
  private lateinit var sessionTemplateSaturdayEnhanced: SessionTemplate
  private lateinit var sessionTemplateSaturdayPMLocationAndCategory: SessionTemplate
  private lateinit var sessionTemplateSaturdayPMLocationCategoryAndIncentive: SessionTemplate

  private lateinit var level1ALocations: SessionLocationGroup
  private lateinit var level2A1Locations: SessionLocationGroup
  private lateinit var level2A2Locations: SessionLocationGroup
  private lateinit var level3A12Locations: SessionLocationGroup
  private lateinit var level4A123Locations: SessionLocationGroup
  private lateinit var level1BLocations: SessionLocationGroup
  private lateinit var categoryAs: SessionCategoryGroup
  private lateinit var categoryBCandD: SessionCategoryGroup
  private lateinit var incentiveLevelEnhanced: SessionIncentiveLevelGroup
  private lateinit var incentiveLevelNonEnhanced: SessionIncentiveLevelGroup

  @BeforeEach
  internal fun setUpTests() {
    prison = prisonEntityHelper.create(prison.code, prison.active)

    sessionTemplateMondayToBeUpdated = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY, validToDate = LocalDate.now().plusYears(1))
    sessionTemplateMondayExisting = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY)
    /*sessionTemplateTuesdayWithNullValidToDate = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.TUESDAY, validToDate = null)
    sessionTemplateWednesdayWithWeeklyFreqAs2 = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.WEDNESDAY, validToDate = null, weeklyFrequency = 2)

    level1ALocations = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = listOf(AllowedSessionLocationHierarchy("A")))
    level2A1Locations = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = listOf(AllowedSessionLocationHierarchy("A", "1")))
    level2A2Locations = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = listOf(AllowedSessionLocationHierarchy("A", "2")))
    level3A12Locations = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = listOf(AllowedSessionLocationHierarchy("A", "1", "2")))
    level4A123Locations = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = listOf(AllowedSessionLocationHierarchy("A", "1", "2", "3")))
    level4A123Locations = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = listOf(AllowedSessionLocationHierarchy("A", "1", "2", "3")))
    level1BLocations = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = listOf(AllowedSessionLocationHierarchy("B")))

    categoryAs = sessionPrisonerCategoryHelper.create(prisonerCategories = listOf(PrisonerCategoryType.A_STANDARD, PrisonerCategoryType.A_EXCEPTIONAL, PrisonerCategoryType.A_HIGH, PrisonerCategoryType.A_PROVISIONAL))
    categoryBCandD = sessionPrisonerCategoryHelper.create(prisonerCategories = listOf(PrisonerCategoryType.B, PrisonerCategoryType.C, PrisonerCategoryType.D))
    incentiveLevelEnhanced = sessionPrisonerIncentiveLevelHelper.create(incentiveLevelList = listOf(IncentiveLevel.ENHANCED, IncentiveLevel.ENHANCED_2, IncentiveLevel.ENHANCED_3))
    incentiveLevelNonEnhanced = sessionPrisonerIncentiveLevelHelper.create(incentiveLevelList = listOf(IncentiveLevel.BASIC, IncentiveLevel.STANDARD))

    sessionTemplateThursdayWithLevel1ALocations = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.THURSDAY, permittedLocationGroups = mutableListOf(level1ALocations))
    sessionTemplateThursdayWithLevel2Locations = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.THURSDAY, permittedLocationGroups = mutableListOf(level2A1Locations))
    sessionTemplateThursdayWithLevel3Locations = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.THURSDAY, permittedLocationGroups = mutableListOf(level3A12Locations))
    sessionTemplateThursdayWithLevel4Locations = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.THURSDAY, permittedLocationGroups = mutableListOf(level4A123Locations))
    sessionTemplateThursdayWithDifferentLevelLocations = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.THURSDAY, permittedLocationGroups = mutableListOf(level1BLocations))
    sessionTemplateThursdayWithoutLocations = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.THURSDAY)

    sessionTemplateFridayCategoryA = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.FRIDAY, permittedCategories = mutableListOf(categoryAs))
    sessionTemplateFridayCategoryBCandD = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.FRIDAY, permittedCategories = mutableListOf(categoryBCandD))
    sessionTemplateSaturdayEnhanced = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.SATURDAY, permittedIncentiveLevels = mutableListOf(incentiveLevelEnhanced))

    sessionTemplateSaturdayPMLocationAndCategory = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.SATURDAY, permittedLocationGroups = mutableListOf(level1ALocations), permittedCategories = mutableListOf(categoryAs), startTime = LocalTime.of(15, 0), endTime = LocalTime.of(16, 0))
    sessionTemplateSaturdayPMLocationCategoryAndIncentive = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.SATURDAY, permittedLocationGroups = mutableListOf(level1ALocations), permittedCategories = mutableListOf(categoryAs), permittedIncentiveLevels = mutableListOf(incentiveLevelEnhanced), startTime = LocalTime.of(17, 0), endTime = LocalTime.of(18, 0))*/
  }

  @Test
  fun `when updated session template has same details as a different existing session template references are returned`() {
    // Given
    val dto = createUpdateSessionTemplateDto(SessionTemplateDto(sessionTemplateMondayToBeUpdated))
    val reference = sessionTemplateMondayToBeUpdated.reference

    // When
    val responseSpec = callCheckingMatchingTemplatesOnUpdate(webTestClient, reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(1)
    Assertions.assertThat(matchingReferences[0]).isEqualTo(sessionTemplateMondayExisting.reference)
  }

  @Test
  fun `when updated session template does not have same details as an existing session template references are returned`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateMondayToBeUpdated),
      SessionTimeSlotDto(LocalTime.of(13, 0), LocalTime.of(14, 0)),
    )
    val reference = sessionTemplateMondayToBeUpdated.reference

    // When
    val responseSpec = callCheckingMatchingTemplatesOnUpdate(webTestClient, reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(0)
  }
}
