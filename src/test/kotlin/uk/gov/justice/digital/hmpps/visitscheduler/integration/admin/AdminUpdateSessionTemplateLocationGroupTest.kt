package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.LOCATION_GROUP_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callUpdateLocationSessionGroupByReference
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createPermittedSessionLocationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createUpdateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup

@DisplayName("Put update session location groups $LOCATION_GROUP_ADMIN_PATH")
class AdminUpdateSessionTemplateLocationGroupTest : IntegrationTestBase() {

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  private var prison: Prison = Prison(code = "MDI", active = true)

  private lateinit var sessionGroup: SessionLocationGroup

  @BeforeEach
  internal fun setUpTests() {
    prison = prisonEntityHelper.create(prison.code, prison.active)

    val allowedPermittedLocations = listOf(AllowedSessionLocationHierarchy("A", "1", "001"))
    sessionGroup = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = allowedPermittedLocations)
  }

  @Test
  fun `update session group with out duplicates test`() {
    // Given
    val locationDto = createPermittedSessionLocationDto("C", "L1", "S1", "001")
    val duplicateLocationDto = createPermittedSessionLocationDto("C", "L1", "S1", "001")
    val dto = createUpdateLocationGroupDto(permittedSessionLocations = mutableListOf(locationDto, duplicateLocationDto))

    // When
    val responseSpec = callUpdateLocationSessionGroupByReference(webTestClient, sessionGroup.reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionLocationGroupDto = getSessionLocationGroup(responseSpec)
    Assertions.assertThat(sessionLocationGroupDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionLocationGroupDto.reference).isEqualTo(sessionGroup.reference)
    Assertions.assertThat(sessionLocationGroupDto.locations.size).isEqualTo(1)
    Assertions.assertThat(sessionLocationGroupDto.locations[0].levelOneCode).isEqualTo(locationDto.levelOneCode)
    Assertions.assertThat(sessionLocationGroupDto.locations[0].levelTwoCode).isEqualTo(locationDto.levelTwoCode)
    Assertions.assertThat(sessionLocationGroupDto.locations[0].levelThreeCode).isEqualTo(locationDto.levelThreeCode)
    Assertions.assertThat(sessionLocationGroupDto.locations[0].levelFourCode).isEqualTo(locationDto.levelFourCode)
  }

  @Test
  fun `exception thrown when reference not found during update session group test`() {
    // Given
    val locationDto = createPermittedSessionLocationDto("C", "L1", "S1", "001")
    val dto = createUpdateLocationGroupDto(permittedSessionLocations = mutableListOf(locationDto))
    val reference = "Ref1234"

    // When
    val responseSpec = callUpdateLocationSessionGroupByReference(webTestClient, reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isNotFound
    val errorResponse = getErrorResponse(responseSpec)
    Assertions.assertThat(errorResponse.userMessage).isEqualTo("Not found")
    Assertions.assertThat(errorResponse.developerMessage).isEqualTo("SessionLocationGroup reference:$reference not found")
  }
}
