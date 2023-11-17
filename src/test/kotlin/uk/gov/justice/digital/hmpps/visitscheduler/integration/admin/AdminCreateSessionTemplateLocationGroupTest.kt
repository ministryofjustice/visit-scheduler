package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.LOCATION_GROUP_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCreateSessionGroup
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callDeleteGroupByReference
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGetGroupByReference
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGetGroupsByPrisonId
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createCreateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createPermittedSessionLocationDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionLocationGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionTemplateRepository
import java.time.LocalDate

@DisplayName("Get/Create/Update/Delete $LOCATION_GROUP_ADMIN_PATH")
class AdminCreateSessionTemplateLocationGroupTest(
  @Autowired val testTemplateRepository: TestSessionTemplateRepository,
  @Autowired val testSessionLocationGroupRepository: TestSessionLocationGroupRepository,
) : IntegrationTestBase() {

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  private lateinit var sessionTemplateWithGrps: SessionTemplate

  private lateinit var sessionGroup1: SessionLocationGroup
  private lateinit var sessionGroup2: SessionLocationGroup
  private lateinit var sessionGroupWithNoTemplate: SessionLocationGroup

  @BeforeEach
  internal fun setUpTests() {
    prison = prisonEntityHelper.create()

    sessionTemplateWithGrps = sessionTemplateEntityHelper.create(validFromDate = LocalDate.now())

    val allowedPermittedLocations1 = listOf(AllowedSessionLocationHierarchy("A", "1", "001"))
    sessionGroup1 =
      sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = allowedPermittedLocations1)
    val allowedPermittedLocations2 = listOf(AllowedSessionLocationHierarchy("B"))
    sessionGroup2 = sessionLocationGroupHelper.create(
      prisonCode = prison.code,
      name = "get 2",
      prisonHierarchies = allowedPermittedLocations2,
    )

    sessionTemplateWithGrps.permittedSessionLocationGroups.add(sessionGroup1)
    sessionTemplateWithGrps.permittedSessionLocationGroups.add(sessionGroup2)

    testTemplateRepository.saveAndFlush(sessionTemplateWithGrps)

    val allowedPermittedLocations3 = listOf(AllowedSessionLocationHierarchy("B"))
    sessionGroupWithNoTemplate = sessionLocationGroupHelper.create(
      prisonCode = prison.code,
      name = "get 3",
      prisonHierarchies = allowedPermittedLocations3,
    )
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

  @Test
  fun `create session location group with null level one code, validation fails`() {
    // Given
    val jsonCreateDto = "{'name':'create','prisonId':'MDI','locations':[{}]}"

    // When
    val responseSpec = callCreateSessionGroup(webTestClient, jsonCreateDto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest.expectBody()
  }

  @Test
  fun `create session location group with blank level one code, validation fails`() {
    // Given
    val locationDto = createPermittedSessionLocationDto(" ")
    val dto = createCreateLocationGroupDto(permittedSessionLocations = mutableListOf(locationDto))

    // When
    val responseSpec = callCreateSessionGroup(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest.expectBody()
  }

  @Test
  fun `create session location group with blank level two code, validation fails`() {
    // Given
    val locationDto = createPermittedSessionLocationDto("1", levelTwoCode = "")
    val dto = createCreateLocationGroupDto(permittedSessionLocations = mutableListOf(locationDto))

    // When
    val responseSpec = callCreateSessionGroup(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest.expectBody()
  }

  @Test
  fun `create session location group with blank level three code, validation fails`() {
    // Given
    val locationDto = createPermittedSessionLocationDto("1", levelTwoCode = "2", levelThreeCode = "")
    val dto = createCreateLocationGroupDto(permittedSessionLocations = mutableListOf(locationDto))

    // When
    val responseSpec = callCreateSessionGroup(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest.expectBody()
  }

  @Test
  fun `create session location group with blank level four code, validation fails`() {
    // Given
    val locationDto = createPermittedSessionLocationDto("1", levelTwoCode = "2", levelThreeCode = "3", levelFourCode = "")
    val dto = createCreateLocationGroupDto(permittedSessionLocations = mutableListOf(locationDto))

    // When
    val responseSpec = callCreateSessionGroup(webTestClient, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest.expectBody()
  }

  @Test
  fun `get session groups by prison id test`() {
    // Given
    val prisonCode = prison.code
    // When
    val responseSpec = callGetGroupsByPrisonId(webTestClient, prisonCode, setAuthorisation(roles = adminRole))

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
    val responseSpec = callGetGroupByReference(webTestClient, reference, setAuthorisation(roles = adminRole))

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
    val responseSpec = callDeleteGroupByReference(webTestClient, reference, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
    responseSpec.expectBody()
      .jsonPath("$").isEqualTo("Session location group Deleted $reference!")

    Assertions.assertThat(testSessionLocationGroupRepository.hasById(sessionGroupWithNoTemplate.id)).isFalse
  }

  @Test
  fun `delete session group when session template uses the group exception is thrown`() {
    // Given
    val reference = sessionGroup1.reference

    // When
    val responseSpec = callDeleteGroupByReference(webTestClient, reference, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").isEqualTo("Location group cannot be deleted $reference because session templates are using it!")
  }
}
