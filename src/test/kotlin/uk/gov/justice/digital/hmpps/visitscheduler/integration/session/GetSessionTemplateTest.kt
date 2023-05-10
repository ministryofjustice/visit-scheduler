package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType.A_EXCEPTIONAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType.A_HIGH
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType.A_PROVISIONAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType.A_STANDARD
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionTemplateRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@DisplayName("Get /visit-session-templates")
class GetSessionTemplateTest(
  @Autowired private val repository: TestSessionTemplateRepository,
) : IntegrationTestBase() {

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  @Test
  fun `all session templates are returned empty list`() {
    // Given
    val prisonCode = "MDI"

    // When
    val responseSpec = webTestClient.get().uri("/visit-session-templates?prisonCode=$prisonCode")
      .headers(setAuthorisation(roles = requiredRole))
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
    sessionTemplateEntityHelper.create(validFromDate = LocalDate.now(), prisonCode = prisonCode)

    // When
    val responseSpec = webTestClient.get().uri("/visit-session-templates?prisonCode=$prisonCode")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(2)
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
    val responseSpec = webTestClient.get().uri("/visit-session-templates/template/${sessionTemplate.reference}")
      .headers(setAuthorisation(roles = requiredRole))
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
    val responseSpec = webTestClient.get().uri("/visit-session-templates/template/${sessionTemplate.reference}")
      .headers(setAuthorisation(roles = requiredRole))
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
    val responseSpec = webTestClient.get().uri("/visit-session-templates/template/${sessionTemplate.reference}")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val sessionTemplateDto = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, SessionTemplateDto::class.java)

    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(sessionTemplate.name)
    Assertions.assertThat(sessionTemplateDto.reference).isEqualTo(sessionTemplate.reference)
    Assertions.assertThat(sessionTemplateDto.prisonCode).isEqualTo(sessionTemplate.prison.code)
    Assertions.assertThat(sessionTemplateDto.startTime).isEqualTo(sessionTemplate.startTime)
    Assertions.assertThat(sessionTemplateDto.endTime).isEqualTo(sessionTemplate.endTime)
    Assertions.assertThat(sessionTemplateDto.validFromDate).isEqualTo(LocalDate.now().format(DateTimeFormatter.ISO_DATE))
    Assertions.assertThat(sessionTemplateDto.visitType).isEqualTo(VisitType.SOCIAL)
    Assertions.assertThat(sessionTemplateDto.visitRoom).isEqualTo(sessionTemplate.visitRoom)
    Assertions.assertThat(sessionTemplateDto.closedCapacity).isEqualTo(sessionTemplate.closedCapacity)
    Assertions.assertThat(sessionTemplateDto.openCapacity).isEqualTo(sessionTemplate.openCapacity)
  }
}
