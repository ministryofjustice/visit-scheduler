package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.FIND_MATCHING_SESSION_TEMPLATES_ON_CREATE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionDateRangeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCheckingMatchingTemplatesOnCreate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createCreateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionCategoryGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.SessionIncentiveLevelGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Post $FIND_MATCHING_SESSION_TEMPLATES_ON_CREATE")
class AdminCheckMatchingTemplatesOnCreateTest : IntegrationTestBase() {

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  private var prison: Prison = Prison(code = "MDI", active = true)

  private lateinit var sessionTemplateMonday: SessionTemplate
  private lateinit var sessionTemplateMondayPM: SessionTemplate

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

    sessionTemplateMonday = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY, validToDate = LocalDate.now().plusYears(1))
    sessionTemplateMondayPM = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(15, 0), endTime = LocalTime.of(16, 0))
    sessionTemplateTuesdayWithNullValidToDate = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.TUESDAY, validToDate = null)
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
    sessionTemplateSaturdayPMLocationCategoryAndIncentive = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.SATURDAY, permittedLocationGroups = mutableListOf(level1ALocations), permittedCategories = mutableListOf(categoryAs), permittedIncentiveLevels = mutableListOf(incentiveLevelEnhanced), startTime = LocalTime.of(17, 0), endTime = LocalTime.of(18, 0))
  }

  @Test
  fun `when create session template has same details as existing session template references are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateMonday.validFromDate, sessionTemplateMonday.validToDate),
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateMonday.startTime, sessionTemplateMonday.endTime),
      dayOfWeek = DayOfWeek.MONDAY,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(1)
    Assertions.assertThat(matchingReferences[0]).isEqualTo(sessionTemplateMonday.reference)
  }

  @Test
  fun `when create session template has different dayofweek as existing session template references are not returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateMonday.validFromDate, sessionTemplateMonday.validToDate),
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateMonday.startTime, sessionTemplateMonday.endTime),
      dayOfWeek = DayOfWeek.SUNDAY,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(0)
  }

  @Test
  fun `when create session template starts before existing session template references are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateMonday.validFromDate.minusDays(1), sessionTemplateMonday.validToDate),
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateMonday.startTime, sessionTemplateMonday.endTime),
      dayOfWeek = DayOfWeek.MONDAY,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(1)
    Assertions.assertThat(matchingReferences[0]).isEqualTo(sessionTemplateMonday.reference)
  }

  @Test
  fun `when create session template starts after existing session template references are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateMonday.validFromDate.plusDays(1), sessionTemplateMonday.validToDate!!.minusDays(1)),
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateMonday.startTime, sessionTemplateMonday.endTime),
      dayOfWeek = DayOfWeek.MONDAY,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(1)
    Assertions.assertThat(matchingReferences[0]).isEqualTo(sessionTemplateMonday.reference)
  }

  @Test
  fun `when create session template starts after existing session template end date references are not returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateMonday.validToDate!!.plusDays(1), sessionTemplateMonday.validToDate!!.plusDays(1)),
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateMonday.startTime, sessionTemplateMonday.endTime),
      dayOfWeek = DayOfWeek.MONDAY,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(0)
  }

  @Test
  fun `when create session template starts before existing date but ends after existing session template dates then references are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateMonday.validFromDate.minusDays(14), sessionTemplateMonday.validToDate!!.plusDays(1)),
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateMonday.startTime, sessionTemplateMonday.endTime),
      dayOfWeek = DayOfWeek.MONDAY,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(1)
    Assertions.assertThat(matchingReferences).contains(sessionTemplateMonday.reference)
  }

  @Test
  fun `when create session template has same details as existing null valid to date session template references are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateTuesdayWithNullValidToDate.validFromDate, sessionTemplateTuesdayWithNullValidToDate.validToDate),
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateTuesdayWithNullValidToDate.startTime, sessionTemplateTuesdayWithNullValidToDate.endTime),
      dayOfWeek = DayOfWeek.TUESDAY,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(1)
    Assertions.assertThat(matchingReferences[0]).isEqualTo(sessionTemplateTuesdayWithNullValidToDate.reference)
  }

  @Test
  fun `when create session template starts before existing null valid to date session template references are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateTuesdayWithNullValidToDate.validFromDate.minusDays(1), sessionTemplateTuesdayWithNullValidToDate.validToDate),
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateTuesdayWithNullValidToDate.startTime, sessionTemplateTuesdayWithNullValidToDate.endTime),
      dayOfWeek = DayOfWeek.TUESDAY,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(1)
    Assertions.assertThat(matchingReferences[0]).isEqualTo(sessionTemplateTuesdayWithNullValidToDate.reference)
  }

  @Test
  fun `when create session template starts after existing null valid to date session template references are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateTuesdayWithNullValidToDate.validFromDate.plusDays(1), sessionTemplateTuesdayWithNullValidToDate.validFromDate.plusDays(1)),
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateTuesdayWithNullValidToDate.startTime, sessionTemplateTuesdayWithNullValidToDate.endTime),
      dayOfWeek = DayOfWeek.TUESDAY,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(1)
    Assertions.assertThat(matchingReferences[0]).isEqualTo(sessionTemplateTuesdayWithNullValidToDate.reference)
  }

  @Test
  fun `when create session template starts and ends before existing session template start date references are not returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateTuesdayWithNullValidToDate.validFromDate.minusDays(14), sessionTemplateTuesdayWithNullValidToDate.validFromDate.minusDays(1)),
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateTuesdayWithNullValidToDate.startTime, sessionTemplateTuesdayWithNullValidToDate.endTime),
      dayOfWeek = DayOfWeek.TUESDAY,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(0)
  }

  @Test
  fun `when create session template starts and ends after existing session template end date references are not returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateMonday.validToDate!!.plusDays(14), sessionTemplateMonday.validToDate!!.plusDays(31)),
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateMonday.startTime, sessionTemplateMonday.endTime),
      dayOfWeek = DayOfWeek.MONDAY,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(0)
  }

  @Test
  fun `when create session template has same time as existing session template references are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateMonday.startTime, sessionTemplateMonday.endTime),
      dayOfWeek = DayOfWeek.MONDAY,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(1)
    Assertions.assertThat(matchingReferences[0]).isEqualTo(sessionTemplateMonday.reference)
  }

  @Test
  fun `when create session template start time starts before but ends after existing session template start time then references are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateMonday.validFromDate.minusDays(1), sessionTemplateMonday.validToDate),
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateMonday.startTime.minusHours(1), sessionTemplateMonday.startTime),
      dayOfWeek = DayOfWeek.MONDAY,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(1)
    Assertions.assertThat(matchingReferences[0]).isEqualTo(sessionTemplateMonday.reference)
  }

  @Test
  fun `when create session template start time starts between existing session template references are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateMonday.validFromDate.plusDays(1), sessionTemplateMonday.validToDate!!.minusDays(1)),
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateMonday.startTime, sessionTemplateMonday.endTime),
      dayOfWeek = DayOfWeek.MONDAY,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(1)
    Assertions.assertThat(matchingReferences[0]).isEqualTo(sessionTemplateMonday.reference)
  }

  @Test
  fun `when create session template start time is before existing start time but ends after existing session template end time then references are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateMonday.validFromDate.minusDays(14), sessionTemplateMonday.validToDate!!.plusDays(1)),
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateMonday.startTime.minusMinutes(30), sessionTemplateMonday.endTime.plusMinutes(30)),
      dayOfWeek = DayOfWeek.MONDAY,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(1)
    Assertions.assertThat(matchingReferences).contains(sessionTemplateMonday.reference)
  }

  @Test
  fun `when create session template overlaps multiple session templates then multiple references are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateMonday.validFromDate.minusDays(14), sessionTemplateMonday.validToDate!!.plusDays(1)),
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateMonday.startTime.minusMinutes(30), sessionTemplateMondayPM.endTime.plusMinutes(30)),
      dayOfWeek = DayOfWeek.MONDAY,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(2)
    Assertions.assertThat(matchingReferences).contains(sessionTemplateMonday.reference, sessionTemplateMondayPM.reference)
  }

  @Test
  fun `when create session template start and end times are after existing session template end time no references are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateMonday.validToDate!!.plusDays(1), sessionTemplateMonday.validToDate!!.plusDays(1)),
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateMonday.endTime.plusMinutes(1), sessionTemplateMonday.endTime.plusMinutes(30)),
      dayOfWeek = DayOfWeek.MONDAY,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(0)
  }

  @Test
  fun `when create session template start and end times are before existing session template start time references are not returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateMonday.validToDate!!.plusDays(14), sessionTemplateMonday.validToDate!!.plusDays(31)),
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateMonday.startTime.minusMinutes(30), sessionTemplateMonday.startTime.minusMinutes(1)),
      dayOfWeek = DayOfWeek.MONDAY,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(0)
  }

  @Test
  fun `when create session template has weekly frequency lower than existing session template start time references are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateWednesdayWithWeeklyFreqAs2.validFromDate.plusWeeks(1)),
      dayOfWeek = DayOfWeek.WEDNESDAY,
      weeklyFrequency = 1,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(1)
    Assertions.assertThat(matchingReferences).contains(sessionTemplateWednesdayWithWeeklyFreqAs2.reference)
  }

  @Test
  fun `when create session template has weekly frequency higher than existing session template start time references are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateWednesdayWithWeeklyFreqAs2.validFromDate.plusWeeks(1)),
      dayOfWeek = DayOfWeek.WEDNESDAY,
      weeklyFrequency = 3,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(1)
    Assertions.assertThat(matchingReferences).contains(sessionTemplateWednesdayWithWeeklyFreqAs2.reference)
  }

  @Test
  fun `when create session template has weekly frequency same as existing session template but dates overlap then references are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateWednesdayWithWeeklyFreqAs2.validFromDate.plusWeeks(12)),
      dayOfWeek = DayOfWeek.WEDNESDAY,
      weeklyFrequency = 2,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(1)
    Assertions.assertThat(matchingReferences).contains(sessionTemplateWednesdayWithWeeklyFreqAs2.reference)
  }

  @Test
  fun `when create session template has weekly frequency same as existing session template with higher start date but dates overlap then references are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateWednesdayWithWeeklyFreqAs2.validFromDate.minusWeeks(12)),
      dayOfWeek = DayOfWeek.WEDNESDAY,
      weeklyFrequency = 2,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(1)
    Assertions.assertThat(matchingReferences).contains(sessionTemplateWednesdayWithWeeklyFreqAs2.reference)
  }

  @Test
  fun `when create session template has weekly frequency same as existing session template but dates do not overlap then references are not returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateWednesdayWithWeeklyFreqAs2.validFromDate.plusWeeks(13)),
      dayOfWeek = DayOfWeek.WEDNESDAY,
      weeklyFrequency = 2,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(0)
  }

  @Test
  fun `when create session template has weekly frequency same as existing session template with higher start date but dates do not overlap then references are not returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(sessionTemplateWednesdayWithWeeklyFreqAs2.validFromDate.minusWeeks(11)),
      dayOfWeek = DayOfWeek.WEDNESDAY,
      weeklyFrequency = 2,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(0)
  }

  @Test
  fun `when create session template has no locations and matches multiple session templates by location multiple references are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      dayOfWeek = DayOfWeek.THURSDAY,
      weeklyFrequency = 1,
    )

    val allThursdayReferences = listOf(
      sessionTemplateThursdayWithLevel1ALocations.reference,
      sessionTemplateThursdayWithLevel2Locations.reference,
      sessionTemplateThursdayWithLevel3Locations.reference,
      sessionTemplateThursdayWithLevel4Locations.reference,
      sessionTemplateThursdayWithDifferentLevelLocations.reference,
      sessionTemplateThursdayWithoutLocations.reference,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(6)
    Assertions.assertThat(matchingReferences).containsExactlyInAnyOrderElementsOf(allThursdayReferences)
  }

  @Test
  fun `when create session template has level 1 locations all matching session template references by location are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      dayOfWeek = DayOfWeek.THURSDAY,
      weeklyFrequency = 1,
      locationGroupReferences = listOf(level1ALocations.reference),
    )

    val allMatchingReferences = listOf(
      sessionTemplateThursdayWithLevel1ALocations.reference,
      sessionTemplateThursdayWithLevel2Locations.reference,
      sessionTemplateThursdayWithLevel3Locations.reference,
      sessionTemplateThursdayWithLevel4Locations.reference,
      sessionTemplateThursdayWithoutLocations.reference,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(5)
    Assertions.assertThat(matchingReferences).containsExactlyInAnyOrderElementsOf(allMatchingReferences)
    Assertions.assertThat(matchingReferences).doesNotContain(sessionTemplateThursdayWithDifferentLevelLocations.reference)
  }

  @Test
  fun `when create session template has level 2 locations all matching session template references by location are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      dayOfWeek = DayOfWeek.THURSDAY,
      weeklyFrequency = 1,
      locationGroupReferences = listOf(level2A1Locations.reference),
    )

    val allMatchingReferences = listOf(
      sessionTemplateThursdayWithLevel1ALocations.reference,
      sessionTemplateThursdayWithLevel2Locations.reference,
      sessionTemplateThursdayWithLevel3Locations.reference,
      sessionTemplateThursdayWithLevel4Locations.reference,
      sessionTemplateThursdayWithoutLocations.reference,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(5)
    Assertions.assertThat(matchingReferences).containsExactlyInAnyOrderElementsOf(allMatchingReferences)
    Assertions.assertThat(matchingReferences).doesNotContain(sessionTemplateThursdayWithDifferentLevelLocations.reference)
  }

  @Test
  fun `when create session template has level 2 for wing A2 locations all matching session template references by location are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      dayOfWeek = DayOfWeek.THURSDAY,
      weeklyFrequency = 1,
      locationGroupReferences = listOf(level2A2Locations.reference),
    )

    val allMatchingReferences = listOf(
      sessionTemplateThursdayWithLevel1ALocations.reference,
      sessionTemplateThursdayWithoutLocations.reference,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(2)
    Assertions.assertThat(matchingReferences).containsExactlyInAnyOrderElementsOf(allMatchingReferences)
    Assertions.assertThat(matchingReferences).doesNotContain(sessionTemplateThursdayWithDifferentLevelLocations.reference)
  }

  @Test
  fun `when create session template has level 3 locations all matching session template references by location are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      dayOfWeek = DayOfWeek.THURSDAY,
      weeklyFrequency = 1,
      locationGroupReferences = listOf(level3A12Locations.reference),
    )

    val allMatchingReferences = listOf(
      sessionTemplateThursdayWithLevel1ALocations.reference,
      sessionTemplateThursdayWithLevel2Locations.reference,
      sessionTemplateThursdayWithLevel3Locations.reference,
      sessionTemplateThursdayWithLevel4Locations.reference,
      sessionTemplateThursdayWithoutLocations.reference,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(5)
    Assertions.assertThat(matchingReferences).containsExactlyInAnyOrderElementsOf(allMatchingReferences)
    Assertions.assertThat(matchingReferences).doesNotContain(sessionTemplateThursdayWithDifferentLevelLocations.reference)
  }

  @Test
  fun `when create session template has level 4 locations all matching session template references by location are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      dayOfWeek = DayOfWeek.THURSDAY,
      weeklyFrequency = 1,
      locationGroupReferences = listOf(level4A123Locations.reference),
    )

    val allMatchingReferences = listOf(
      sessionTemplateThursdayWithLevel1ALocations.reference,
      sessionTemplateThursdayWithLevel2Locations.reference,
      sessionTemplateThursdayWithLevel3Locations.reference,
      sessionTemplateThursdayWithLevel4Locations.reference,
      sessionTemplateThursdayWithoutLocations.reference,
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(5)
    Assertions.assertThat(matchingReferences).containsExactlyInAnyOrderElementsOf(allMatchingReferences)
    Assertions.assertThat(matchingReferences).doesNotContain(sessionTemplateThursdayWithDifferentLevelLocations.reference)
  }

  @Test
  fun `when create session template has level B locations no A level matching session template references by location are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      dayOfWeek = DayOfWeek.THURSDAY,
      weeklyFrequency = 1,
      locationGroupReferences = listOf(level1BLocations.reference),
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(2)
    Assertions.assertThat(matchingReferences).containsExactlyInAnyOrderElementsOf(listOf(sessionTemplateThursdayWithoutLocations.reference, sessionTemplateThursdayWithDifferentLevelLocations.reference))
  }

  @Test
  fun `when create session template has category A 1 matching session template reference by category are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      dayOfWeek = DayOfWeek.FRIDAY,
      weeklyFrequency = 1,
      categoryGroupReferences = listOf(categoryAs.reference),
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(1)
    Assertions.assertThat(matchingReferences).contains(sessionTemplateFridayCategoryA.reference)
  }

  @Test
  fun `when create session template has non enhanced matching session template reference by enhanced level is not returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      dayOfWeek = DayOfWeek.SATURDAY,
      weeklyFrequency = 1,
      incentiveLevelGroupReferences = listOf(incentiveLevelNonEnhanced.reference),
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(0)
  }

  @Test
  fun `when create session template has location and category match then matching session templates are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      dayOfWeek = DayOfWeek.SATURDAY,
      weeklyFrequency = 1,
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateSaturdayPMLocationAndCategory.startTime, sessionTemplateSaturdayPMLocationAndCategory.endTime),
      locationGroupReferences = listOf(level1ALocations.reference),
      categoryGroupReferences = listOf(categoryAs.reference),
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(1)
    Assertions.assertThat(matchingReferences).contains(sessionTemplateSaturdayPMLocationAndCategory.reference)
  }

  @Test
  fun `when create session template has location match then matching session templates are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      dayOfWeek = DayOfWeek.SATURDAY,
      weeklyFrequency = 1,
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateSaturdayPMLocationAndCategory.startTime, sessionTemplateSaturdayPMLocationAndCategory.endTime),
      locationGroupReferences = listOf(level1ALocations.reference),
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(1)
    Assertions.assertThat(matchingReferences).contains(sessionTemplateSaturdayPMLocationAndCategory.reference)
  }

  @Test
  fun `when create session template has no location match then matching session templates are not returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      dayOfWeek = DayOfWeek.SATURDAY,
      weeklyFrequency = 1,
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateSaturdayPMLocationAndCategory.startTime, sessionTemplateSaturdayPMLocationAndCategory.endTime),
      locationGroupReferences = listOf(level1BLocations.reference),
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(0)
  }

  @Test
  fun `when create session template has no location but only category match then matching session templates are not returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      dayOfWeek = DayOfWeek.SATURDAY,
      weeklyFrequency = 1,
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateSaturdayPMLocationAndCategory.startTime, sessionTemplateSaturdayPMLocationAndCategory.endTime),
      locationGroupReferences = listOf(level1BLocations.reference),
      categoryGroupReferences = listOf(categoryAs.reference),
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(0)
  }

  @Test
  fun `when create session template has no incentive but only location and category match then matching session templates are not returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      dayOfWeek = DayOfWeek.SATURDAY,
      weeklyFrequency = 1,
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateSaturdayPMLocationCategoryAndIncentive.startTime, sessionTemplateSaturdayPMLocationCategoryAndIncentive.endTime),
      locationGroupReferences = listOf(level1ALocations.reference),
      categoryGroupReferences = listOf(categoryAs.reference),
      incentiveLevelGroupReferences = listOf(incentiveLevelNonEnhanced.reference),
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(0)
  }

  @Test
  fun `when create session template has incentive and location but no category match then matching session templates are not returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      dayOfWeek = DayOfWeek.SATURDAY,
      weeklyFrequency = 1,
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateSaturdayPMLocationCategoryAndIncentive.startTime, sessionTemplateSaturdayPMLocationCategoryAndIncentive.endTime),
      locationGroupReferences = listOf(level1ALocations.reference),
      categoryGroupReferences = listOf(categoryBCandD.reference),
      incentiveLevelGroupReferences = listOf(incentiveLevelNonEnhanced.reference),
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(0)
  }

  @Test
  fun `when create session template has no incentive location o category match then matching session templates are not returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      dayOfWeek = DayOfWeek.SATURDAY,
      weeklyFrequency = 1,
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateSaturdayPMLocationCategoryAndIncentive.startTime, sessionTemplateSaturdayPMLocationCategoryAndIncentive.endTime),
      locationGroupReferences = listOf(level1BLocations.reference),
      categoryGroupReferences = listOf(categoryBCandD.reference),
      incentiveLevelGroupReferences = listOf(incentiveLevelNonEnhanced.reference),
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(0)
  }

  @Test
  fun `when create session template has incentive location and category match then matching session templates are returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      dayOfWeek = DayOfWeek.SATURDAY,
      weeklyFrequency = 1,
      sessionTimeSlot = SessionTimeSlotDto(sessionTemplateSaturdayPMLocationCategoryAndIncentive.startTime, sessionTemplateSaturdayPMLocationCategoryAndIncentive.endTime),
      locationGroupReferences = listOf(level1ALocations.reference),
      categoryGroupReferences = listOf(categoryAs.reference),
      incentiveLevelGroupReferences = listOf(incentiveLevelEnhanced.reference),
    )

    // When
    val responseSpec = callCheckingMatchingTemplatesOnCreate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(1)
    Assertions.assertThat(matchingReferences).contains(sessionTemplateSaturdayPMLocationCategoryAndIncentive.reference)
  }
}
