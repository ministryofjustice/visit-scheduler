package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.ADMIN_SESSION_TEMPLATES_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.SESSION_TEMPLATE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerCategoryType.A_EXCEPTIONAL
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerCategoryType.A_HIGH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerCategoryType.A_PROVISIONAL
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerCategoryType.A_STANDARD
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionTemplateRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@DisplayName("Get /visit-session-templates")
class AdminGetSessionTemplateTest(
  @Autowired private val repository: TestSessionTemplateRepository,
) : IntegrationTestBase() {

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  @BeforeEach
  internal fun setUpTests() {
    deleteEntityHelper.deleteAll()
  }

  @Test
  fun `all session templates are returned empty list`() {
    // Given
    val prisonCode = "MDI"

    // When
    val responseSpec = webTestClient.get().uri("$ADMIN_SESSION_TEMPLATES_PATH?prisonCode=$prisonCode&rangeType=ALL")
      .headers(setAuthorisation(roles = adminRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(0)
  }

  @Test
  fun `all session templates are returned`() {
    // Given
    val prisonCode = "MDI"

    sessionTemplateEntityHelper.create(validFromDate = LocalDate.now(), prisonCode = prisonCode)
    sessionTemplateEntityHelper.create(validFromDate = LocalDate.now(), prisonCode = prisonCode, includeLocationGroupType = true)

    // When
    val responseSpec = webTestClient.get().uri("$ADMIN_SESSION_TEMPLATES_PATH?prisonCode=$prisonCode&rangeType=ALL")
      .headers(setAuthorisation(roles = adminRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(2)
  }

  @Test
  fun `all active or future session templates are returned`() {
    // Given
    val prisonCode = "MDI"

    sessionTemplateEntityHelper.create(name = "fail_", validFromDate = LocalDate.now().minusDays(100), validToDate = LocalDate.now().minusDays(1), prisonCode = prisonCode)
    sessionTemplateEntityHelper.create(name = "pass1_", validFromDate = LocalDate.now().minusDays(90), prisonCode = prisonCode)
    sessionTemplateEntityHelper.create(name = "pass2_", validFromDate = LocalDate.now(), prisonCode = prisonCode)
    sessionTemplateEntityHelper.create(name = "pass3_", validFromDate = LocalDate.now().plusDays(10), prisonCode = prisonCode)
    sessionTemplateEntityHelper.create(name = "pass4_", validFromDate = LocalDate.now().plusDays(11), validToDate = LocalDate.now().plusDays(15), prisonCode = prisonCode, includeLocationGroupType = false)

    // When
    val responseSpec = webTestClient.get().uri("$ADMIN_SESSION_TEMPLATES_PATH?prisonCode=$prisonCode&rangeType=CURRENT_OR_FUTURE")
      .headers(setAuthorisation(roles = adminRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDtos = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, Array<SessionTemplateDto>::class.java)
    Assertions.assertThat(sessionTemplateDtos).hasSize(4)
    Assertions.assertThat(sessionTemplateDtos[0].name).isEqualTo("pass1_FRIDAYFRIDAY")
    Assertions.assertThat(sessionTemplateDtos[0].includeLocationGroupType).isTrue()
    Assertions.assertThat(sessionTemplateDtos[1].name).isEqualTo("pass2_FRIDAYFRIDAY")
    Assertions.assertThat(sessionTemplateDtos[1].includeLocationGroupType).isTrue()
    Assertions.assertThat(sessionTemplateDtos[2].name).isEqualTo("pass3_FRIDAYFRIDAY")
    Assertions.assertThat(sessionTemplateDtos[2].includeLocationGroupType).isTrue()
    Assertions.assertThat(sessionTemplateDtos[3].name).isEqualTo("pass4_FRIDAYFRIDAY")
    Assertions.assertThat(sessionTemplateDtos[3].includeLocationGroupType).isFalse()
  }

  @Test
  fun `all historic session templates are returned`() {
    // Given
    val prisonCode = "MDI"

    sessionTemplateEntityHelper.create(name = "fail1_", validFromDate = LocalDate.now(), prisonCode = prisonCode)
    sessionTemplateEntityHelper.create(name = "fail2_", validFromDate = LocalDate.now().plusDays(10), prisonCode = prisonCode)
    sessionTemplateEntityHelper.create(name = "fail3_", validFromDate = LocalDate.now().plusDays(10), validToDate = LocalDate.now().plusDays(15), prisonCode = prisonCode)
    sessionTemplateEntityHelper.create(name = "pass1_", validFromDate = LocalDate.now().minusDays(100), validToDate = LocalDate.now().minusDays(1), prisonCode = prisonCode)
    sessionTemplateEntityHelper.create(name = "fail4_", validFromDate = LocalDate.now().plusDays(10), prisonCode = prisonCode)

    // When
    val responseSpec = webTestClient.get().uri("$ADMIN_SESSION_TEMPLATES_PATH?prisonCode=$prisonCode&rangeType=HISTORIC")
      .headers(setAuthorisation(roles = adminRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDtos = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, Array<SessionTemplateDto>::class.java)
    Assertions.assertThat(sessionTemplateDtos).hasSize(1)
    Assertions.assertThat(sessionTemplateDtos[0].name).isEqualTo("pass1_FRIDAYFRIDAY")
  }

  @Test
  fun `Session templates are returned with permittedSessionLocations`() {
    // Given
    val sessionLocationGroup = sessionLocationGroupHelper.create()
    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = LocalDate.now(),
      permittedLocationGroups = mutableListOf(sessionLocationGroup),
    )

    // When
    val responseSpec = webTestClient.get().uri("$ADMIN_SESSION_TEMPLATES_PATH/template/${sessionTemplate.reference}")
      .headers(setAuthorisation(roles = adminRole))
      .exchange()

    // Then
    val sessionTemplateDto = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, SessionTemplateDto::class.java)

    val group = sessionTemplateDto.permittedLocationGroups[0]
    val permittedLocation = group.locations[0]

    Assertions.assertThat(group.locations).hasSize(1)
    Assertions.assertThat(permittedLocation.levelOneCode).isEqualTo(sessionLocationGroup.sessionLocations[0].levelOneCode)
    Assertions.assertThat(permittedLocation.levelTwoCode).isEqualTo(sessionLocationGroup.sessionLocations[0].levelTwoCode)
    Assertions.assertThat(permittedLocation.levelThreeCode).isEqualTo(sessionLocationGroup.sessionLocations[0].levelThreeCode)
    Assertions.assertThat(permittedLocation.levelFourCode).isEqualTo(sessionLocationGroup.sessionLocations[0].levelFourCode)
  }

  @Test
  fun `Session templates are returned with permitted category groups`() {
    // Given
    val sessionCategoryGroup = sessionPrisonerCategoryHelper.create()
    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = LocalDate.now(),
      permittedCategories = mutableListOf(sessionCategoryGroup),
    )

    // When
    val responseSpec = webTestClient.get().uri("$SESSION_TEMPLATE_PATH/${sessionTemplate.reference}")
      .headers(setAuthorisation(roles = adminRole))
      .exchange()

    // Then
    val sessionTemplateDto = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, SessionTemplateDto::class.java)

    val prisonerCategoryGroups = sessionTemplateDto.prisonerCategoryGroups
    Assertions.assertThat(prisonerCategoryGroups).hasSize(1)

    val prisonerCategories = prisonerCategoryGroups[0].categories
    Assertions.assertThat(prisonerCategories).hasSize(4)
    val expectedCategories = mutableListOf(A_PROVISIONAL, A_STANDARD, A_HIGH, A_EXCEPTIONAL)
    Assertions.assertThat(prisonerCategories).containsAll(expectedCategories)
  }

  @Test
  fun `session templates are returned by reference`() {
    // Given
    val sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = LocalDate.now())

    repository.save(sessionTemplate)

    // When
    val responseSpec = webTestClient.get().uri("$SESSION_TEMPLATE_PATH/${sessionTemplate.reference}")
      .headers(setAuthorisation(roles = adminRole))
      .exchange()

    // Then
    val sessionTemplateDto = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, SessionTemplateDto::class.java)

    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(sessionTemplate.name)
    Assertions.assertThat(sessionTemplateDto.reference).isEqualTo(sessionTemplate.reference)
    Assertions.assertThat(sessionTemplateDto.prisonCode).isEqualTo(sessionTemplate.prison.code)
    Assertions.assertThat(sessionTemplateDto.sessionTimeSlot.startTime).isEqualTo(sessionTemplate.startTime)
    Assertions.assertThat(sessionTemplateDto.sessionTimeSlot.endTime).isEqualTo(sessionTemplate.endTime)
    Assertions.assertThat(sessionTemplateDto.sessionDateRange.validFromDate).isEqualTo(LocalDate.now().format(DateTimeFormatter.ISO_DATE))
    Assertions.assertThat(sessionTemplateDto.visitType).isEqualTo(VisitType.SOCIAL)
    Assertions.assertThat(sessionTemplateDto.visitRoom).isEqualTo(sessionTemplate.visitRoom)
    Assertions.assertThat(sessionTemplateDto.sessionCapacity.closed).isEqualTo(sessionTemplate.closedCapacity)
    Assertions.assertThat(sessionTemplateDto.sessionCapacity.open).isEqualTo(sessionTemplate.openCapacity)
  }
}
