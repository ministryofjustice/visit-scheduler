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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.ADMIN_SESSION_TEMPLATES_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UserClientDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionCapacityDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionDateRangeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callUpdateSessionTemplateByReference
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createUpdateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionCategoryGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.SessionIncentiveLevelGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Update $ADMIN_SESSION_TEMPLATES_PATH")
class AdminUpdateSessionTemplateTest : IntegrationTestBase() {

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

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

  @MockitoSpyBean
  private lateinit var visitRepository: VisitRepository

  @BeforeEach
  internal fun setUpTests() {
    prison = prisonEntityHelper.create()
    sessionTemplateDefault = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true)
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
      name = sessionTemplateDefault.name + " Updated",
      sessionDateRange = SessionDateRangeDto(
        validFromDate = sessionTemplateDefault.validFromDate.plusDays(1),
        validToDate = sessionTemplateDefault.validToDate?.plusDays(10),
      ),
      sessionCapacity = SessionCapacityDto(
        closed = sessionTemplateDefault.closedCapacity + 1,
        open = sessionTemplateDefault.openCapacity + 1,
      ),
      sessionTimeSlot = SessionTimeSlotDto(
        startTime = sessionTemplateDefault.startTime.plusHours(1),
        endTime = sessionTemplateDefault.endTime.plusHours(2),
      ),
      visitRoom = "new room name",
      dayOfWeek = sessionTemplateDefault.dayOfWeek.minus(1),
      locationGroupReferences = mutableListOf(sessionGroup.reference, sessionGroup.reference),
      categoryGroupReferences = mutableListOf(sessionCategoryGroup.reference, sessionCategoryGroup.reference),
      incentiveLevelGroupReferences = mutableListOf(sessionIncentiveGroup.reference, sessionIncentiveGroup.reference),
      weeklyFrequency = sessionTemplateDefault.weeklyFrequency + 1,
      includeLocationGroupType = true,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validFromDate).isEqualTo(dto.sessionDateRange?.validFromDate)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validToDate).isEqualTo(dto.sessionDateRange?.validToDate)
    Assertions.assertThat(sessionTemplateDto.visitRoom).isEqualTo(dto.visitRoom)
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
    Assertions.assertThat(sessionTemplateDto.includeLocationGroupType).isEqualTo(true)
  }

  @Test
  fun `when session template updated with new time slot and visits exist for session template update should fail`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplateDefault.name + " Updated",
      sessionDateRange = null,
      sessionTimeSlot = SessionTimeSlotDto(
        startTime = sessionTemplateDefault.startTime.plusHours(1),
        endTime = sessionTemplateDefault.endTime.plusHours(2),
      ),
    )

    visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      slotDate = LocalDate.now(),
      visitStart = dto.sessionTimeSlot!!.startTime,
      visitEnd = dto.sessionTimeSlot.endTime,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update session times for ${sessionTemplateDefault.reference} as there are existing visits associated with this session template!"))
  }

  @Test
  fun `when session template updated with new from date and visits exist for session template update should fail`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplateDefault.name + " Updated",
      sessionDateRange = SessionDateRangeDto(
        // valid from date updated
        validFromDate = sessionTemplateDefault.validFromDate.plusDays(2),
      ),
    )

    visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      slotDate = LocalDate.now(),
      visitStart = dto.sessionTimeSlot!!.startTime,
      visitEnd = dto.sessionTimeSlot.endTime,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update session valid from date for ${sessionTemplateDefault.reference} as there are existing visits associated with this session template!"))
  }

  @Test
  fun `exception thrown when reference not found during update session template`() {
    // Given
    val dto = createUpdateSessionTemplateDto()
    val reference = "Ref1234"

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithValidDates.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithValidDates.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithValidDates.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
      sessionTemplate = sessionTemplateWithValidDates,
      slotDate = newValidToDate.minusDays(1),
      visitStart = LocalTime.of(10, 0),
      visitEnd = LocalTime.of(11, 0),
    )

    // visit exists same day as new valid to date
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithValidDates,
      slotDate = newValidToDate,
      visitStart = LocalTime.of(10, 0),
      visitEnd = LocalTime.of(11, 0),
    )
    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithValidDates.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
      sessionTemplate = sessionTemplateWithValidDates,
      slotDate = visitDate,
      visitStart = LocalTime.of(10, 0),
      visitEnd = LocalTime.of(11, 0),
      visitStatus = VisitStatus.CANCELLED,
      visitSubStatus = VisitSubStatus.CANCELLED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithValidDates.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
      sessionTemplate = sessionTemplateWithValidDates,
      slotDate = visitDate,
      visitStart = LocalTime.of(10, 0),
      visitEnd = LocalTime.of(11, 0),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithValidDates.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val newWeeklyFrequency = sessionTemplateDefault.weeklyFrequency + 2

    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplateDefault.name + " Updated",
      weeklyFrequency = newWeeklyFrequency,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithWeeklyFrequencyOf6.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithWeeklyFrequencyOf6.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val newWeeklyFrequency = sessionTemplateDefault.weeklyFrequency + 2

    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplateDefault.name + " Updated",
      weeklyFrequency = newWeeklyFrequency,
      sessionDateRange = null,
    )

    // visit exists for session template
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
    )
    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.containsString("Cannot update session template weekly frequency from ${sessionTemplateDefault.weeklyFrequency} to $newWeeklyFrequency for ${sessionTemplateDefault.reference} as existing visits for ${sessionTemplateDefault.reference} might be affected!"))
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
      sessionTemplate = sessionTemplateWithWeeklyFrequencyOf6,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithWeeklyFrequencyOf6.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
      sessionTemplate = sessionTemplateWithWeeklyFrequencyOf6,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithWeeklyFrequencyOf6.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val newSessionCapacity = SessionCapacityDto(closed = sessionTemplateDefault.closedCapacity + 1, open = sessionTemplateDefault.openCapacity + 1)

    // updating session template capacity
    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplateDefault.name + " Updated",
      sessionCapacity = newSessionCapacity,
      sessionDateRange = null,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val newSessionCapacity = SessionCapacityDto(closed = sessionTemplateDefault.closedCapacity, open = sessionTemplateDefault.openCapacity)

    // updating session template capacity
    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplateDefault.name + " Updated",
      sessionCapacity = newSessionCapacity,
      sessionDateRange = null,
    )
    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val newSessionCapacity = SessionCapacityDto(closed = sessionTemplateDefault.closedCapacity - 1, open = sessionTemplateDefault.openCapacity - 1)

    // updating session template capacity
    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplateDefault.name + " Updated",
      sessionCapacity = newSessionCapacity,
      sessionDateRange = null,
    )
    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.sessionCapacity).isEqualTo(newSessionCapacity)
    verify(visitRepository, times(1)).hasVisitsForSessionTemplate(sessionTemplateDefault.reference, null)
  }

  @Test
  fun `when session template open capacity lowered above minimum allowed capacity update session template should be successful`() {
    // Given
    val newSessionCapacity = SessionCapacityDto(closed = 1, open = 2)

    // updating session template capacity
    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplateDefault.name + " Updated",
      sessionCapacity = newSessionCapacity,
      sessionDateRange = null,
    )
    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // 2 open visit exists for session template
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      visitRestriction = VisitRestriction.OPEN,
      slotDate = LocalDate.now().plusDays(1),
      visitStart = LocalTime.of(11, 0),
      visitEnd = LocalTime.of(12, 0),
    )

    visitEntityHelper.create(
      prisonerId = "AABBCC1",
      sessionTemplate = sessionTemplateDefault,
      visitRestriction = VisitRestriction.OPEN,
      slotDate = LocalDate.now().plusDays(1),
      visitStart = LocalTime.of(11, 0),
      visitEnd = LocalTime.of(12, 0),
    )

    // 1 closed visit exists for session template
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      visitRestriction = VisitRestriction.CLOSED,
      slotDate = LocalDate.now().plusDays(1),
      visitStart = LocalTime.of(11, 0),
      visitEnd = LocalTime.of(12, 0),
    )

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.sessionCapacity).isEqualTo(newSessionCapacity)
    verify(visitRepository, times(1)).hasVisitsForSessionTemplate(sessionTemplateDefault.reference, null)
  }

  @Test
  fun `update session template fails for multiple reasons`() {
    // Given
    // update fails for multiple reasons
    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplateDefault.name + " Updated",
      sessionDateRange = SessionDateRangeDto(sessionTemplateDefault.validFromDate.minusDays(1)),
      sessionTimeSlot = SessionTimeSlotDto(
        startTime = sessionTemplateDefault.startTime.plusHours(1),
        endTime = sessionTemplateDefault.endTime.plusHours(2),
      ),
      sessionCapacity = SessionCapacityDto(closed = 1, open = 0),
    )

    visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      slotDate = LocalDate.now(),
      visitStart = dto.sessionTimeSlot!!.startTime,
      visitEnd = dto.sessionTimeSlot.endTime,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update session times for ${sessionTemplateDefault.reference} as there are existing visits associated with this session template!"))
      .jsonPath("$.validationMessages[1]").value(Matchers.equalTo("Cannot update session valid from date for ${sessionTemplateDefault.reference} as there are existing visits associated with this session template!"))
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
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update locations to the new location list as all existing locations in session template are not catered for."))
  }

  @Test
  fun `when session template updated with same excluded location list as existing then session template is updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      permittedLocationGroups = mutableListOf(level4A123Locations),
      includeLocationGroupType = false,
    )

    // existing locations are being removed
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      includeLocationGroupType = false,
      locationGroupReferences = listOf(level4A123Locations.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated to remove all excluded locations then session template is updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeLocationGroupType = false,
      permittedLocationGroups = mutableListOf(level1ALocations),
    )

    // existing locations are being removed
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      includeLocationGroupType = false,
      locationGroupReferences = emptyList(),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated to exclude a location when no excluded locations exist on the session template then session template is not updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      permittedLocationGroups = mutableListOf(),
      includeLocationGroupType = false,
    )

    // level4A123Locations are being excluded now
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      includeLocationGroupType = false,
      locationGroupReferences = listOf(level4A123Locations.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update locations to the new location list as all existing locations in session template are not catered for."))
  }

  @Test
  fun `when session template updated to exclude a higher location than existing excluded location session template is not updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeLocationGroupType = false,
      permittedLocationGroups = mutableListOf(level2A1Locations),
    )

    // level2A2Locations locations are also being excluded
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      includeLocationGroupType = false,
      locationGroupReferences = listOf(level2A1Locations.reference, level2A2Locations.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update locations to the new location list as all existing locations in session template are not catered for."))
  }

  @Test
  fun `when session template updated to exclude more locations than existing excluded locations session template is not updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeLocationGroupType = false,
      permittedLocationGroups = mutableListOf(level3A12Locations, level4A123Locations),
    )

    // level1ALocations also being excluded
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      includeLocationGroupType = false,
      locationGroupReferences = listOf(level1ALocations.reference, level4A123Locations.reference, level3A12Locations.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update locations to the new location list as all existing locations in session template are not catered for."))
  }

  @Test
  fun `when session template updated to remove an excluded location from existing excluded locations session template is updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeLocationGroupType = false,
      permittedLocationGroups = mutableListOf(level3A12Locations, level4A123Locations),
    )

    // level3A12Locations is no longer being excluded
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      includeLocationGroupType = false,
      locationGroupReferences = listOf(level4A123Locations.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated with lower location than existing excluded locations session template is updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeLocationGroupType = false,
      permittedLocationGroups = mutableListOf(level1ALocations),
    )

    // existing locations are being replaced with lower matches - validation should fail
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      includeLocationGroupType = false,
      locationGroupReferences = listOf(level2A1Locations.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated is missing an excluded location in existing excluded location list then session template update fails validation`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      permittedLocationGroups = mutableListOf(level2A1Locations),
    )

    // existing locations are being replaced with lower matches - validation should fail
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      locationGroupReferences = mutableListOf(level2A1Locations.reference, level3A12Locations.reference, level4A123Locations.reference, level1BLocations.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated to change included locations to excluded locations then session template is not updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      permittedLocationGroups = mutableListOf(level4A123Locations),
      includeLocationGroupType = true,
    )

    // existing locations are being removed
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      includeLocationGroupType = false,
      locationGroupReferences = listOf(level4A123Locations.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update locations to the new location list as all existing locations in session template are not catered for."))
  }

  @Test
  fun `when included session template changed to excluded locations then session template is not updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeLocationGroupType = true,
      permittedLocationGroups = mutableListOf(level1ALocations),
    )

    // existing locations are being removed
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      includeLocationGroupType = false,
      locationGroupReferences = emptyList(),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update locations to the new location list as all existing locations in session template are not catered for."))
  }

  @Test
  fun `when session template updated to exclude a location when no included locations exist on the session template then session template is not updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      permittedLocationGroups = mutableListOf(),
      includeLocationGroupType = true,
    )

    // level4A123Locations are being excluded now
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      includeLocationGroupType = false,
      locationGroupReferences = listOf(level4A123Locations.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update locations to the new location list as all existing locations in session template are not catered for."))
  }

  @Test
  fun `when session template updated to exclude a higher location than existing included location session template is not updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeLocationGroupType = true,
      permittedLocationGroups = mutableListOf(level2A1Locations),
    )

    // level2A2Locations locations are also being excluded
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      includeLocationGroupType = false,
      locationGroupReferences = listOf(level1ALocations.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update locations to the new location list as all existing locations in session template are not catered for."))
  }

  @Test
  fun `when session template updated to exclude locations that were included earlier session template is not updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeLocationGroupType = true,
      permittedLocationGroups = mutableListOf(level3A12Locations, level4A123Locations),
    )

    // level1ALocations also being excluded
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      includeLocationGroupType = false,
      locationGroupReferences = listOf(level1ALocations.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update locations to the new location list as all existing locations in session template are not catered for."))
  }

  @Test
  fun `when session template updated to remove an included location from existing excluded locations session template is not updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeLocationGroupType = true,
      permittedLocationGroups = mutableListOf(level3A12Locations, level1ALocations),
    )

    // level3A12Locations is no longer being excluded
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      includeLocationGroupType = false,
      locationGroupReferences = listOf(level4A123Locations.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update locations to the new location list as all existing locations in session template are not catered for."))
  }

  @Test
  fun `when session template updated with lower location than existing excluded locations session template is not updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeLocationGroupType = true,
      permittedLocationGroups = mutableListOf(level1ALocations),
    )

    // existing locations are being replaced with lower matches - validation should fail
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      includeLocationGroupType = false,
      locationGroupReferences = listOf(level2A1Locations.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update locations to the new location list as all existing locations in session template are not catered for."))
  }

  @Test
  fun `when session template updated adds different location than existing included locations list then session template is updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeLocationGroupType = true,
      permittedLocationGroups = mutableListOf(level2A1Locations),
    )

    // existing locations are being replaced with lower matches - validation should fail
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      includeLocationGroupType = false,
      locationGroupReferences = mutableListOf(level1BLocations.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated with lower location but no future visits exist update is successful`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, permittedLocationGroups = mutableListOf())

    // existing locations are being replaced with lower matches - but validation will not fail as no visits in future
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      locationGroupReferences = listOf(level2A1Locations.reference),
    )

    // all existing visits are past dated
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().minusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated to change excluded locations to included locations and all sessions are included then session template is updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      permittedLocationGroups = mutableListOf(level4A123Locations),
      includeLocationGroupType = false,
    )

    // all locations are included
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      includeLocationGroupType = true,
      locationGroupReferences = emptyList(),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated to change excluded locations to included locations and not all sessions are included then session template is not updated`() {
    // Given
    val sessionTemplateWithLocations = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      permittedLocationGroups = mutableListOf(level4A123Locations),
      includeLocationGroupType = false,
    )

    // all locations are included
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithLocations),
      includeLocationGroupType = true,
      locationGroupReferences = mutableListOf(level1ALocations.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithLocations,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithLocations.startTime,
      visitEnd = sessionTemplateWithLocations.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithLocations.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update locations to the new location list as all existing locations in session template are not catered for."))
  }

  @Test
  fun `when session template updated with less categories but no future visits exist update is successful`() {
    // Given
    val sessionTemplateWithCategories = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, permittedCategories = mutableListOf())

    // existing categories are being replaced with lower matches - but validation will not fail as no visits in future
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithCategories),
      categoryGroupReferences = listOf(categoryAs.reference),
    )

    // all existing visits are past dated
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithCategories,
      slotDate = LocalDate.now().minusWeeks(1),
      visitStart = sessionTemplateWithCategories.startTime,
      visitEnd = sessionTemplateWithCategories.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
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
      sessionTemplate = sessionTemplateWithCategories,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithCategories.startTime,
      visitEnd = sessionTemplateWithCategories.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
      sessionTemplate = sessionTemplateWithCategories,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithCategories.startTime,
      visitEnd = sessionTemplateWithCategories.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
      sessionTemplate = sessionTemplateWithCategories,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithCategories.startTime,
      visitEnd = sessionTemplateWithCategories.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
      sessionTemplate = sessionTemplateWithCategories,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithCategories.startTime,
      visitEnd = sessionTemplateWithCategories.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
      sessionTemplate = sessionTemplateWithCategories,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithCategories.startTime,
      visitEnd = sessionTemplateWithCategories.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update categories to the new category list as all existing prisoner categories in session template are not catered for."))
  }

  @Test
  fun `when session template updated with 0 categories and no excluded categories exist session template is updated`() {
    // Given
    val sessionTemplateWithCategories = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeCategoryGroupType = false,
      permittedCategories = mutableListOf(),
    )
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithCategories),
      includeCategoryGroupType = false,
      categoryGroupReferences = emptyList(),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithCategories,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithCategories.startTime,
      visitEnd = sessionTemplateWithCategories.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when existing excluded categories removed session template is updated`() {
    // Given
    val sessionTemplateWithCategories = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeCategoryGroupType = false,
      permittedCategories = mutableListOf(categoryAs),
    )

    // existing categories are being removed
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithCategories),
      includeCategoryGroupType = false,
      categoryGroupReferences = emptyList(),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithCategories,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithCategories.startTime,
      visitEnd = sessionTemplateWithCategories.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated with same excluded category as existing excluded category session template is updated`() {
    // Given
    val sessionTemplateWithCategories = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeCategoryGroupType = false,
      permittedCategories = mutableListOf(categoryAs),
    )

    // existing categories are being removed
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithCategories),
      includeCategoryGroupType = false,
      categoryGroupReferences = listOf(categoryAs.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithCategories,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithCategories.startTime,
      visitEnd = sessionTemplateWithCategories.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated with new excluded categories then session template is not updated`() {
    // Given
    val sessionTemplateWithCategories = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      permittedCategories = mutableListOf(categoryAs),
      includeCategoryGroupType = false,
    )

    // existing B C and D category not included - validation should fail
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithCategories),
      categoryGroupReferences = listOf(categoryAs.reference, categoryBCandD.reference),
      includeCategoryGroupType = false,
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithCategories,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithCategories.startTime,
      visitEnd = sessionTemplateWithCategories.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update categories to the new category list as all existing prisoner categories in session template are not catered for."))
  }

  @Test
  fun `when session template updated with new excluded categories when there were none then session template is not updated`() {
    // Given
    val sessionTemplateWithCategories = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      permittedCategories = mutableListOf(),
      includeCategoryGroupType = false,
    )

    // existing B C and D category not included - validation should fail
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithCategories),
      categoryGroupReferences = listOf(categoryAs.reference, categoryBCandD.reference),
      includeCategoryGroupType = false,
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithCategories,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithCategories.startTime,
      visitEnd = sessionTemplateWithCategories.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update categories to the new category list as all existing prisoner categories in session template are not catered for."))
  }

  @Test
  fun `when session template updated with new excluded categories but no visits exists then session template is updated`() {
    // Given
    val sessionTemplateWithCategories = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      permittedCategories = mutableListOf(),
      includeCategoryGroupType = false,
    )

    // existing B C and D category not included - validation should fail
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithCategories),
      categoryGroupReferences = listOf(categoryAs.reference, categoryBCandD.reference),
      includeCategoryGroupType = false,
    )

    // no future dated BOOKED visits exist, only past dated ones
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithCategories,
      slotDate = LocalDate.now().minusWeeks(1),
      visitStart = sessionTemplateWithCategories.startTime,
      visitEnd = sessionTemplateWithCategories.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated to change included categories to excluded categories then session template is not updated`() {
    // Given
    val sessionTemplateWithCategories = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      permittedCategories = mutableListOf(categoryAs),
      includeCategoryGroupType = true,
    )

    // existing categories are being removed
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithCategories),
      includeCategoryGroupType = false,
      categoryGroupReferences = listOf(categoryAs.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithCategories,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithCategories.startTime,
      visitEnd = sessionTemplateWithCategories.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update categories to the new category list as all existing prisoner categories in session template are not catered for."))
  }

  @Test
  fun `when included session template changed to excluded categories then session template is not updated`() {
    // Given
    val sessionTemplateWithCategories = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeCategoryGroupType = true,
      permittedCategories = mutableListOf(categoryAs),
    )

    // existing locations are being removed
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithCategories),
      includeCategoryGroupType = false,
      categoryGroupReferences = emptyList(),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithCategories,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithCategories.startTime,
      visitEnd = sessionTemplateWithCategories.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update categories to the new category list as all existing prisoner categories in session template are not catered for."))
  }

  @Test
  fun `when session template updated to exclude a category when no included categories exist on the session template then session template is not updated`() {
    // Given
    val sessionTemplateWithCategories = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      permittedCategories = mutableListOf(),
      includeCategoryGroupType = true,
    )

    // categoryAs are being excluded now
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithCategories),
      includeCategoryGroupType = false,
      categoryGroupReferences = listOf(categoryAs.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithCategories,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithCategories.startTime,
      visitEnd = sessionTemplateWithCategories.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update categories to the new category list as all existing prisoner categories in session template are not catered for."))
  }

  @Test
  fun `when session template updated to exclude categories that were included earlier session template is not updated`() {
    // Given
    val sessionTemplateWithCategories = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeCategoryGroupType = true,
      permittedCategories = mutableListOf(categoryAs, categoryBCandD),
    )

    // category A is being excluded
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithCategories),
      includeCategoryGroupType = false,
      categoryGroupReferences = listOf(categoryAs.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithCategories,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithCategories.startTime,
      visitEnd = sessionTemplateWithCategories.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update categories to the new category list as all existing prisoner categories in session template are not catered for."))
  }

  @Test
  fun `when session template updated adds different categories than existing included categories then session template is updated`() {
    // Given
    val sessionTemplateWithCategories = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeCategoryGroupType = true,
      permittedCategories = mutableListOf(categoryAs),
    )

    // existing locations are being replaced with lower matches - validation should fail
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithCategories),
      includeCategoryGroupType = false,
      categoryGroupReferences = mutableListOf(categoryBCandD.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithCategories,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithCategories.startTime,
      visitEnd = sessionTemplateWithCategories.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated to remove a category but no future visits exist update is successful`() {
    // Given
    val sessionTemplateWithCategories = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, permittedCategories = mutableListOf())

    // existing categories are being replaced with more restrictive - but validation will not fail as there are no future visits
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithCategories),
      categoryGroupReferences = listOf(categoryAs.reference),
    )

    // all existing visits are past dated
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithCategories,
      slotDate = LocalDate.now().minusWeeks(1),
      visitStart = sessionTemplateWithCategories.startTime,
      visitEnd = sessionTemplateWithCategories.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated to change excluded categories to included categories and all sessions are included then session template is updated`() {
    // Given
    val sessionTemplateWithCategories = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      permittedCategories = mutableListOf(categoryAs),
      includeCategoryGroupType = false,
    )

    // all categories are included
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithCategories),
      includeCategoryGroupType = true,
      categoryGroupReferences = emptyList(),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithCategories,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithCategories.startTime,
      visitEnd = sessionTemplateWithCategories.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated to change excluded categories to included categories and not all sessions are included then session template is not updated`() {
    // Given
    val sessionTemplateWithCategories = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      permittedCategories = mutableListOf(categoryAs),
      includeCategoryGroupType = false,
    )

    // B C And D included
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithCategories),
      includeCategoryGroupType = true,
      categoryGroupReferences = mutableListOf(categoryBCandD.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithCategories,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithCategories.startTime,
      visitEnd = sessionTemplateWithCategories.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithCategories.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
      sessionTemplate = sessionTemplateWithIncentiveLevels,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithIncentiveLevels.startTime,
      visitEnd = sessionTemplateWithIncentiveLevels.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentiveLevels.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
      sessionTemplate = sessionTemplateWithIncentiveLevels,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithIncentiveLevels.startTime,
      visitEnd = sessionTemplateWithIncentiveLevels.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentiveLevels.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
      sessionTemplate = sessionTemplateWithIncentiveLevels,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithIncentiveLevels.startTime,
      visitEnd = sessionTemplateWithIncentiveLevels.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentiveLevels.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
      sessionTemplate = sessionTemplateWithIncentiveLevels,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithIncentiveLevels.startTime,
      visitEnd = sessionTemplateWithIncentiveLevels.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentiveLevels.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

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
      sessionTemplate = sessionTemplateWithIncentiveLevels,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithIncentiveLevels.startTime,
      visitEnd = sessionTemplateWithIncentiveLevels.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentiveLevels.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update incentive levels to the new incentive levels list as all existing incentive levels in session template are not catered for."))
  }

  @Test
  fun `when session template updated with less incentive levels but no future visits exist update is successful`() {
    // Given
    val sessionTemplateWithIncentives = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, permittedIncentiveLevels = mutableListOf())

    // existing incentive levels are being replaced with lower matches - but validation will not fail as no visits in future
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithIncentives),
      incentiveLevelReferences = listOf(incentiveLevelEnhanced.reference),
    )

    // all existing visits are past dated
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithIncentives,
      slotDate = LocalDate.now().minusWeeks(1),
      visitStart = sessionTemplateWithIncentives.startTime,
      visitEnd = sessionTemplateWithIncentives.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentives.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template with no clients are updated new client are added`() {
    // Given
    Assertions.assertThat(sessionTemplateDefault.clients.size).isEqualTo(0)

    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplateDefault.name + " Updated",
      visitRoom = "new room name",
      clients = listOf(UserClientDto(STAFF, true)),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.clients.size).isEqualTo(1)
    Assertions.assertThat(sessionTemplateDto.clients[0]).isEqualTo(UserClientDto(STAFF, true))
  }

  @Test
  fun `when session template with existing clients are updated existing clients are replaced with new clients`() {
    // Given
    val existingClients = listOf(UserClientDto(STAFF, true))
    val newClients = listOf(UserClientDto(PUBLIC, true), UserClientDto(STAFF, false))
    val sessionTemplate = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, clients = existingClients)

    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplate.name + " Updated",
      visitRoom = "new room name",
      includeLocationGroupType = true,
      clients = newClients,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.clients.size).isEqualTo(2)
    Assertions.assertThat(sessionTemplateDto.clients[0]).isEqualTo(UserClientDto(PUBLIC, true))
    Assertions.assertThat(sessionTemplateDto.clients[1]).isEqualTo(UserClientDto(STAFF, false))
  }

  @Test
  fun `when session template with existing clients are updated but new clients not sent through existing clients are not replaced with new clients`() {
    // Given
    val existingClients = listOf(UserClientDto(STAFF, true))
    val newClients = null
    val sessionTemplate = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, clients = existingClients)

    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplate.name + " Updated",
      visitRoom = "new room name",
      includeLocationGroupType = true,
      // newClients is null
      clients = newClients,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.clients.size).isEqualTo(1)
    Assertions.assertThat(sessionTemplateDto.clients[0]).isEqualTo(UserClientDto(STAFF, true))
  }

  @Test
  fun `when session template include groups types are not sent the include flags are not updated`() {
    // Given
    val sessionTemplate = sessionTemplateEntityHelper.create(prisonCode = prison.code, isActive = true, includeLocationGroupType = false, includeCategoryGroupType = false, includeIncentiveGroupType = false)

    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplate.name + " Updated",
      visitRoom = "new room name",
      includeLocationGroupType = null,
      includeCategoryGroupType = null,
      includeIncentiveGroupType = null,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.includeLocationGroupType).isFalse
    Assertions.assertThat(sessionTemplateDto.includeLocationGroupType).isFalse
    Assertions.assertThat(sessionTemplateDto.includeLocationGroupType).isFalse
  }

  @Test
  fun `when session template include location groups type is included on request the include location group type is updated`() {
    // Given

    // session template has includeLocationGroupType as false
    val sessionTemplate = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeLocationGroupType = false,
    )
    Assertions.assertThat(sessionTemplate.includeLocationGroupType).isFalse

    // updating includeLocationGroupType to true
    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplate.name + " Updated",
      includeLocationGroupType = true,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.includeLocationGroupType).isTrue
  }

  @Test
  fun `when session template include category groups type is included on request the include category group type is updated`() {
    // Given
    // session template has includeCategoryGroupType as false
    val sessionTemplate = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeCategoryGroupType = false,
    )
    Assertions.assertThat(sessionTemplate.includeCategoryGroupType).isFalse

    // updating includeCategoryGroupType to true
    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplate.name + " Updated",
      includeCategoryGroupType = true,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.includeCategoryGroupType).isTrue
  }

  @Test
  fun `when session template include incentive groups type is included on request the include category group type is updated`() {
    // Given
    // session template has includeIncentiveGroupType as false

    val sessionTemplate = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeIncentiveGroupType = false,
    )
    Assertions.assertThat(sessionTemplate.includeIncentiveGroupType).isFalse

    // updating includeIncentiveGroupType from false to true
    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplate.name + " Updated",
      includeIncentiveGroupType = true,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.includeIncentiveGroupType).isTrue
  }

  @Test
  fun `when session template updated with 0 incentives and no excluded incentives exist session template is updated`() {
    // Given
    val sessionTemplateWithIncentives = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeIncentiveGroupType = false,
      permittedIncentiveLevels = mutableListOf(),
    )
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithIncentives),
      includeIncentiveGroupType = false,
      incentiveLevelReferences = emptyList(),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithIncentives,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithIncentives.startTime,
      visitEnd = sessionTemplateWithIncentives.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentives.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when existing excluded incentives removed session template is updated`() {
    // Given
    val sessionTemplateWithIncentives = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeIncentiveGroupType = false,
      permittedIncentiveLevels = mutableListOf(incentiveLevelEnhanced),
    )

    // existing incentive levels are being removed
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithIncentives),
      includeIncentiveGroupType = false,
      incentiveLevelReferences = emptyList(),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithIncentives,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithIncentives.startTime,
      visitEnd = sessionTemplateWithIncentives.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentives.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated with same excluded incentives as existing excluded incentives session template is updated`() {
    // Given
    val sessionTemplateWithIncentives = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeIncentiveGroupType = false,
      permittedIncentiveLevels = mutableListOf(incentiveLevelEnhanced),
    )

    // existing incentive levels are being kept
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithIncentives),
      includeIncentiveGroupType = false,
      incentiveLevelReferences = listOf(incentiveLevelEnhanced.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithIncentives,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithIncentives.startTime,
      visitEnd = sessionTemplateWithIncentives.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentives.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated with new excluded incentives then session template is not updated`() {
    // Given
    val sessionTemplateWithIncentives = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      permittedIncentiveLevels = mutableListOf(incentiveLevelEnhanced),
      includeIncentiveGroupType = false,
    )

    // existing B C and D category not included - validation should fail
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithIncentives),
      incentiveLevelReferences = listOf(incentiveLevelEnhanced.reference, incentiveLevelNonEnhanced.reference),
      includeIncentiveGroupType = false,
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithIncentives,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithIncentives.startTime,
      visitEnd = sessionTemplateWithIncentives.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentives.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update incentive levels to the new incentive levels list as all existing incentive levels in session template are not catered for."))
  }

  @Test
  fun `when session template updated with new excluded incentives when there were none then session template is not updated`() {
    // Given
    val sessionTemplateWithIncentives = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      permittedIncentiveLevels = mutableListOf(),
      includeIncentiveGroupType = false,
    )

    // existing B C and D category not included - validation should fail
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithIncentives),
      incentiveLevelReferences = listOf(incentiveLevelEnhanced.reference, incentiveLevelNonEnhanced.reference),
      includeIncentiveGroupType = false,
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithIncentives,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithIncentives.startTime,
      visitEnd = sessionTemplateWithIncentives.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentives.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update incentive levels to the new incentive levels list as all existing incentive levels in session template are not catered for."))
  }

  @Test
  fun `when session template updated with new excluded incentives but no visits exists then session template is updated`() {
    // Given
    val sessionTemplateWithIncentives = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      permittedIncentiveLevels = mutableListOf(),
      includeIncentiveGroupType = false,
    )

    // existing B C and D category not included - validation should fail
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithIncentives),
      incentiveLevelReferences = listOf(incentiveLevelEnhanced.reference, incentiveLevelNonEnhanced.reference),
      includeIncentiveGroupType = false,
    )

    // no future-dated BOOKED visits exist, only past-dated ones
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithIncentives,
      slotDate = LocalDate.now().minusWeeks(1),
      visitStart = sessionTemplateWithIncentives.startTime,
      visitEnd = sessionTemplateWithIncentives.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentives.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated to change included incentive levels to excluded incentive levels then session template is not updated`() {
    // Given
    val sessionTemplateWithIncentiveLevels = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      permittedIncentiveLevels = mutableListOf(incentiveLevelEnhanced),
      includeIncentiveGroupType = true,
    )

    // existing incentive levels are being removed
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithIncentiveLevels),
      includeIncentiveGroupType = false,
      incentiveLevelReferences = listOf(incentiveLevelEnhanced.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithIncentiveLevels,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithIncentiveLevels.startTime,
      visitEnd = sessionTemplateWithIncentiveLevels.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentiveLevels.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update incentive levels to the new incentive levels list as all existing incentive levels in session template are not catered for."))
  }

  @Test
  fun `when included session template changed to excluded incentive levels then session template is not updated`() {
    // Given
    val sessionTemplateWithIncentiveLevels = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeIncentiveGroupType = true,
      permittedIncentiveLevels = mutableListOf(incentiveLevelEnhanced),
    )

    // existing locations are being removed
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithIncentiveLevels),
      includeIncentiveGroupType = false,
      incentiveLevelReferences = emptyList(),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithIncentiveLevels,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithIncentiveLevels.startTime,
      visitEnd = sessionTemplateWithIncentiveLevels.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentiveLevels.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update incentive levels to the new incentive levels list as all existing incentive levels in session template are not catered for."))
  }

  @Test
  fun `when session template updated to exclude a category when no included incentive levels exist on the session template then session template is not updated`() {
    // Given
    val sessionTemplateWithIncentiveLevels = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      permittedIncentiveLevels = mutableListOf(),
      includeIncentiveGroupType = true,
    )

    // categoryAs are being excluded now
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithIncentiveLevels),
      includeIncentiveGroupType = false,
      incentiveLevelReferences = listOf(incentiveLevelNonEnhanced.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithIncentiveLevels,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithIncentiveLevels.startTime,
      visitEnd = sessionTemplateWithIncentiveLevels.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentiveLevels.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update incentive levels to the new incentive levels list as all existing incentive levels in session template are not catered for."))
  }

  @Test
  fun `when session template updated to exclude incentive levels that were included earlier session template is not updated`() {
    // Given
    val sessionTemplateWithIncentiveLevels = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeIncentiveGroupType = true,
      permittedIncentiveLevels = mutableListOf(incentiveLevelEnhanced, incentiveLevelNonEnhanced),
    )

    // category A is being excluded
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithIncentiveLevels),
      includeIncentiveGroupType = false,
      incentiveLevelReferences = listOf(incentiveLevelEnhanced.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithIncentiveLevels,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithIncentiveLevels.startTime,
      visitEnd = sessionTemplateWithIncentiveLevels.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentiveLevels.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update incentive levels to the new incentive levels list as all existing incentive levels in session template are not catered for."))
  }

  @Test
  fun `when session template updated adds different incentive levels than existing included incentive levels then session template is updated`() {
    // Given
    val sessionTemplateWithIncentiveLevels = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      includeIncentiveGroupType = true,
      permittedIncentiveLevels = mutableListOf(incentiveLevelEnhanced),
    )

    // existing locations are being replaced with lower matches - validation should fail
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithIncentiveLevels),
      includeIncentiveGroupType = false,
      incentiveLevelReferences = mutableListOf(incentiveLevelNonEnhanced.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithIncentiveLevels,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithIncentiveLevels.startTime,
      visitEnd = sessionTemplateWithIncentiveLevels.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentiveLevels.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated to remove an incentive level but no future visits exist update is successful`() {
    // Given
    val sessionTemplateWithIncentiveLevels = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      permittedIncentiveLevels = mutableListOf(),
    )

    // existing incentive levels are being replaced with more restrictive - but validation will not fail as there are no future visits
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithIncentiveLevels),
      incentiveLevelReferences = listOf(incentiveLevelEnhanced.reference),
    )

    // all existing visits are past dated
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithIncentiveLevels,
      slotDate = LocalDate.now().minusWeeks(1),
      visitStart = sessionTemplateWithIncentiveLevels.startTime,
      visitEnd = sessionTemplateWithIncentiveLevels.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentiveLevels.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated to change excluded incentive levels to included incentive levels and all sessions are included then session template is updated`() {
    // Given
    val sessionTemplateWithIncentiveLevels = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      permittedIncentiveLevels = mutableListOf(incentiveLevelEnhanced),
      includeIncentiveGroupType = false,
    )

    // all incentive levels are included
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithIncentiveLevels),
      includeIncentiveGroupType = true,
      incentiveLevelReferences = emptyList(),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithIncentiveLevels,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithIncentiveLevels.startTime,
      visitEnd = sessionTemplateWithIncentiveLevels.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentiveLevels.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template updated to change excluded incentive levels to included incentive levels and not all sessions are included then session template is not updated`() {
    // Given
    val sessionTemplateWithIncentiveLevels = sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      isActive = true,
      permittedIncentiveLevels = mutableListOf(incentiveLevelEnhanced),
      includeIncentiveGroupType = false,
    )

    // B C And D included
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateWithIncentiveLevels),
      includeIncentiveGroupType = true,
      incentiveLevelReferences = mutableListOf(incentiveLevelNonEnhanced.reference),
    )

    // future dated BOOKED visit exists
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateWithIncentiveLevels,
      slotDate = LocalDate.now().plusWeeks(1),
      visitStart = sessionTemplateWithIncentiveLevels.startTime,
      visitEnd = sessionTemplateWithIncentiveLevels.endTime,
      visitStatus = VisitStatus.BOOKED,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithIncentiveLevels.reference, dto, validateRequest = true, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").value(Matchers.equalTo("Cannot update incentive levels to the new incentive levels list as all existing incentive levels in session template are not catered for."))
  }

  @Test
  fun `when validate request is passed as false basic validations still fail and BAD_REQUEST is returned`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(
        validFromDate = LocalDate.of(2023, 1, 1),
        validToDate = LocalDate.of(2022, 12, 31),
      ),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateDefault.reference, dto, validateRequest = false, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.developerMessage").value(Matchers.containsString("Session valid to date cannot be less than valid from date"))
  }

  @Test
  fun `when session template updated with reduced valid to date and booked visits affected but validate flag is false update session template is successful`() {
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
      sessionTemplate = sessionTemplateWithValidDates,
      slotDate = visitDate,
      visitStart = LocalTime.of(10, 0),
      visitEnd = LocalTime.of(11, 0),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplateWithValidDates.reference, dto, validateRequest = false, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }
}
