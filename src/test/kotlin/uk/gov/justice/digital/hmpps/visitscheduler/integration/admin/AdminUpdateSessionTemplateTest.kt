package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.assertj.core.api.Assertions
import org.hamcrest.Matchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.boot.test.mock.mockito.SpyBean
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.ADMIN_SESSION_TEMPLATES_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionCapacityDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionDateRangeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callUpdateSessionTemplateByReference
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createUpdateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionCategoryGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.SessionIncentiveLevelGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Update $ADMIN_SESSION_TEMPLATES_PATH")
class AdminUpdateSessionTemplateTest : IntegrationTestBase() {

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  private var prison: Prison = Prison(code = "MDI", active = true)
  private lateinit var sessionTemplate: SessionTemplate
  private lateinit var sessionTemplateWithValidDates: SessionTemplate
  private lateinit var sessionTemplateWithWeeklyFrequencyOf6: SessionTemplate

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

  @SpyBean
  private lateinit var visitRepository: VisitRepository

  @BeforeEach
  internal fun setUpTests() {
    prison = prisonEntityHelper.create(prison.code, prison.active)
    sessionTemplate = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true)
    sessionTemplateWithValidDates = sessionTemplateEntityHelper.create(
      name = "session-template-for-update",
      prisonCode = prison.code,
      isActive = true,
      validFromDate = LocalDate.now().plusYears(1),
      validToDate = LocalDate.now().plusYears(2),
    )
    sessionTemplateWithWeeklyFrequencyOf6 = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, weeklyFrequency = 6)

    level1ALocations = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = listOf(AllowedSessionLocationHierarchy("A")))
    level2A1Locations = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = listOf(AllowedSessionLocationHierarchy("A", "1")))
    level2A2Locations = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = listOf(AllowedSessionLocationHierarchy("A", "2")))
    level3A12Locations = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = listOf(AllowedSessionLocationHierarchy("A", "1", "2")))
    level4A123Locations = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = listOf(AllowedSessionLocationHierarchy("A", "1", "2", "3")))
    level1BLocations = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = listOf(AllowedSessionLocationHierarchy("B")))

    categoryAs = sessionPrisonerCategoryHelper.create(prisonerCategories = listOf(PrisonerCategoryType.A_STANDARD, PrisonerCategoryType.A_EXCEPTIONAL, PrisonerCategoryType.A_HIGH, PrisonerCategoryType.A_PROVISIONAL))
    categoryBCandD = sessionPrisonerCategoryHelper.create(prisonerCategories = listOf(PrisonerCategoryType.B, PrisonerCategoryType.C, PrisonerCategoryType.D))
    incentiveLevelEnhanced = sessionPrisonerIncentiveLevelHelper.create(incentiveLevelList = listOf(IncentiveLevel.ENHANCED, IncentiveLevel.ENHANCED_2, IncentiveLevel.ENHANCED_3))
    incentiveLevelNonEnhanced = sessionPrisonerIncentiveLevelHelper.create(incentiveLevelList = listOf(IncentiveLevel.BASIC, IncentiveLevel.STANDARD))
  }

  @Test
  fun `update session template`() {
    // Given
    val allowedPermittedLocations = listOf(AllowedSessionLocationHierarchy("A", "1", "001"))
    val sessionGroup = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = allowedPermittedLocations)

    val categoryAs = listOf(PrisonerCategoryType.A_HIGH, PrisonerCategoryType.A_STANDARD, PrisonerCategoryType.A_EXCEPTIONAL, PrisonerCategoryType.A_PROVISIONAL)
    val sessionCategoryGroup = sessionPrisonerCategoryHelper.create(prisonCode = prison.code, prisonerCategories = categoryAs)

    val nonEnhancedIncentives = listOf(IncentiveLevel.BASIC, IncentiveLevel.STANDARD)
    val sessionIncentiveGroup = sessionPrisonerIncentiveLevelHelper.create(name = "Non Enhanced Incentives", prisonCode = prison.code, incentiveLevelList = nonEnhancedIncentives)

    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplate.name + " Updated",
      sessionDateRange = SessionDateRangeDto(
        validFromDate = sessionTemplate.validFromDate.plusDays(1),
        validToDate = sessionTemplate.validToDate?.plusDays(10),
      ),
      sessionCapacity = SessionCapacityDto(
        closed = sessionTemplate.closedCapacity + 1,
        open = sessionTemplate.openCapacity + 1,
      ),
      sessionTimeSlot = SessionTimeSlotDto(
        startTime = sessionTemplate.startTime.plusHours(1),
        endTime = sessionTemplate.endTime.plusHours(2),
      ),
      dayOfWeek = sessionTemplate.dayOfWeek.minus(1),
      locationGroupReferences = mutableListOf(sessionGroup.reference, sessionGroup.reference),
      categoryGroupReferences = mutableListOf(sessionCategoryGroup.reference, sessionCategoryGroup.reference),
      incentiveLevelGroupReferences = mutableListOf(sessionIncentiveGroup.reference, sessionIncentiveGroup.reference),
      weeklyFrequency = sessionTemplate.weeklyFrequency + 1,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validFromDate).isEqualTo(dto.sessionDateRange?.validFromDate)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validToDate).isEqualTo(dto.sessionDateRange?.validToDate)
    Assertions.assertThat(sessionTemplateDto.sessionCapacity.closed).isEqualTo(dto.sessionCapacity?.closed)
    Assertions.assertThat(sessionTemplateDto.sessionCapacity.open).isEqualTo(dto.sessionCapacity?.open)
    Assertions.assertThat(sessionTemplateDto.sessionTimeSlot.startTime).isEqualTo(dto.sessionTimeSlot?.startTime)
    Assertions.assertThat(sessionTemplateDto.sessionTimeSlot.endTime).isEqualTo(dto.sessionTimeSlot?.endTime)
    Assertions.assertThat(sessionTemplateDto.permittedLocationGroups.size).isEqualTo(1)
    Assertions.assertThat(sessionTemplateDto.permittedLocationGroups[0].reference).isEqualTo(dto.locationGroupReferences!![0])
    Assertions.assertThat(sessionTemplateDto.weeklyFrequency).isEqualTo(dto.weeklyFrequency)
    Assertions.assertThat(sessionTemplateDto.prisonerCategoryGroups.size).isEqualTo(1)
    Assertions.assertThat(sessionTemplateDto.prisonerCategoryGroups.stream().map { it.categories }).containsExactlyInAnyOrder(categoryAs)
    Assertions.assertThat(sessionTemplateDto.prisonerCategoryGroups[0].reference).isEqualTo(dto.categoryGroupReferences!![0])
    Assertions.assertThat(sessionTemplateDto.prisonerIncentiveLevelGroups.size).isEqualTo(1)
    Assertions.assertThat(sessionTemplateDto.prisonerIncentiveLevelGroups.stream().map { it.incentiveLevels }).containsExactlyInAnyOrder(nonEnhancedIncentives)
    Assertions.assertThat(sessionTemplateDto.prisonerIncentiveLevelGroups[0].reference).isEqualTo(dto.incentiveLevelGroupReferences!![0])
    Assertions.assertThat(sessionTemplateDto.active).isEqualTo(true)
  }

  @Test
  fun `when session template updated with new time slot and visits exist for session template update should fail`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplate.name + " Updated",
      sessionDateRange = null,
      sessionTimeSlot = SessionTimeSlotDto(
        startTime = sessionTemplate.startTime.plusHours(1),
        endTime = sessionTemplate.endTime.plusHours(2),
      ),
    )

    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplate.reference,
      visitStart = LocalDate.now().atTime(dto.sessionTimeSlot?.startTime),
      visitEnd = LocalDate.now().atTime(dto.sessionTimeSlot?.endTime),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update session times for ${sessionTemplate.reference} as there are existing visits associated with this session template!"))
  }

  @Test
  fun `when session template updated with new from date and visits exist for session template update should fail`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplate.name + " Updated",
      sessionDateRange = SessionDateRangeDto(
        // valid from date updated
        validFromDate = sessionTemplate.validFromDate.plusDays(2),
      ),
    )

    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplate.reference,
      visitStart = LocalDate.now().atTime(dto.sessionTimeSlot?.startTime),
      visitEnd = LocalDate.now().atTime(dto.sessionTimeSlot?.endTime),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update session valid from date for ${sessionTemplate.reference} as there are existing visits associated with this session template!"))
  }

  @Test
  fun `exception thrown when reference not found during update session template`() {
    // Given
    val dto = createUpdateSessionTemplateDto()
    val reference = "Ref1234"

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isNotFound
    val errorResponse = getErrorResponse(responseSpec)
    Assertions.assertThat(errorResponse.userMessage).isEqualTo("Template not found: null")
    Assertions.assertThat(errorResponse.developerMessage).isEqualTo("Template reference:$reference not found")
  }

  @Test
  fun `when session template name greater than 100 then validation fails and BAD_REQUEST is returned`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      name = RandomStringUtils.randomAlphabetic(101),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `when session template name not passed session template successfully updated`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      name = null,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template end time is less than start time then validation fails and BAD_REQUEST is returned`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      sessionTimeSlot = SessionTimeSlotDto(
        startTime = LocalTime.of(9, 0),
        endTime = LocalTime.of(8, 0),
      ),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.developerMessage").value(Matchers.containsString("Session end time should be greater than start time"))
  }

  @Test
  fun `when session template end time is same as start time then validation fails and BAD_REQUEST is returned`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      sessionTimeSlot = SessionTimeSlotDto(
        startTime = LocalTime.of(9, 0),
        endTime = LocalTime.of(9, 0),
      ),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.developerMessage").value(Matchers.containsString("Session end time should be greater than start time"))
  }

  @Test
  fun `when session template session times not passed session template successfully updated`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      sessionTimeSlot = null,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template end time is greater than start time then session template is created`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      sessionTimeSlot = SessionTimeSlotDto(
        startTime = LocalTime.of(9, 0),
        endTime = LocalTime.of(9, 1),
      ),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template valid to date is less than valid from date then validation fails and BAD_REQUEST is returned`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(
        validFromDate = LocalDate.of(2023, 1, 1),
        validToDate = LocalDate.of(2022, 12, 31),
      ),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.developerMessage").value(Matchers.containsString("Session valid to date cannot be less than valid from date"))
  }

  @Test
  fun `when session template valid to date is same as valid from date then session template is created`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(
        validFromDate = LocalDate.of(2023, 1, 1),
        validToDate = LocalDate.of(2023, 1, 1),
      ),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template valid to date is greater than valid from date then session template is created`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(
        validFromDate = LocalDate.of(2023, 1, 1),
        validToDate = LocalDate.of(2023, 1, 31),
      ),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template valid to date is null then session template is created`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      sessionDateRange = null,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated with new valid to date greater than current valid to date update session template should be successful`() {
    // Given
    val newSessionDateRange = SessionDateRangeDto(
      validFromDate = sessionTemplateWithValidDates.validFromDate,
      validToDate = sessionTemplateWithValidDates.validToDate!!.plusYears(1),
    )

    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplateWithValidDates.name + " Updated",
      sessionDateRange = newSessionDateRange,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithValidDates.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validFromDate).isEqualTo(dto.sessionDateRange?.validFromDate)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validToDate).isEqualTo(dto.sessionDateRange?.validToDate)
    verify(visitRepository, times(0)).hasVisitsForSessionTemplate(any(), any())
  }

  @Test
  fun `when session template updated with new valid to date same as current valid to date update session template should be successful`() {
    // Given
    val newSessionDateRange = SessionDateRangeDto(
      validFromDate = sessionTemplateWithValidDates.validFromDate,
      validToDate = sessionTemplateWithValidDates.validToDate,
    )

    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplateWithValidDates.name + " Updated",
      sessionDateRange = newSessionDateRange,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithValidDates.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validFromDate).isEqualTo(dto.sessionDateRange?.validFromDate)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validToDate).isEqualTo(dto.sessionDateRange?.validToDate)
    verify(visitRepository, times(0)).hasVisitsForSessionTemplate(any(), any())
  }

  @Test
  fun `when session template updated with new valid to date as null update session template should be successful`() {
    // Given
    val newSessionDateRange = SessionDateRangeDto(
      validFromDate = sessionTemplateWithValidDates.validFromDate,
      // setting new valid to date to null
      validToDate = null,
    )

    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplateWithValidDates.name + " Updated",
      sessionDateRange = newSessionDateRange,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithValidDates.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validFromDate).isEqualTo(dto.sessionDateRange?.validFromDate)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validToDate).isEqualTo(dto.sessionDateRange?.validToDate)
    verify(visitRepository, times(0)).hasVisitsForSessionTemplate(any(), any())
  }

  @Test
  fun `when session template updated with reduced valid to date but booked visits not affected update session template should be successful`() {
    // Given
    val newValidToDate = sessionTemplateWithValidDates.validToDate!!.minusMonths(1)
    val newSessionDateRange = SessionDateRangeDto(
      validFromDate = sessionTemplateWithValidDates.validFromDate,
      validToDate = newValidToDate,
    )

    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplateWithValidDates.name + " Updated",
      sessionDateRange = newSessionDateRange,
    )

    // visit exists 1 day before the new valid to date
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithValidDates.reference,
      visitStart = newValidToDate.minusDays(1).atTime(10, 0),
      visitEnd = newValidToDate.minusDays(1).atTime(11, 0),
    )

    // visit exists same day as new valid to date
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithValidDates.reference,
      visitStart = newValidToDate.atTime(10, 0),
      visitEnd = newValidToDate.atTime(11, 0),
    )
    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithValidDates.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validFromDate).isEqualTo(dto.sessionDateRange?.validFromDate)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validToDate).isEqualTo(dto.sessionDateRange?.validToDate)
    verify(visitRepository, times(0)).hasVisitsForSessionTemplate(any(), any())
    verify(visitRepository, times(1)).hasBookedVisitsForSessionTemplate(
      eq(sessionTemplateWithValidDates.reference),
      eq(newValidToDate.plusDays(1)),
    )
  }

  @Test
  fun `when session template updated with reduced valid to date but only cancelled visits are affected update session template should be successful`() {
    // Given
    val newValidToDate = sessionTemplateWithValidDates.validToDate!!.minusMonths(1)
    val newSessionDateRange = SessionDateRangeDto(
      validFromDate = sessionTemplateWithValidDates.validFromDate,
      validToDate = newValidToDate,
    )

    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplateWithValidDates.name + " Updated",
      sessionDateRange = newSessionDateRange,
    )

    // cancelled visit exists 1 day after the new valid to date
    val visitDate = newValidToDate.plusDays(1)
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithValidDates.reference,
      visitStart = visitDate.atTime(10, 0),
      visitEnd = visitDate.atTime(11, 0),
      visitStatus = VisitStatus.CANCELLED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithValidDates.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validFromDate).isEqualTo(dto.sessionDateRange?.validFromDate)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validToDate).isEqualTo(dto.sessionDateRange?.validToDate)
    verify(visitRepository, times(0)).hasVisitsForSessionTemplate(any(), any())
    verify(visitRepository, times(1)).hasBookedVisitsForSessionTemplate(
      eq(sessionTemplateWithValidDates.reference),
      eq(newValidToDate.plusDays(1)),
    )
  }

  @Test
  fun `when session template updated with reduced valid to date but booked visits affected update session template validation fails`() {
    // Given
    val newValidToDate = sessionTemplateWithValidDates.validToDate!!.minusMonths(1)
    val newSessionDateRange = SessionDateRangeDto(
      validFromDate = sessionTemplateWithValidDates.validFromDate,
      validToDate = newValidToDate,
    )

    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplateWithValidDates.name + " Updated",
      sessionDateRange = newSessionDateRange,
    )

    // visit exists 1 day after the new valid to date
    val visitDate = newValidToDate.plusDays(1)
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithValidDates.reference,
      visitStart = visitDate.atTime(10, 0),
      visitEnd = visitDate.atTime(11, 0),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithValidDates.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.containsString("Cannot update session valid to date to $newValidToDate for session template - ${sessionTemplateWithValidDates.reference} as there are booked or reserved visits associated with this session template after $newValidToDate."))
    verify(visitRepository, times(0)).hasVisitsForSessionTemplate(any(), any())
    verify(visitRepository, times(1)).hasBookedVisitsForSessionTemplate(eq(sessionTemplateWithValidDates.reference), eq(newValidToDate.plusDays(1)))
  }

  @Test
  fun `when session template updated with higher weekly frequency but visits do not exist for session template update session template should be successful`() {
    // Given
    val newWeeklyFrequency = sessionTemplate.weeklyFrequency + 2

    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplate.name + " Updated",
      weeklyFrequency = newWeeklyFrequency,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.weeklyFrequency).isEqualTo(newWeeklyFrequency)
    verify(visitRepository, times(1)).hasVisitsForSessionTemplate(eq(sessionTemplateDto.reference), eq(null))
  }

  @Test
  fun `when session template updated with lower weekly frequency which is not a factor but visits do not exist for session template update session template should be successful`() {
    // Given
    val newWeeklyFrequency = 4

    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplateWithWeeklyFrequencyOf6.name + " Updated",
      weeklyFrequency = newWeeklyFrequency,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithWeeklyFrequencyOf6.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.weeklyFrequency).isEqualTo(newWeeklyFrequency)
    verify(visitRepository, times(1)).hasVisitsForSessionTemplate(eq(sessionTemplateDto.reference), eq(null))
  }

  @Test
  fun `when session template updated with lower weekly frequency which is a factor of existing weekly frequency but visits do not exist for session template update session template should be successful`() {
    // Given
    val newWeeklyFrequency = 2

    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplateWithWeeklyFrequencyOf6.name + " Updated",
      weeklyFrequency = newWeeklyFrequency,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithWeeklyFrequencyOf6.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.weeklyFrequency).isEqualTo(newWeeklyFrequency)
    verify(visitRepository, times(0)).hasVisitsForSessionTemplate(any(), any())
  }

  @Test
  fun `when session template updated with higher weekly frequency but visits exist for session template update session template should fail`() {
    // Given
    val newWeeklyFrequency = sessionTemplate.weeklyFrequency + 2

    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplate.name + " Updated",
      weeklyFrequency = newWeeklyFrequency,
      sessionDateRange = null,
    )

    // visit exists for session template
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplate.reference,
    )
    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.containsString("Cannot update session template weekly frequency from ${sessionTemplate.weeklyFrequency} to $newWeeklyFrequency for ${sessionTemplate.reference} as existing visits for ${sessionTemplate.reference} might be affected!"))
  }

  @Test
  fun `when session template updated with lower weekly frequency which is not a factor and visits exist for session template update session template fails`() {
    // Given
    val newWeeklyFrequency = 4

    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplateWithWeeklyFrequencyOf6.name + " Updated",
      sessionDateRange = null,
      weeklyFrequency = newWeeklyFrequency,
    )

    // visit exists for session template
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithWeeklyFrequencyOf6.reference,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithWeeklyFrequencyOf6.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.containsString("Cannot update session template weekly frequency from ${sessionTemplateWithWeeklyFrequencyOf6.weeklyFrequency} to $newWeeklyFrequency for ${sessionTemplateWithWeeklyFrequencyOf6.reference} as existing visits for ${sessionTemplateWithWeeklyFrequencyOf6.reference} might be affected!"))
  }

  @Test
  fun `when session template updated with lower weekly frequency which is a factor of existing weekly frequency and visits exist for session template update session template should be successful`() {
    // Given
    val newWeeklyFrequency = 2

    // updating weekly frequency from 6 to 2 - valid scenario
    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplateWithWeeklyFrequencyOf6.name + " Updated",
      weeklyFrequency = newWeeklyFrequency,
      sessionDateRange = null,
    )

    // visit exists for session template
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithWeeklyFrequencyOf6.reference,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithWeeklyFrequencyOf6.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.weeklyFrequency).isEqualTo(newWeeklyFrequency)
    verify(visitRepository, times(0)).hasVisitsForSessionTemplate(any(), any())
  }

  @Test
  fun `when session template capacity upped update session template should be successful`() {
    // Given
    val newSessionCapacity = SessionCapacityDto(closed = sessionTemplate.closedCapacity + 1, open = sessionTemplate.openCapacity + 1)

    // updating session template capacity
    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplate.name + " Updated",
      sessionCapacity = newSessionCapacity,
      sessionDateRange = null,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.sessionCapacity).isEqualTo(newSessionCapacity)
    verify(visitRepository, times(0)).hasVisitsForSessionTemplate(any(), any())
  }

  @Test
  fun `when session template capacity same as existing update session template should be successful`() {
    // Given
    val newSessionCapacity = SessionCapacityDto(closed = sessionTemplate.closedCapacity, open = sessionTemplate.openCapacity)

    // updating session template capacity
    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplate.name + " Updated",
      sessionCapacity = newSessionCapacity,
      sessionDateRange = null,
    )
    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.sessionCapacity).isEqualTo(newSessionCapacity)
    verify(visitRepository, times(0)).hasVisitsForSessionTemplate(any(), any())
  }

  @Test
  fun `when session template open capacity lowered and no visits exist update session template should be successful`() {
    // Given
    val newSessionCapacity = SessionCapacityDto(closed = sessionTemplate.closedCapacity - 1, open = sessionTemplate.openCapacity - 1)

    // updating session template capacity
    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplate.name + " Updated",
      sessionCapacity = newSessionCapacity,
      sessionDateRange = null,
    )
    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.sessionCapacity).isEqualTo(newSessionCapacity)
    verify(visitRepository, times(1)).hasVisitsForSessionTemplate(sessionTemplate.reference, null)
  }

  @Test
  fun `when session template open capacity lowered above minimum allowed capacity update session template should be successful`() {
    // Given
    val newSessionCapacity = SessionCapacityDto(closed = 1, open = 2)

    // updating session template capacity
    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplate.name + " Updated",
      sessionCapacity = newSessionCapacity,
      sessionDateRange = null,
    )
    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // 2 open visit exists for session template
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplate.reference,
      visitRestriction = VisitRestriction.OPEN,
      visitStart = LocalDate.now().plusDays(1).atTime(11, 0),
      visitEnd = LocalDate.now().plusDays(1).atTime(12, 0),
    )

    visitEntityHelper.create(
      prisonerId = "AABBCC1",
      sessionTemplateReference = sessionTemplate.reference,
      visitRestriction = VisitRestriction.OPEN,
      visitStart = LocalDate.now().plusDays(1).atTime(11, 0),
      visitEnd = LocalDate.now().plusDays(1).atTime(12, 0),
    )

    // 1 closed visit exists for session template
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplate.reference,
      visitRestriction = VisitRestriction.CLOSED,
      visitStart = LocalDate.now().plusDays(1).atTime(11, 0),
      visitEnd = LocalDate.now().plusDays(1).atTime(12, 0),
    )

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.sessionCapacity).isEqualTo(newSessionCapacity)
    verify(visitRepository, times(1)).hasVisitsForSessionTemplate(sessionTemplate.reference, null)
  }

  @Test
  fun `when session template closed capacity lowered below minimum allowed capacity update session template should fail`() {
    // Given
    val newSessionCapacity = SessionCapacityDto(closed = 1, open = sessionTemplate.openCapacity)

    // updating session template capacity
    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplate.name + " Updated",
      sessionCapacity = newSessionCapacity,
      sessionDateRange = null,
    )

    // 2 open visit exists for session template
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplate.reference,
      visitRestriction = VisitRestriction.CLOSED,
      visitStart = LocalDate.now().plusDays(1).atTime(11, 0),
      visitEnd = LocalDate.now().plusDays(1).atTime(12, 0),
    )

    visitEntityHelper.create(
      prisonerId = "AABBCC1",
      sessionTemplateReference = sessionTemplate.reference,
      visitRestriction = VisitRestriction.CLOSED,
      visitStart = LocalDate.now().plusDays(1).atTime(11, 0),
      visitEnd = LocalDate.now().plusDays(1).atTime(12, 0),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.containsString("Cannot update session template closed capacity from ${sessionTemplate.closedCapacity} to ${newSessionCapacity.closed} for ${sessionTemplate.reference} as its lower than minimum capacity of 2!"))
  }

  @Test
  fun `when session template open capacity lowered below minimum allowed capacity update session template should fail`() {
    // Given
    val newSessionCapacity = SessionCapacityDto(closed = sessionTemplate.closedCapacity, open = 1)

    // updating session template capacity
    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplate.name + " Updated",
      sessionCapacity = newSessionCapacity,
      sessionDateRange = null,
    )

    // 2 open visit exists for session template
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplate.reference,
      visitRestriction = VisitRestriction.OPEN,
      visitStart = LocalDate.now().plusDays(1).atTime(11, 0),
      visitEnd = LocalDate.now().plusDays(1).atTime(12, 0),
    )

    visitEntityHelper.create(
      prisonerId = "AABBCC1",
      sessionTemplateReference = sessionTemplate.reference,
      visitRestriction = VisitRestriction.OPEN,
      visitStart = LocalDate.now().plusDays(1).atTime(11, 0),
      visitEnd = LocalDate.now().plusDays(1).atTime(12, 0),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.containsString("Cannot update session template open capacity from ${sessionTemplate.openCapacity} to ${newSessionCapacity.open} for ${sessionTemplate.reference} as its lower than minimum capacity of 2!"))
  }

  @Test
  fun `update session template fails for multiple reasons`() {
    // Given
    // update fails for multiple reasons
    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplate.name + " Updated",
      sessionDateRange = SessionDateRangeDto(sessionTemplate.validFromDate.minusDays(1)),
      sessionTimeSlot = SessionTimeSlotDto(
        startTime = sessionTemplate.startTime.plusHours(1),
        endTime = sessionTemplate.endTime.plusHours(2),
      ),
      sessionCapacity = SessionCapacityDto(closed = 1, open = 0),
    )

    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplate.reference,
      visitStart = LocalDate.now().atTime(dto.sessionTimeSlot?.startTime),
      visitEnd = LocalDate.now().atTime(dto.sessionTimeSlot?.endTime),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update session times for ${sessionTemplate.reference} as there are existing visits associated with this session template!"))
      .jsonPath("$.validationMessages[1]").value(Matchers.equalTo("Cannot update session valid from date for ${sessionTemplate.reference} as there are existing visits associated with this session template!"))
      .jsonPath("$.validationMessages[2]").value(Matchers.equalTo("Cannot update session template open capacity from ${sessionTemplate.openCapacity} to 0 for ${sessionTemplate.reference} as its lower than minimum capacity of 1!"))
  }

  @Test
  fun `when session template open and closed capacity are zero then validation fails and BAD_REQUEST is returned`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      sessionCapacity = SessionCapacityDto(
        open = 0,
        closed = 0,
      ),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.developerMessage").value(Matchers.containsString("Either open capacity or closed capacity should be greater than 0"))
  }

  @Test
  fun `when session template open capacity less than zero then validation fails and BAD_REQUEST is returned`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      sessionCapacity = SessionCapacityDto(
        open = -1,
        closed = 10,
      ),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `when session template closed capacity less than zero then validation fails and BAD_REQUEST is returned`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      sessionCapacity = SessionCapacityDto(
        open = 10,
        closed = -1,
      ),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `when session template open capacity greater than 0 but closed capacity is 0 then session template is created`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      sessionCapacity = SessionCapacityDto(
        open = 2,
        closed = 0,
      ),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template closed capacity greater than 0 but open capacity is 0 then session template is created`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      sessionCapacity = SessionCapacityDto(
        open = 0,
        closed = 3,
      ),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template capacity is null then session template is created`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      sessionCapacity = null,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template day of week is null then session template is created`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      dayOfWeek = null,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated with 0 locations and no locations exist session template is updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, permittedLocationGroups = mutableListOf())
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      locationGroupReferences = emptyList(),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithLocations.reference,
      visitStart = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithLocations.startTime),
      visitEnd = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithLocations.endTime),
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated with 0 locations and specific locations exist session template is updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, permittedLocationGroups = mutableListOf(level1ALocations))

    // existing locations are being removed
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      locationGroupReferences = emptyList(),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithLocations.reference,
      visitStart = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithLocations.startTime),
      visitEnd = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithLocations.endTime),
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated with same location as existing location session template is updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, permittedLocationGroups = mutableListOf(level4A123Locations))

    // existing locations are being removed
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      locationGroupReferences = listOf(level4A123Locations.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithLocations.reference,
      visitStart = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithLocations.startTime),
      visitEnd = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithLocations.endTime),
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated with higher location than existing location session template is updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, permittedLocationGroups = mutableListOf(level2A1Locations, level2A2Locations, level3A12Locations, level4A123Locations))

    // existing locations are being removed
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      locationGroupReferences = listOf(level1ALocations.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithLocations.reference,
      visitStart = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithLocations.startTime),
      visitEnd = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithLocations.endTime),
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated with higher location - level 2 than existing location session template is updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, permittedLocationGroups = mutableListOf(level3A12Locations, level4A123Locations))

    // existing locations are being removed
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      locationGroupReferences = listOf(level2A1Locations.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithLocations.reference,
      visitStart = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithLocations.startTime),
      visitEnd = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithLocations.endTime),
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated with higher location - level 3 than existing location session template is updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, permittedLocationGroups = mutableListOf(level4A123Locations))

    // existing locations are being removed
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      locationGroupReferences = listOf(level3A12Locations.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithLocations.reference,
      visitStart = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithLocations.startTime),
      visitEnd = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithLocations.endTime),
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated with lower location than existing location then session template update fails validation`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, permittedLocationGroups = mutableListOf(level1ALocations))

    // existing locations are being replaced with lower matches - validation should fail
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      locationGroupReferences = listOf(level2A1Locations.reference, level3A12Locations.reference, level4A123Locations.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithLocations.reference,
      visitStart = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithLocations.startTime),
      visitEnd = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithLocations.endTime),
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update locations to the new location list as all existing locations in session template are not catered for."))
  }

  @Test
  fun `when session template updated is missing a location in existing location list then session template update fails validation`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, permittedLocationGroups = mutableListOf(level2A1Locations, level3A12Locations, level4A123Locations, level1BLocations))

    // existing locations are being replaced with lower matches - validation should fail
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      locationGroupReferences = listOf(level2A1Locations.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithLocations.reference,
      visitStart = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithLocations.startTime),
      visitEnd = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithLocations.endTime),
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update locations to the new location list as all existing locations in session template are not catered for."))
  }

  @Test
  fun `when session template updated with 0 categories and no categories exist session template is updated`() {
    // Given
    val sessionTemplateWithCategories = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, permittedCategories = mutableListOf())
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithCategories),
      categoryGroupReferences = emptyList(),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithCategories.reference,
      visitStart = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithCategories.startTime),
      visitEnd = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithCategories.endTime),
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated with 0 categories and specific categories exist session template is updated`() {
    // Given
    val sessionTemplateWithCategories = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, permittedCategories = mutableListOf(categoryAs))

    // existing categories are being removed
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithCategories),
      categoryGroupReferences = emptyList(),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithCategories.reference,
      visitStart = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithCategories.startTime),
      visitEnd = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithCategories.endTime),
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated with same category as existing category session template is updated`() {
    // Given
    val sessionTemplateWithCategories = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, permittedCategories = mutableListOf(categoryAs))

    // existing categories are being removed
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithCategories),
      categoryGroupReferences = listOf(categoryAs.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithCategories.reference,
      visitStart = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithCategories.startTime),
      visitEnd = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithCategories.endTime),
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated but existing categories cannot be accommodated then session template update fails validation`() {
    // Given
    val sessionTemplateWithCategories = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, permittedCategories = mutableListOf(categoryAs, categoryBCandD))

    // existing B C and D category not included - validation should fail
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithCategories),
      categoryGroupReferences = listOf(categoryAs.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithCategories.reference,
      visitStart = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithCategories.startTime),
      visitEnd = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithCategories.endTime),
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update categories to the new category list as all existing prisoner categories in session template are not catered for."))
  }

  @Test
  fun `when session template updated but all existing categories cannot be accommodated then session template update fails validation`() {
    // Given
    val sessionTemplateWithCategories = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, permittedCategories = mutableListOf())

    // existing B C and D category not included - validation should fail
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithCategories),
      categoryGroupReferences = listOf(categoryAs.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithCategories.reference,
      visitStart = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithCategories.startTime),
      visitEnd = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithCategories.endTime),
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update categories to the new category list as all existing prisoner categories in session template are not catered for."))
  }

  @Test
  fun `when session template updated with 0 incentive levels and no incentive levels exist session template is updated`() {
    // Given
    val sessionTemplateWithIncentiveLevels = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, permittedIncentiveLevels = mutableListOf())
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithIncentiveLevels),
      incentiveLevelReferences = emptyList(),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithIncentiveLevels.reference,
      visitStart = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithIncentiveLevels.startTime),
      visitEnd = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithIncentiveLevels.endTime),
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentiveLevels.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated with 0 incentive levels and specific incentive levels exist session template is updated`() {
    // Given
    val sessionTemplateWithIncentiveLevels = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, permittedIncentiveLevels = mutableListOf(incentiveLevelEnhanced))

    // existing incentive levels are being removed
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithIncentiveLevels),
      incentiveLevelReferences = emptyList(),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithIncentiveLevels.reference,
      visitStart = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithIncentiveLevels.startTime),
      visitEnd = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithIncentiveLevels.endTime),
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentiveLevels.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated with same incentive level as existing incentive level session template is updated`() {
    // Given
    val sessionTemplateWithIncentiveLevels = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, permittedIncentiveLevels = mutableListOf(incentiveLevelEnhanced))

    // existing incentive levels are being removed
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithIncentiveLevels),
      incentiveLevelReferences = listOf(incentiveLevelEnhanced.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithIncentiveLevels.reference,
      visitStart = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithIncentiveLevels.startTime),
      visitEnd = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithIncentiveLevels.endTime),
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentiveLevels.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated but existing incentive levels cannot be accommodated then session template update fails validation`() {
    // Given
    val sessionTemplateWithIncentiveLevels = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, permittedIncentiveLevels = mutableListOf(incentiveLevelEnhanced, incentiveLevelNonEnhanced))

    // existing enhanced incentive level not included - validation should fail
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithIncentiveLevels),
      incentiveLevelReferences = listOf(incentiveLevelNonEnhanced.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithIncentiveLevels.reference,
      visitStart = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithIncentiveLevels.startTime),
      visitEnd = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithIncentiveLevels.endTime),
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentiveLevels.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update incentive levels to the new incentive levels list as all existing incentive levels in session template are not catered for."))
  }

  @Test
  fun `when session template updated but all existing incentive levels cannot be accommodated then session template update fails validation`() {
    // Given
    val sessionTemplateWithIncentiveLevels = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, permittedIncentiveLevels = mutableListOf())

    // existing incentive levels not included - validation should fail
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithIncentiveLevels),
      incentiveLevelReferences = listOf(incentiveLevelEnhanced.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplateReference = sessionTemplateWithIncentiveLevels.reference,
      visitStart = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithIncentiveLevels.startTime),
      visitEnd = LocalDate.now().plusWeeks(1).atTime(sessionTemplateWithIncentiveLevels.endTime),
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentiveLevels.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update incentive levels to the new incentive levels list as all existing incentive levels in session template are not catered for."))
  }
}
