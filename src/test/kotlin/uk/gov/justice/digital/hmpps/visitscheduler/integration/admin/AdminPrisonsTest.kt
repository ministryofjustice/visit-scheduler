package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.ACTIVATE_PRISON
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.ACTIVATE_PRISON_CLIENT
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.ADMIN_PRISONS_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.DEACTIVATE_PRISON
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.DEACTIVATE_PRISON_CLIENT
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.PRISON
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.PRISON_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonUserClientDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.helper.PrisonEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository
import java.time.LocalDate

@Transactional
@DisplayName("Admin $ADMIN_PRISONS_PATH")
class AdminPrisonsTest : IntegrationTestBase() {
  @SpyBean
  private lateinit var spyPrisonRepository: PrisonRepository

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  @BeforeEach
  internal fun setUpTests() {
    // We need this hear to delete the default prison in IntegrationTestBase
    deleteEntityHelper.deleteAll()
  }

  @Test
  fun `get all prisons are returned in correct order`() {
    // Given
    prisonEntityHelper.create(prisonCode = "AWE")
    prisonEntityHelper.create(prisonCode = "GRE", activePrison = false)
    prisonEntityHelper.create(prisonCode = "WDE")

    // When
    val responseSpec = webTestClient.get().uri(ADMIN_PRISONS_PATH)
      .headers(setAuthorisation(roles = adminRole))
      .exchange()

    // Then
    val results = getPrisonsResults(responseSpec)

    assertThat(results.size).isEqualTo(3)
    assertPrisonDto(results[0], prisonCode = "AWE", isActive = true, isStaffActive = true, isPublicActive = true)
    assertPrisonDto(results[1], prisonCode = "GRE", isActive = false, isStaffActive = true, isPublicActive = true)
    assertPrisonDto(results[2], prisonCode = "WDE", isActive = true, isStaffActive = true, isPublicActive = true)

    results.forEach { dto ->
      assertPrisonEntity(prisonCode = dto.code, isActive = dto.active, isStaffActive = true, isPublicActive = true)
    }

    verify(spyPrisonRepository, times(1)).findAllByOrderByCodeAsc()
  }

  @Test
  fun `make prison active prison`() {
    // Given

    prisonEntityHelper.create(prisonCode = "AWE", activePrison = false)

    // When
    val responseSpec = webTestClient.put().uri(ACTIVATE_PRISON.replace("{prisonCode}", "AWE"))
      .headers(setAuthorisation(roles = adminRole))
      .exchange()

    // Then
    val dto = getPrisonResults(responseSpec)
    assertPrisonDto(dto, prisonCode = "AWE", isActive = true, isStaffActive = true, isPublicActive = true)
    assertPrisonEntity(prisonCode = dto.code, isActive = dto.active, isStaffActive = true, isPublicActive = true)
  }

  @Test
  fun `make new prisonClient active for prison`() {
    // Given
    val type = STAFF
    prisonEntityHelper.create(prisonCode = "AWE", dontMakeClient = true)

    // When
    val responseSpec = webTestClient.put().uri(ACTIVATE_PRISON_CLIENT.replace("{prisonCode}", "AWE").replace("{type}", type.name))
      .headers(setAuthorisation(roles = adminRole))
      .exchange()

    // Then
    val dto = getPrisonClientResults(responseSpec)
    assertClientPrisonDto(dto, isActive = true, type = type)
    assertClientPrisonEntity(dto, prisonCode = "AWE", isActive = true, type = type)
  }

  @Test
  fun `make new prisonClient in-active for prison`() {
    // Given
    val type = STAFF
    prisonEntityHelper.create(prisonCode = "AWE", dontMakeClient = true)

    // When
    val responseSpec = webTestClient.put().uri(DEACTIVATE_PRISON_CLIENT.replace("{prisonCode}", "AWE").replace("{type}", type.name))
      .headers(setAuthorisation(roles = adminRole))
      .exchange()

    // Then
    val dto = getPrisonClientResults(responseSpec)
    assertClientPrisonDto(dto, isActive = false, type = type)
    assertClientPrisonEntity(dto, prisonCode = "AWE", isActive = false, type = type)
  }

