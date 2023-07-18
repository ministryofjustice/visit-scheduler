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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callUpdateSessionTemplateByReference
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createUpdateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
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
      .jsonPath("$.developerMessage").value(Matchers.equalTo("Cannot update session times for ${sessionTemplate.reference} as there are existing visits associated with this session template!"))
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
      .jsonPath("$.developerMessage").value(Matchers.equalTo("Cannot update session valid from date for ${sessionTemplate.reference} as there are existing visits associated with this session template!"))
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
  fun `when session template updated with reduced valid to date but visits not affected update session template should be successful`() {
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
    verify(visitRepository, times(1)).hasVisitsForSessionTemplate(eq(sessionTemplateWithValidDates.reference), eq(newValidToDate.plusDays(1)))
  }

  @Test
  fun `when session template updated with reduced valid to date but visits affected update session template validation fails`() {
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
      .jsonPath("$.developerMessage").value(Matchers.containsString("Cannot update session valid to date to $newValidToDate for session template - ${sessionTemplateWithValidDates.reference} as there are visits associated with this session template after $newValidToDate."))
    verify(visitRepository, times(1)).hasVisitsForSessionTemplate(eq(sessionTemplateWithValidDates.reference), eq(newValidToDate.plusDays(1)))
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
      .jsonPath("$.developerMessage").value(Matchers.containsString("Cannot update session template weekly frequency from ${sessionTemplate.weeklyFrequency} to $newWeeklyFrequency for ${sessionTemplate.reference} as existing visits for ${sessionTemplate.reference} might be affected!"))
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
      .jsonPath("$.developerMessage").value(Matchers.containsString("Cannot update session template weekly frequency from ${sessionTemplateWithWeeklyFrequencyOf6.weeklyFrequency} to $newWeeklyFrequency for ${sessionTemplateWithWeeklyFrequencyOf6.reference} as existing visits for ${sessionTemplateWithWeeklyFrequencyOf6.reference} might be affected!"))
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
}
