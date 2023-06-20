package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.assertj.core.api.Assertions
import org.hamcrest.Matchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
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
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Update $ADMIN_SESSION_TEMPLATES_PATH")
class AdminUpdateSessionTemplateTest : IntegrationTestBase() {

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  private var prison: Prison = Prison(code = "MDI", active = true)
  private lateinit var sessionTemplate: SessionTemplate

  @BeforeEach
  internal fun setUpTests() {
    prison = prisonEntityHelper.create(prison.code, prison.active)
    sessionTemplate = sessionTemplateEntityHelper.create(prisonCode = prison.code)
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
      sessionDateRangeDto = SessionDateRangeDto(
        validFromDate = sessionTemplate.validFromDate.plusDays(1),
        validToDate = sessionTemplate.validToDate?.plusDays(10),
      ),
      sessionCapacity = SessionCapacityDto(
        closed = sessionTemplate.closedCapacity + 1,
        open = sessionTemplate.openCapacity + 1,
      ),
      sessionTimeSlotDto = SessionTimeSlotDto(
        startTime = sessionTemplate.startTime.plusHours(1),
        endTime = sessionTemplate.endTime.plusHours(2),
      ),
      dayOfWeek = sessionTemplate.dayOfWeek.minus(1),
      locationGroupReferences = mutableListOf(sessionGroup.reference, sessionGroup.reference),
      categoryGroupReferences = mutableListOf(sessionCategoryGroup.reference, sessionCategoryGroup.reference),
      incentiveLevelGroupReferences = mutableListOf(sessionIncentiveGroup.reference, sessionIncentiveGroup.reference),
      biWeekly = !sessionTemplate.biWeekly,
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
    Assertions.assertThat(sessionTemplateDto.biWeekly).isEqualTo(dto.biWeekly)
    Assertions.assertThat(sessionTemplateDto.prisonerCategoryGroups.size).isEqualTo(1)
    Assertions.assertThat(sessionTemplateDto.prisonerCategoryGroups.stream().map { it.categories }).containsExactlyInAnyOrder(categoryAs)
    Assertions.assertThat(sessionTemplateDto.prisonerCategoryGroups[0].reference).isEqualTo(dto.categoryGroupReferences!![0])
    Assertions.assertThat(sessionTemplateDto.prisonerIncentiveLevelGroups.size).isEqualTo(1)
    Assertions.assertThat(sessionTemplateDto.prisonerIncentiveLevelGroups.stream().map { it.incentiveLevels }).containsExactlyInAnyOrder(nonEnhancedIncentives)
    Assertions.assertThat(sessionTemplateDto.prisonerIncentiveLevelGroups[0].reference).isEqualTo(dto.incentiveLevelGroupReferences!![0])
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
      sessionTimeSlotDto = SessionTimeSlotDto(
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
      sessionTimeSlotDto = SessionTimeSlotDto(
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
      sessionTimeSlotDto = null,
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
      sessionTimeSlotDto = SessionTimeSlotDto(
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
      sessionDateRangeDto = SessionDateRangeDto(
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
      sessionDateRangeDto = SessionDateRangeDto(
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
      sessionDateRangeDto = SessionDateRangeDto(
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
      sessionDateRangeDto = null,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
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
  fun `when session template bi weekly is null then session template is created`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      biWeekly = null,
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }
}