  @Test
  fun `update new prisonClient active for prison`() {
    // Given
    val type = PUBLIC
    prisonEntityHelper.create(prisonCode = "AWE", dontMakeClient = false)

    // When
    val responseSpec = webTestClient.put().uri(ACTIVATE_PRISON_CLIENT.replace("{prisonCode}", "AWE").replace("{type}", type.name))
      .headers(setAuthorisation(roles = adminRole))
      .exchange()

    // Then
    val dto = getPrisonClientResults(responseSpec)
    assertClientPrisonDto(dto, isActive = true, type = type)
    assertClientPrisonEntity(dto, prisonCode = "AWE", isActive = true, type = type)
  }

  @Test
  fun `update prisonClient in-active for prison`() {
    // Given
    val type = PUBLIC
    prisonEntityHelper.create(prisonCode = "AWE", dontMakeClient = false)

    // When
    val responseSpec = webTestClient.put().uri(DEACTIVATE_PRISON_CLIENT.replace("{prisonCode}", "AWE").replace("{type}", type.name))
      .headers(setAuthorisation(roles = adminRole))
      .exchange()

    // Then
    val dto = getPrisonClientResults(responseSpec)
    assertClientPrisonDto(dto, isActive = false, type = type)
    assertClientPrisonEntity(dto, prisonCode = "AWE", isActive = false, type = type)
  }

  fun assertClientPrisonDto(dto: PrisonUserClientDto, isActive: Boolean, type: UserType) {
    assertThat(dto.active).isEqualTo(isActive)
    assertThat(dto.userType).isEqualTo(type)
  }

  fun assertClientPrisonEntity(dto: PrisonUserClientDto, prisonCode: String, isActive: Boolean, type: UserType) {
    val client = testPrisonUserClientRepository.getPrisonClient(prisonCode, type)
    assertThat(client).isNotNull
    client?.let {
      assertThat(client.active).isEqualTo(isActive)
      assertThat(client.userType).isEqualTo(type)
    }
  }

  @Test
  fun `deactivate prison`() {
    // Given

    prisonEntityHelper.create(prisonCode = "AWE", activePrison = true)

    // When
    val responseSpec = webTestClient.put().uri(DEACTIVATE_PRISON.replace("{prisonCode}", "AWE"))
      .headers(setAuthorisation(roles = adminRole))
      .exchange()

    // Then
    val dto = getPrisonResults(responseSpec)
    assertPrisonDto(dto, prisonCode = "AWE", isActive = false, isStaffActive = true, isPublicActive = true)
    assertPrisonEntity(prisonCode = dto.code, isActive = dto.active, isStaffActive = true, isPublicActive = true)
  }

  @Test
  fun `create prison`() {
    // Given
    val excludeDate = sortedSetOf(LocalDate.now())
    val clients = listOf(PrisonUserClientDto(PUBLIC, true), PrisonUserClientDto(STAFF, false))
    val prisonDto = PrisonEntityHelper.createPrisonDto("AWE", true, excludeDates = excludeDate, clients = clients)

    // When
    val responseSpec = webTestClient.post().uri(PRISON_ADMIN_PATH.replace("{prisonCode}", "AWE"))
      .headers(setAuthorisation(roles = adminRole))
      .body(BodyInserters.fromValue(prisonDto))
      .exchange()

    // Then
    val dto = getPrisonResults(responseSpec)
    assertPrisonDto(dto, prisonCode = "AWE", isActive = true, excludeDate = excludeDate.first, isStaffActive = false, isPublicActive = true)
    assertPrisonEntity(prisonCode = dto.code, isActive = dto.active, isStaffActive = false, isPublicActive = true, excludeDate.first)
  }

  @Test
  fun `create prison when it exists throws an exception`() {
    // Given
    prisonEntityHelper.create(prisonCode = "AWE", activePrison = true)

    val excludeDate = LocalDate.now()
    val prisonDto = PrisonEntityHelper.createPrisonDto("AWE", true, excludeDates = sortedSetOf(excludeDate))

    // When
    val responseSpec = webTestClient.post().uri(PRISON_ADMIN_PATH.replace("{prisonCode}", "AWE"))
      .headers(setAuthorisation(roles = adminRole))
      .body(BodyInserters.fromValue(prisonDto))
      .exchange()

    // Then
    responseSpec.expectStatus().isBadRequest
    val errorResponse = getErrorResponse(responseSpec)
    assertThat(errorResponse.userMessage).isEqualTo("Validation failure: null")
    assertThat(errorResponse.developerMessage).isEqualTo("Prison code AWE found, already exists cannot create!")
  }

