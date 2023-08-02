package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.LOCATION_GROUP_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCreateSessionGroup
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createCreateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createPermittedSessionLocationDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionLocationGroupRepository

@DisplayName("Get $LOCATION_GROUP_ADMIN_PATH")
class AdminCreateSessionTemplateGroupTest(
  @Autowired val testSessionLocationGroupRepository: TestSessionLocationGroupRepository,
) : IntegrationTestBase() {

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  private var prison: Prison = Prison(code = "MDI", active = true)

  @BeforeEach
  internal fun setUpTests() {
    prison = prisonEntityHelper.create(prison.code, prison.active)
  }

  @Test
  fun `create session location group test`() {
    // Given
    val locationDto = createPermittedSessionLocationDto("C", "L1", "S1", "001")
    val dto = createCreateLocationGroupDto(permittedSessionLocations = mutableListOf(locationDto))

    // When
    val responseSpec = callCreateSessionGroup(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionLocationGroupDto = getSessionLocationGroup(responseSpec)
    Assertions.assertThat(sessionLocationGroupDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionLocationGroupDto.reference).isNotNull
    Assertions.assertThat(sessionLocationGroupDto.locations.size).isEqualTo(1)
    val permittedSessionLocationDto = sessionLocationGroupDto.locations[0]
    Assertions.assertThat(permittedSessionLocationDto.levelOneCode).isEqualTo(locationDto.levelOneCode)
    Assertions.assertThat(permittedSessionLocationDto.levelTwoCode).isEqualTo(locationDto.levelTwoCode)
    Assertions.assertThat(permittedSessionLocationDto.levelThreeCode).isEqualTo(locationDto.levelThreeCode)
    Assertions.assertThat(permittedSessionLocationDto.levelFourCode).isEqualTo(locationDto.levelFourCode)

    // also check against the database post fix for VB-2458
    val sessionLocationGroupEntity = testSessionLocationGroupRepository.findByReference(sessionLocationGroupDto.reference)
    Assertions.assertThat(sessionLocationGroupEntity).isNotNull
    val permittedSessionLocationEntities = testSessionLocationGroupRepository.findPermittedSessionLocationsByGroup(sessionLocationGroupEntity!!)
    Assertions.assertThat(permittedSessionLocationEntities?.size).isEqualTo(1)
    val permittedSessionLocationEntity = permittedSessionLocationEntities?.get(0)!!
    Assertions.assertThat(permittedSessionLocationEntity.levelOneCode).isEqualTo(permittedSessionLocationDto.levelOneCode)
    Assertions.assertThat(permittedSessionLocationEntity.levelTwoCode).isEqualTo(permittedSessionLocationDto.levelTwoCode)
    Assertions.assertThat(permittedSessionLocationEntity.levelThreeCode).isEqualTo(permittedSessionLocationDto.levelThreeCode)
    Assertions.assertThat(permittedSessionLocationEntity.levelFourCode).isEqualTo(permittedSessionLocationDto.levelFourCode)
  }
}
