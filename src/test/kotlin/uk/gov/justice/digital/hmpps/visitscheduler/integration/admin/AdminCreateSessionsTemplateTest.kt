package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.assertj.core.api.Assertions
import org.hamcrest.Matchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.ADMIN_SESSION_TEMPLATES_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UserClientDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionCapacityDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionDateRangeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCreateSessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createCreateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Get $ADMIN_SESSION_TEMPLATES_PATH")
class AdminCreateSessionsTemplateTest : IntegrationTestBase() {

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  @BeforeEach
  internal fun setUpTests() {
    prison = prisonEntityHelper.create()
  }

  @Test
  fun `create session template without duplicate location,category,incentive`() {
    // Given
    val allowedPermittedLocations = listOf(AllowedSessionLocationHierarchy("A", "1", "001"))
    val sessionLocationGroup = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = allowedPermittedLocations)

    val categoryAs = listOf(PrisonerCategoryType.A_EXCEPTIONAL, PrisonerCategoryType.A_HIGH, PrisonerCategoryType.A_PROVISIONAL, PrisonerCategoryType.A_STANDARD)
    val sessionCategoryGroup = sessionPrisonerCategoryHelper.create(prisonCode = prison.code, prisonerCategories = categoryAs)

    val enhancedIncentives = listOf(IncentiveLevel.ENHANCED, IncentiveLevel.ENHANCED_2, IncentiveLevel.ENHANCED_3)
    val sessionIncentiveGroup = sessionPrisonerIncentiveLevelHelper.create(prisonCode = prison.code, incentiveLevelList = enhancedIncentives)
    val staffUserClient = UserClientDto(STAFF, active = false)
    val publicUserClient = UserClientDto(PUBLIC, active = true)

    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(LocalDate.now().plusDays(1), null),
      locationGroupReferences = mutableListOf(sessionLocationGroup.reference, sessionLocationGroup.reference),
      categoryGroupReferences = mutableListOf(sessionCategoryGroup.reference, sessionCategoryGroup.reference),
      incentiveLevelGroupReferences = mutableListOf(sessionIncentiveGroup.reference, sessionIncentiveGroup.reference),
      userClients = listOf(staffUserClient, publicUserClient),
      includeLocationGroupType = true,
      includeCategoryGroupType = true,
      includeIncentiveGroupType = false,
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validFromDate).isEqualTo(dto.sessionDateRange.validFromDate)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validToDate).isEqualTo(dto.sessionDateRange.validToDate)
    Assertions.assertThat(sessionTemplateDto.sessionCapacity.closed).isEqualTo(dto.sessionCapacity.closed)
    Assertions.assertThat(sessionTemplateDto.sessionCapacity.open).isEqualTo(dto.sessionCapacity.open)
    Assertions.assertThat(sessionTemplateDto.prisonCode).isEqualTo(dto.prisonCode)
    Assertions.assertThat(sessionTemplateDto.visitRoom).isEqualTo(dto.visitRoom)
    Assertions.assertThat(sessionTemplateDto.sessionTimeSlot.startTime).isEqualTo(dto.sessionTimeSlot.startTime)
    Assertions.assertThat(sessionTemplateDto.sessionTimeSlot.endTime).isEqualTo(dto.sessionTimeSlot.endTime)
    Assertions.assertThat(sessionTemplateDto.dayOfWeek).isEqualTo(dto.dayOfWeek)
    Assertions.assertThat(sessionTemplateDto.permittedLocationGroups.size).isEqualTo(1)
    Assertions.assertThat(sessionTemplateDto.permittedLocationGroups[0].reference).isEqualTo(dto.locationGroupReferences!![0])
    Assertions.assertThat(sessionTemplateDto.weeklyFrequency).isEqualTo(dto.weeklyFrequency)
    Assertions.assertThat(sessionTemplateDto.prisonerCategoryGroups.size).isEqualTo(1)
    Assertions.assertThat(sessionTemplateDto.prisonerCategoryGroups.stream().map { it.categories }).containsExactlyInAnyOrder(categoryAs)
    Assertions.assertThat(sessionTemplateDto.prisonerCategoryGroups[0].reference).isEqualTo(dto.categoryGroupReferences!![0])
    Assertions.assertThat(sessionTemplateDto.prisonerIncentiveLevelGroups.size).isEqualTo(1)
    Assertions.assertThat(sessionTemplateDto.prisonerIncentiveLevelGroups.stream().map { it.incentiveLevels }).containsExactlyInAnyOrder(enhancedIncentives)
    Assertions.assertThat(sessionTemplateDto.prisonerIncentiveLevelGroups[0].reference).isEqualTo(dto.incentiveLevelGroupReferences!![0])
    Assertions.assertThat(sessionTemplateDto.active).isFalse
    Assertions.assertThat(sessionTemplateDto.includeLocationGroupType).isTrue
    Assertions.assertThat(sessionTemplateDto.includeCategoryGroupType).isTrue
    Assertions.assertThat(sessionTemplateDto.includeIncentiveGroupType).isFalse
    Assertions.assertThat(sessionTemplateDto.clients).isEqualTo(listOf(staffUserClient, publicUserClient))
  }

  @Test
  fun `when create session template with clients list empty session template is created but no clients are added`() {
    // Given
    val staffUserClient = UserClientDto(STAFF, active = false)
    val publicUserClient = UserClientDto(PUBLIC, active = true)
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(LocalDate.now().plusDays(1), null),
      userClients = listOf(staffUserClient, publicUserClient),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validFromDate).isEqualTo(dto.sessionDateRange.validFromDate)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validToDate).isEqualTo(dto.sessionDateRange.validToDate)
    Assertions.assertThat(sessionTemplateDto.sessionCapacity.closed).isEqualTo(dto.sessionCapacity.closed)
    Assertions.assertThat(sessionTemplateDto.sessionCapacity.open).isEqualTo(dto.sessionCapacity.open)
    Assertions.assertThat(sessionTemplateDto.prisonCode).isEqualTo(dto.prisonCode)
    Assertions.assertThat(sessionTemplateDto.visitRoom).isEqualTo(dto.visitRoom)
    Assertions.assertThat(sessionTemplateDto.sessionTimeSlot.startTime).isEqualTo(dto.sessionTimeSlot.startTime)
    Assertions.assertThat(sessionTemplateDto.sessionTimeSlot.endTime).isEqualTo(dto.sessionTimeSlot.endTime)
    Assertions.assertThat(sessionTemplateDto.dayOfWeek).isEqualTo(dto.dayOfWeek)
    Assertions.assertThat(sessionTemplateDto.permittedLocationGroups).isEmpty()
    Assertions.assertThat(sessionTemplateDto.weeklyFrequency).isEqualTo(dto.weeklyFrequency)
    Assertions.assertThat(sessionTemplateDto.prisonerCategoryGroups).isEmpty()
    Assertions.assertThat(sessionTemplateDto.prisonerIncentiveLevelGroups).isEmpty()
    Assertions.assertThat(sessionTemplateDto.active).isFalse
    Assertions.assertThat(sessionTemplateDto.clients.size).isEqualTo(2)
    Assertions.assertThat(sessionTemplateDto.clients[0]).isEqualTo(UserClientDto(STAFF, false))
    Assertions.assertThat(sessionTemplateDto.clients[1]).isEqualTo(UserClientDto(PUBLIC, true))
  }

  @Test
  fun `when create session template with clients list populated session template and the user clients are created`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(LocalDate.now().plusDays(1), null),
      userClients = emptyList(),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validFromDate).isEqualTo(dto.sessionDateRange.validFromDate)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validToDate).isEqualTo(dto.sessionDateRange.validToDate)
    Assertions.assertThat(sessionTemplateDto.sessionCapacity.closed).isEqualTo(dto.sessionCapacity.closed)
    Assertions.assertThat(sessionTemplateDto.sessionCapacity.open).isEqualTo(dto.sessionCapacity.open)
    Assertions.assertThat(sessionTemplateDto.prisonCode).isEqualTo(dto.prisonCode)
    Assertions.assertThat(sessionTemplateDto.visitRoom).isEqualTo(dto.visitRoom)
    Assertions.assertThat(sessionTemplateDto.sessionTimeSlot.startTime).isEqualTo(dto.sessionTimeSlot.startTime)
    Assertions.assertThat(sessionTemplateDto.sessionTimeSlot.endTime).isEqualTo(dto.sessionTimeSlot.endTime)
    Assertions.assertThat(sessionTemplateDto.dayOfWeek).isEqualTo(dto.dayOfWeek)
    Assertions.assertThat(sessionTemplateDto.permittedLocationGroups).isEmpty()
    Assertions.assertThat(sessionTemplateDto.weeklyFrequency).isEqualTo(dto.weeklyFrequency)
    Assertions.assertThat(sessionTemplateDto.prisonerCategoryGroups).isEmpty()
    Assertions.assertThat(sessionTemplateDto.prisonerIncentiveLevelGroups).isEmpty()
    Assertions.assertThat(sessionTemplateDto.active).isFalse
    Assertions.assertThat(sessionTemplateDto.clients).isEmpty()
  }

  @Test
  fun `create session template with TAP session as true`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(LocalDate.now().plusDays(1), null),
      includeLocationGroupType = true,
      includeCategoryGroupType = false,
      includeIncentiveGroupType = true,
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validFromDate).isEqualTo(dto.sessionDateRange.validFromDate)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validToDate).isEqualTo(dto.sessionDateRange.validToDate)
    Assertions.assertThat(sessionTemplateDto.sessionCapacity.closed).isEqualTo(dto.sessionCapacity.closed)
    Assertions.assertThat(sessionTemplateDto.sessionCapacity.open).isEqualTo(dto.sessionCapacity.open)
    Assertions.assertThat(sessionTemplateDto.prisonCode).isEqualTo(dto.prisonCode)
    Assertions.assertThat(sessionTemplateDto.visitRoom).isEqualTo(dto.visitRoom)
    Assertions.assertThat(sessionTemplateDto.sessionTimeSlot.startTime).isEqualTo(dto.sessionTimeSlot.startTime)
    Assertions.assertThat(sessionTemplateDto.sessionTimeSlot.endTime).isEqualTo(dto.sessionTimeSlot.endTime)
    Assertions.assertThat(sessionTemplateDto.dayOfWeek).isEqualTo(dto.dayOfWeek)
    Assertions.assertThat(sessionTemplateDto.permittedLocationGroups).isEmpty()
    Assertions.assertThat(sessionTemplateDto.weeklyFrequency).isEqualTo(dto.weeklyFrequency)
    Assertions.assertThat(sessionTemplateDto.prisonerCategoryGroups).isEmpty()
    Assertions.assertThat(sessionTemplateDto.prisonerIncentiveLevelGroups).isEmpty()
    Assertions.assertThat(sessionTemplateDto.includeLocationGroupType).isEqualTo(dto.includeLocationGroupType)
    Assertions.assertThat(sessionTemplateDto.includeCategoryGroupType).isFalse
    Assertions.assertThat(sessionTemplateDto.includeIncentiveGroupType).isTrue
  }

  @Test
  fun `when session template name greater than 100 then validation fails and BAD_REQUEST is returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      name = RandomStringUtils.randomAlphabetic(101),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `when session template prison code is blank then validation fails and BAD_REQUEST is returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      prisonCode = "",
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `when session template end time is less than start time then validation fails and BAD_REQUEST is returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionTimeSlot = SessionTimeSlotDto(LocalTime.of(9, 0), LocalTime.of(8, 0)),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.developerMessage").value(Matchers.containsString("Session end time should be greater than start time"))
  }

  @Test
  fun `when session template end time is same as start time then validation fails and BAD_REQUEST is returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionTimeSlot = SessionTimeSlotDto(LocalTime.of(9, 0), LocalTime.of(9, 0)),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.developerMessage").value(Matchers.containsString("Session end time should be greater than start time"))
  }

  @Test
  fun `when session template end time is greater than start time then session template is created`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      sessionTimeSlot = SessionTimeSlotDto(LocalTime.of(9, 0), LocalTime.of(9, 1)),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template valid to date is less than valid from date then validation fails and BAD_REQUEST is returned`() {
    // Given
    val validFromDate = LocalDate.of(2023, 1, 1)
    val validToDate = LocalDate.of(2022, 12, 31)

    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(validFromDate, validToDate),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.developerMessage").value(Matchers.containsString("Session valid to date cannot be less than valid from date"))
  }

  @Test
  fun `when session template valid to date is same as valid from date then session template is created`() {
    // Given
    val validFromDate = LocalDate.of(2023, 1, 1)
    val validToDate = LocalDate.of(2023, 1, 1)

    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(validFromDate, validToDate),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template valid to date is greater than valid from date then session template is created`() {
    // Given
    val validFromDate = LocalDate.of(2023, 1, 1)
    val validToDate = LocalDate.of(2023, 1, 31)
    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(validFromDate, validToDate),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template valid to date is null then session template is created`() {
    // Given
    val validFromDate = LocalDate.of(2023, 1, 1)
    val validToDate = null

    val dto = createCreateSessionTemplateDto(
      sessionDateRange = SessionDateRangeDto(validFromDate, validToDate),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.active).isFalse
  }

  @Test
  fun `when session template open and closed capacity are zero then validation fails and BAD_REQUEST is returned`() {
    // Given
    val openCapacity = 0
    val closedCapacity = 0

    val dto = createCreateSessionTemplateDto(
      sessionCapacity = SessionCapacityDto(open = openCapacity, closed = closedCapacity),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.developerMessage").value(Matchers.containsString("Either open capacity or closed capacity should be greater than 0"))
  }

  @Test
  fun `when session template open capacity less than zero then validation fails and BAD_REQUEST is returned`() {
    // Given
    val openCapacity = -1
    val closedCapacity = 10

    val dto = createCreateSessionTemplateDto(
      sessionCapacity = SessionCapacityDto(open = openCapacity, closed = closedCapacity),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `when session template closed capacity less than zero then validation fails and BAD_REQUEST is returned`() {
    // Given
    val openCapacity = 10
    val closedCapacity = -1

    val dto = createCreateSessionTemplateDto(
      sessionCapacity = SessionCapacityDto(open = openCapacity, closed = closedCapacity),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `when session template open capacity greater than 0 but closed capacity is 0 then session template is created`() {
    // Given
    val openCapacity = 2
    val closedCapacity = 0

    val dto = createCreateSessionTemplateDto(
      sessionCapacity = SessionCapacityDto(open = openCapacity, closed = closedCapacity),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template closed capacity greater than 0 but open capacity is 0 then session template is created`() {
    // Given
    val openCapacity = 0
    val closedCapacity = 3
    val dto = createCreateSessionTemplateDto(
      sessionCapacity = SessionCapacityDto(open = openCapacity, closed = closedCapacity),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template visit room greater then 255 validation fails and BAD_REQUEST is returned`() {
    // Given
    val dto = createCreateSessionTemplateDto(
      name = RandomStringUtils.randomAlphabetic(256),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
  }
}
