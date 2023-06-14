package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.LOCATION_GROUP_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCreateSessionGroup
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createCreateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createPermittedSessionLocationDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison

@DisplayName("Get $LOCATION_GROUP_ADMIN_PATH")
class AdminCreateSessionTemplateGroupTest : IntegrationTestBase() {

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  private var prison: Prison = Prison(code = "MDI", active = true)

  @BeforeEach
  internal fun setUpTests() {
    prison = prisonEntityHelper.create(prison.code, prison.active)
  }

  @Test
  fun `create session group test`() {
    // Given
    val locationDto = createPermittedSessionLocationDto("C", "L1", "S1", "001")
    val dto = createCreateLocationGroupDto(permittedSessionLocations = mutableListOf(locationDto))

    // When
    val responseSpec = callCreateSessionGroup(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionLocationGroupDto = getSessionLocationGroup(responseSpec)
    Assertions.assertThat(sessionLocationGroupDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionLocationGroupDto.reference).isNotNull()
    Assertions.assertThat(sessionLocationGroupDto.locations.size).isEqualTo(1)
    Assertions.assertThat(sessionLocationGroupDto.locations[0].levelOneCode).isEqualTo(locationDto.levelOneCode)
    Assertions.assertThat(sessionLocationGroupDto.locations[0].levelTwoCode).isEqualTo(locationDto.levelTwoCode)
    Assertions.assertThat(sessionLocationGroupDto.locations[0].levelThreeCode).isEqualTo(locationDto.levelThreeCode)
    Assertions.assertThat(sessionLocationGroupDto.locations[0].levelFourCode).isEqualTo(locationDto.levelFourCode)
  }
}
