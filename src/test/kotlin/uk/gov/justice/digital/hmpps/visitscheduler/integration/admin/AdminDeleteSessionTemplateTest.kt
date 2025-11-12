package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.ADMIN_SESSION_TEMPLATES_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callDeleteSessionTemplateByReference
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionLocationGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionTemplateRepository
import java.time.LocalDate

@DisplayName("Delete session template tests $ADMIN_SESSION_TEMPLATES_PATH")
class AdminDeleteSessionTemplateTest(
  @param:Autowired private val testTemplateRepository: TestSessionTemplateRepository,
  @param:Autowired val testSessionLocationGroupRepository: TestSessionLocationGroupRepository,
) : IntegrationTestBase() {

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  private lateinit var sessionGroup1: SessionLocationGroup
  private lateinit var sessionGroup2: SessionLocationGroup
  private lateinit var sessionTemplate1: SessionTemplate

  @BeforeEach
  internal fun setUp() {
    sessionTemplate1 = sessionTemplateEntityHelper.create(isActive = false)
    val allowedPermittedLocations1 = listOf(AllowedSessionLocationHierarchy("A", "1", "001"))
    sessionGroup1 = sessionLocationGroupHelper.create(prisonCode = sessionTemplate1.prison.code, prisonHierarchies = allowedPermittedLocations1)
    val allowedPermittedLocations2 = listOf(AllowedSessionLocationHierarchy("B"))
    sessionGroup2 = sessionLocationGroupHelper.create(prisonCode = sessionTemplate1.prison.code, name = "get 2", prisonHierarchies = allowedPermittedLocations2)

    sessionTemplate1.permittedSessionLocationGroups.add(sessionGroup1)
    sessionTemplate1.permittedSessionLocationGroups.add(sessionGroup2)

    testTemplateRepository.saveAndFlush(sessionTemplate1)
  }

  @Test
  fun `delete session template by reference test successfully`() {
    // Given
    val reference = sessionTemplate1.reference
    val grp1Id = sessionTemplate1.permittedSessionLocationGroups[0].id
    val grp2Id = sessionTemplate1.permittedSessionLocationGroups[1].id

    // When
    val responseSpec = callDeleteSessionTemplateByReference(webTestClient, reference, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
    responseSpec.expectBody()
      .jsonPath("$").isEqualTo("Session Template Deleted $reference!")

    Assertions.assertThat(testTemplateRepository.hasSessionTemplate(sessionTemplate1.id)).isFalse
    Assertions.assertThat(testSessionLocationGroupRepository.hasById(grp1Id)).isTrue
    Assertions.assertThat(testSessionLocationGroupRepository.hasById(grp2Id)).isTrue
    Assertions.assertThat(testSessionLocationGroupRepository.hasJoinTable(sessionTemplate1.id, sessionGroup1.id)).isFalse
    Assertions.assertThat(testSessionLocationGroupRepository.hasJoinTable(sessionTemplate1.id, sessionGroup2.id)).isFalse
  }

  @Test
  fun `cannot delete session template by reference with existing visits test`() {
    // Given
    visitEntityHelper.create(visitStatus = BOOKED, sessionTemplate = sessionTemplate1)

    val reference = sessionTemplate1.reference
    sessionTemplate1.permittedSessionLocationGroups[0].id
    sessionTemplate1.permittedSessionLocationGroups[1].id

    // When
    val responseSpec = callDeleteSessionTemplateByReference(webTestClient, reference, setAuthorisation(roles = adminRole))

    // Then
    responseSpec
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").isEqualTo("Cannot delete session template $reference with existing visits!")
  }

  @Test
  fun `cannot delete active session template `() {
    // Given
    val activeSessionTemplate = sessionTemplateEntityHelper.create(validFromDate = LocalDate.now())

    val reference = activeSessionTemplate.reference

    // When
    val responseSpec = callDeleteSessionTemplateByReference(webTestClient, reference, setAuthorisation(roles = adminRole))

    // Then
    responseSpec
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").isEqualTo("Cannot delete session template $reference since it is active!")
  }

  @Test
  fun `when session template reference does not exist then error is thrown`() {
    // Given
    val reference = "does-not-exist"

    // When
    val responseSpec = callDeleteSessionTemplateByReference(webTestClient, reference, setAuthorisation(roles = adminRole))

    // Then
    responseSpec
      .expectStatus().isNotFound
      .expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Template reference:$reference not found")
  }
}
