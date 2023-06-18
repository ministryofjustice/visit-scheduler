package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.ADMIN_SESSION_TEMPLATES_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateCapacity
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateTime
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateValidDate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCreateSessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Get $ADMIN_SESSION_TEMPLATES_PATH")
class AdminCreateSessionsTemplateTest : IntegrationTestBase() {

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  private var prison: Prison = Prison(code = "MDI", active = true)

  @BeforeEach
  internal fun setUpTests() {
    prison = prisonEntityHelper.create(prison.code, prison.active)
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

    val dto = createSessionTemplateDto(
      sessionTemplateValidDate = SessionTemplateValidDate(LocalDate.now().plusDays(1), null),
      locationGroupReferences = mutableListOf(sessionLocationGroup.reference, sessionLocationGroup.reference),
      categoryGroupReferences = mutableListOf(sessionCategoryGroup.reference, sessionCategoryGroup.reference),
      incentiveLevelGroupReferences = mutableListOf(sessionIncentiveGroup.reference, sessionIncentiveGroup.reference),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.validFromDate).isEqualTo(dto.sessionTemplateValidDate.validFromDate)
    Assertions.assertThat(sessionTemplateDto.validToDate).isEqualTo(dto.sessionTemplateValidDate.validToDate)
    Assertions.assertThat(sessionTemplateDto.closedCapacity).isEqualTo(dto.sessionTemplateCapacity.closedCapacity)
    Assertions.assertThat(sessionTemplateDto.openCapacity).isEqualTo(dto.sessionTemplateCapacity.openCapacity)
    Assertions.assertThat(sessionTemplateDto.prisonCode).isEqualTo(dto.prisonCode)
    Assertions.assertThat(sessionTemplateDto.visitRoom).isEqualTo(dto.visitRoom)
    Assertions.assertThat(sessionTemplateDto.startTime).isEqualTo(dto.sessionTemplateTime.startTime)
    Assertions.assertThat(sessionTemplateDto.endTime).isEqualTo(dto.sessionTemplateTime.endTime)
    Assertions.assertThat(sessionTemplateDto.dayOfWeek).isEqualTo(dto.dayOfWeek)
    Assertions.assertThat(sessionTemplateDto.permittedLocationGroups.size).isEqualTo(1)
    Assertions.assertThat(sessionTemplateDto.permittedLocationGroups[0].reference).isEqualTo(dto.locationGroupReferences!![0])
    Assertions.assertThat(sessionTemplateDto.biWeekly).isEqualTo(dto.biWeekly)
    Assertions.assertThat(sessionTemplateDto.prisonerCategoryGroups.size).isEqualTo(1)
    Assertions.assertThat(sessionTemplateDto.prisonerCategoryGroups.stream().map { it.categories }).containsExactlyInAnyOrder(categoryAs)
    Assertions.assertThat(sessionTemplateDto.prisonerCategoryGroups[0].reference).isEqualTo(dto.categoryGroupReferences!![0])
    Assertions.assertThat(sessionTemplateDto.prisonerIncentiveLevelGroups.size).isEqualTo(1)
    Assertions.assertThat(sessionTemplateDto.prisonerIncentiveLevelGroups.stream().map { it.incentiveLevels }).containsExactlyInAnyOrder(enhancedIncentives)
    Assertions.assertThat(sessionTemplateDto.prisonerIncentiveLevelGroups[0].reference).isEqualTo(dto.incentiveLevelGroupReferences!![0])
  }

  @Test
  fun `when session template name greater than 100 then validation fails and BAD_REQUEST is returned`() {
    // Given
    val dto = createSessionTemplateDto(
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
    val dto = createSessionTemplateDto(
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
    val dto = createSessionTemplateDto(
      sessionTemplateTime = SessionTemplateTime(LocalTime.of(9, 0), LocalTime.of(8, 0)),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `when session template end time is same as start time then validation fails and BAD_REQUEST is returned`() {
    // Given
    val dto = createSessionTemplateDto(
      sessionTemplateTime = SessionTemplateTime(LocalTime.of(9, 0), LocalTime.of(9, 0)),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `when session template end time is greater than start time then session template is created`() {
    // Given
    val dto = createSessionTemplateDto(
      sessionTemplateTime = SessionTemplateTime(LocalTime.of(9, 0), LocalTime.of(9, 1)),
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

    val dto = createSessionTemplateDto(
      sessionTemplateValidDate = SessionTemplateValidDate(validFromDate, validToDate),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `when session template valid to date is same as valid from date then session template is created`() {
    // Given
    val validFromDate = LocalDate.of(2023, 1, 1)
    val validToDate = LocalDate.of(2023, 1, 1)

    val dto = createSessionTemplateDto(
      sessionTemplateValidDate = SessionTemplateValidDate(validFromDate, validToDate),
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
    val dto = createSessionTemplateDto(
      sessionTemplateValidDate = SessionTemplateValidDate(validFromDate, validToDate),
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

    val dto = createSessionTemplateDto(
      sessionTemplateValidDate = SessionTemplateValidDate(validFromDate, validToDate),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template open and closed capacity are zero then validation fails and BAD_REQUEST is returned`() {
    // Given
    val openCapacity = 0
    val closedCapacity = 0

    val dto = createSessionTemplateDto(
      sessionTemplateCapacity = SessionTemplateCapacity(openCapacity, closedCapacity),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `when session template open capacity less than zero then validation fails and BAD_REQUEST is returned`() {
    // Given
    val openCapacity = -1
    val closedCapacity = 10

    val dto = createSessionTemplateDto(
      sessionTemplateCapacity = SessionTemplateCapacity(openCapacity, closedCapacity),
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

    val dto = createSessionTemplateDto(
      sessionTemplateCapacity = SessionTemplateCapacity(openCapacity, closedCapacity),
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

    val dto = createSessionTemplateDto(
      sessionTemplateCapacity = SessionTemplateCapacity(openCapacity, closedCapacity),
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
    val dto = createSessionTemplateDto(
      sessionTemplateCapacity = SessionTemplateCapacity(openCapacity, closedCapacity),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session template visit room greater then 255 validation fails and BAD_REQUEST is returned`() {
    // Given
    val dto = createSessionTemplateDto(
      name = RandomStringUtils.randomAlphabetic(256),
    )

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
  }
}