  @Test
  fun `get prison by prison id-code`() {
    // Given
    val excludeDate = LocalDate.now()

    prisonEntityHelper.create(prisonCode = "AWE", excludeDates = listOf(excludeDate))

    // When
    val responseSpec = webTestClient.get().uri(PRISON.replace("{prisonCode}", "AWE"))
      .headers(setAuthorisation(roles = adminRole))
      .exchange()

    // Then
    val dto = getPrisonResults(responseSpec)
    assertPrisonDto(dto, prisonCode = "AWE", isActive = true, isStaffActive = true, isPublicActive = true, excludeDate = excludeDate)
    assertPrisonEntity(prisonCode = dto.code, isActive = dto.active, isStaffActive = true, isPublicActive = true, excludeDate)
  }

  private fun assertPrisonDto(
    prison: PrisonDto,
    prisonCode: String,
    isActive: Boolean = true,
    isStaffActive: Boolean? = null,
    isPublicActive: Boolean? = null,
    excludeDate: LocalDate? = null,
  ) {
    assertThat(prison.code).isEqualTo(prisonCode)
    assertThat(prison.active).isEqualTo(isActive)

    var expectedClientCounts = 0
    if (isStaffActive != null) expectedClientCounts++
    if (isStaffActive != null) expectedClientCounts++

    assertThat(prison.clients.size).isEqualTo(expectedClientCounts)

    if (isStaffActive != null) {
      val client = prison.clients.first { it.userType == STAFF }
      assertThat(client.userType).isEqualTo(STAFF)
      assertThat(client.active).isEqualTo(isStaffActive)
    } else {
      assertThat(prison.clients.firstOrNull { it.userType == STAFF }).isNull()
    }

    if (isPublicActive != null) {
      val client = prison.clients.first { it.userType == PUBLIC }
      assertThat(client.userType).isEqualTo(PUBLIC)
      assertThat(client.active).isEqualTo(isPublicActive)
    } else {
      assertThat(prison.clients.firstOrNull { it.userType == PUBLIC }).isNull()
    }

    excludeDate?.let {
      assertThat(prison.excludeDates.toList()[0]).isEqualTo(excludeDate)
    }
  }

  private fun assertPrisonEntity(
    prisonCode: String,
    isActive: Boolean = true,
    isStaffActive: Boolean? = null,
    isPublicActive: Boolean? = null,
    exceptedExcludeDate: LocalDate? = null,
  ) {
    val prisonEntity = testPrisonRepository.findByCode(prisonCode)
    System.out.println(" Prison code :" + prisonCode)
    assertThat(prisonEntity).isNotNull
    prisonEntity?.let { prison ->
      assertThat(prison.code).isEqualTo(prisonCode)
      assertThat(prison.active).isEqualTo(isActive)

      var expectedClientCounts = 0
      if (isStaffActive != null) expectedClientCounts++
      if (isStaffActive != null) expectedClientCounts++

      assertThat(prison.clients.size).isEqualTo(expectedClientCounts)

      if (isStaffActive != null) {
        val client = prison.clients.first { it.userType == STAFF }
        assertThat(client.userType).isEqualTo(STAFF)
        assertThat(client.active).isEqualTo(isStaffActive)
      } else {
        assertThat(prison.clients.firstOrNull { it.userType == STAFF }).isNull()
      }

      if (isPublicActive != null) {
        val client = prison.clients.first { it.userType == PUBLIC }
        assertThat(client.userType).isEqualTo(PUBLIC)
        assertThat(client.active).isEqualTo(isPublicActive)
      } else {
        assertThat(prison.clients.firstOrNull { it.userType == PUBLIC }).isNull()
      }

      exceptedExcludeDate?.let {
        with(prison.excludeDates[0]) {
          assertThat(prisonId).isEqualTo(prison.id)
          assertThat(excludeDate).isEqualTo(exceptedExcludeDate)
        }
      }
    }
  }

  private fun getPrisonResults(responseSpec: ResponseSpec): PrisonDto {
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    return objectMapper.readValue(returnResult.returnResult().responseBody, PrisonDto::class.java)
  }

  private fun getPrisonClientResults(responseSpec: ResponseSpec): PrisonUserClientDto {
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    return objectMapper.readValue(returnResult.returnResult().responseBody, PrisonUserClientDto::class.java)
  }

  private fun getPrisonsResults(responseSpec: ResponseSpec): Array<PrisonDto> {
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    return objectMapper.readValue(returnResult.returnResult().responseBody, Array<PrisonDto>::class.java)
  }
}
