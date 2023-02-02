package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callDeleteGroupByReference
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGetGroupByReference
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGetGroupsByPrisonId
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionLocationGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionLocationGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionTemplateRepository
import java.time.LocalDate

@DisplayName("Get /visit-sessions")
class SessionGroupTest(
  @Autowired val testTemplateRepository: TestSessionTemplateRepository,
  @Autowired val testSessionLocationGroupRepository: TestSessionLocationGroupRepository,
) : IntegrationTestBase() {

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  private var prison: Prison = Prison(code = "MDI", active = true)

  private lateinit var sessionTemplateWithGrps: SessionTemplate

  private lateinit var sessionGroup1: SessionLocationGroup
  private lateinit var sessionGroup2: SessionLocationGroup
  private lateinit var sessionGroupWithNoTemplate: SessionLocationGroup

  @BeforeEach
  internal fun setUpTests() {

    sessionTemplateWithGrps = sessionTemplateEntityHelper.create(validFromDate = LocalDate.now())

    prison = prisonEntityHelper.create(prison.code, prison.active)
    val allowedPermittedLocations1 = listOf(AllowedSessionLocationHierarchy("A", "1", "001"))
    sessionGroup1 = sessionLocationGroupHelper.create(prison = prison, prisonHierarchies = allowedPermittedLocations1)
    val allowedPermittedLocations2 = listOf(AllowedSessionLocationHierarchy("B"))
    sessionGroup2 = sessionLocationGroupHelper.create(prison = prison, name = "get 2", prisonHierarchies = allowedPermittedLocations2)

    sessionTemplateWithGrps.permittedSessionGroups.add(sessionGroup1)
    sessionTemplateWithGrps.permittedSessionGroups.add(sessionGroup2)

    testTemplateRepository.saveAndFlush(sessionTemplateWithGrps)

    val allowedPermittedLocations3 = listOf(AllowedSessionLocationHierarchy("B"))
    sessionGroupWithNoTemplate = sessionLocationGroupHelper.create(prison = prison, name = "get 3", prisonHierarchies = allowedPermittedLocations3)
  }

  @Test
  fun `get session groups by prison id test`() {

    // Given
    val prisonCode = prison.code
    // When
    val responseSpec = callGetGroupsByPrisonId(webTestClient, prisonCode, setAuthorisation(roles = requiredRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionLocationGroups = getSessionLocationGroups(responseSpec)
    Assertions.assertThat(sessionLocationGroups).hasSize(3)
    with(sessionLocationGroups[0]) {
      Assertions.assertThat(name).isEqualTo(sessionGroup1.name)
      Assertions.assertThat(reference).isEqualTo(sessionGroup1.reference)
      Assertions.assertThat(locations.size).isEqualTo(1)
      with(sessionGroup1.sessionLocations[0]) {
        Assertions.assertThat(locations[0].levelOneCode).isEqualTo(levelOneCode)
        Assertions.assertThat(locations[0].levelTwoCode).isEqualTo(levelTwoCode)
        Assertions.assertThat(locations[0].levelThreeCode).isEqualTo(levelThreeCode)
        Assertions.assertThat(locations[0].levelFourCode).isEqualTo(levelFourCode)
      }
    }
    with(sessionLocationGroups[1]) {
      Assertions.assertThat(name).isEqualTo(sessionGroup2.name)
      Assertions.assertThat(reference).isEqualTo(sessionGroup2.reference)
      Assertions.assertThat(locations.size).isEqualTo(1)
      with(sessionGroup2.sessionLocations[0]) {
        Assertions.assertThat(locations[0].levelOneCode).isEqualTo(levelOneCode)
        Assertions.assertThat(locations[0].levelTwoCode).isNull()
        Assertions.assertThat(locations[0].levelThreeCode).isNull()
        Assertions.assertThat(locations[0].levelFourCode).isNull()
      }
    }
    with(sessionLocationGroups[2]) {
      Assertions.assertThat(name).isEqualTo(sessionGroupWithNoTemplate.name)
      Assertions.assertThat(reference).isEqualTo(sessionGroupWithNoTemplate.reference)
      Assertions.assertThat(locations.size).isEqualTo(1)
      with(sessionGroupWithNoTemplate.sessionLocations[0]) {
        Assertions.assertThat(locations[0].levelOneCode).isEqualTo(levelOneCode)
        Assertions.assertThat(locations[0].levelTwoCode).isNull()
        Assertions.assertThat(locations[0].levelThreeCode).isNull()
        Assertions.assertThat(locations[0].levelFourCode).isNull()
      }
    }
  }

  @Test
  fun `get session group by reference test`() {

    // Given
    val reference = sessionGroup1.reference
    // When
    val responseSpec = callGetGroupByReference(webTestClient, reference, setAuthorisation(roles = requiredRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionLocationGroup = getSessionLocationGroup(responseSpec)
    with(sessionLocationGroup) {
      Assertions.assertThat(name).isEqualTo(sessionGroup1.name)
      Assertions.assertThat(reference).isEqualTo(sessionGroup1.reference)
      Assertions.assertThat(locations.size).isEqualTo(1)
      with(sessionGroup1.sessionLocations[0]) {
        Assertions.assertThat(locations[0].levelOneCode).isEqualTo(levelOneCode)
        Assertions.assertThat(locations[0].levelTwoCode).isEqualTo(levelTwoCode)
        Assertions.assertThat(locations[0].levelThreeCode).isEqualTo(levelThreeCode)
        Assertions.assertThat(locations[0].levelFourCode).isEqualTo(levelFourCode)
      }
    }
  }

  @Test
  fun `delete session group by reference test`() {

    // Given
    val reference = sessionGroupWithNoTemplate.reference

    // When
    val responseSpec = callDeleteGroupByReference(webTestClient, reference, setAuthorisation(roles = requiredRole))

    // Then
    responseSpec.expectStatus().isOk
    responseSpec.expectBody()
      .jsonPath("$").isEqualTo("Session location group Deleted $reference!")

    Assertions.assertThat(testSessionLocationGroupRepository.hasById(sessionGroupWithNoTemplate.id)).isFalse
  }

  @Test
  fun `delete session group when session template uses the group`() {

    // Given
    val reference = sessionGroup1.reference

    // When
    val responseSpec = callDeleteGroupByReference(webTestClient, reference, setAuthorisation(roles = requiredRole))

    // Then
    responseSpec.expectStatus().is5xxServerError
  }
}
