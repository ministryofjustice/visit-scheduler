package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callDeleteSessionTemplateByReference
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionLocationGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionTemplateRepository
import java.time.LocalDate

@DisplayName("Delete session template tests")
class DeleteSessionTemplateTest(
  @Autowired private val testTemplateRepository: TestSessionTemplateRepository,
  @Autowired val testSessionLocationGroupRepository: TestSessionLocationGroupRepository,
) : IntegrationTestBase() {

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  private lateinit var sessionTemplate: SessionTemplate
  private lateinit var sessionGroup1: SessionLocationGroup
  private lateinit var sessionGroup2: SessionLocationGroup

  @BeforeEach
  internal fun setUp() {
    sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = LocalDate.now())

    val allowedPermittedLocations1 = listOf(AllowedSessionLocationHierarchy("A", "1", "001"))
    sessionGroup1 = sessionLocationGroupHelper.create(prisonCode = sessionTemplate.prison.code, prisonHierarchies = allowedPermittedLocations1)
    val allowedPermittedLocations2 = listOf(AllowedSessionLocationHierarchy("B"))
    sessionGroup2 = sessionLocationGroupHelper.create(prisonCode = sessionTemplate.prison.code, name = "get 2", prisonHierarchies = allowedPermittedLocations2)

    sessionTemplate.permittedSessionLocationGroups.add(sessionGroup1)
    sessionTemplate.permittedSessionLocationGroups.add(sessionGroup2)

    testTemplateRepository.saveAndFlush(sessionTemplate)
  }

  @Test
  fun `delete session template by reference test successfully`() {
    // Given
    val reference = sessionTemplate.reference
    val grp1Id = sessionTemplate.permittedSessionLocationGroups[0].id
    val grp2Id = sessionTemplate.permittedSessionLocationGroups[1].id

    // When
    val responseSpec = callDeleteSessionTemplateByReference(webTestClient, reference, setAuthorisation(roles = requiredRole))

    // Then
    responseSpec.expectStatus().isOk
    responseSpec.expectBody()
      .jsonPath("$").isEqualTo("Session Template Deleted $reference!")

    Assertions.assertThat(testTemplateRepository.hasSessionTemplate(sessionTemplate.id)).isFalse
    Assertions.assertThat(testSessionLocationGroupRepository.hasById(grp1Id)).isTrue
    Assertions.assertThat(testSessionLocationGroupRepository.hasById(grp2Id)).isTrue
    Assertions.assertThat(testSessionLocationGroupRepository.hasJoinTable(sessionTemplate.id, sessionGroup1.id)).isFalse
    Assertions.assertThat(testSessionLocationGroupRepository.hasJoinTable(sessionTemplate.id, sessionGroup2.id)).isFalse
  }
}
