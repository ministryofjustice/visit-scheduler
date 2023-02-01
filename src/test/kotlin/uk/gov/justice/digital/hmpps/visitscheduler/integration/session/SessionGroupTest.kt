package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGetGroupByReference
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGetGroupsByPrisonId
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionLocationGroup

@DisplayName("Get /visit-sessions")
class SessionGroupTest : IntegrationTestBase() {

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  private var prison: Prison = Prison(code = "MDI", active = true)

  private lateinit var sessionGroup1: SessionLocationGroup
  private lateinit var sessionGroup2: SessionLocationGroup

  @BeforeEach
  internal fun setUpTests() {
    prison = prisonEntityHelper.create(prison.code, prison.active)
    val allowedPermittedLocations1 = listOf(AllowedSessionLocationHierarchy("A", "1", "001"))
    sessionGroup1 = sessionLocationGroupHelper.create(prison = prison, prisonHierarchies = allowedPermittedLocations1)
    val allowedPermittedLocations2 = listOf(AllowedSessionLocationHierarchy("B"))
    sessionGroup2 = sessionLocationGroupHelper.create(prison = prison, name = "get 2", prisonHierarchies = allowedPermittedLocations2)
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
    Assertions.assertThat(sessionLocationGroups).hasSize(2)
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
}
