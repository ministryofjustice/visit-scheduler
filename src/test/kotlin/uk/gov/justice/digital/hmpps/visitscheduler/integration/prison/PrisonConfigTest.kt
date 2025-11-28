package uk.gov.justice.digital.hmpps.visitscheduler.integration.prison

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonUserClientDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.helper.PrisonEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCreatePrison
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGetPrison
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callUpdatePrison
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonConfigService

@Transactional(propagation = SUPPORTS)
class PrisonConfigTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @MockitoSpyBean
  lateinit var prisonConfigServiceSpy: PrisonConfigService

  @MockitoSpyBean
  lateinit var prisonRepositorySpy: PrisonRepository

  @Autowired
  lateinit var testVisitNotificationEventRepository: TestVisitNotificationEventRepository

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER_CONFIG"))
  }

  @Test
  fun `when create prison called with unique code prison is created`() {
    // Given
    val prisonDto = PrisonEntityHelper.createPrisonDto(prisonCode = "XYZ")

    // When

    val responseSpec = callCreatePrison(webTestClient, roleVisitSchedulerHttpHeaders, prisonDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val result = getPrison(returnResult)

    Assertions.assertThat(result.code).isEqualTo(prisonDto.code)
    Assertions.assertThat(result.active).isTrue

    Assertions.assertThat(result.maxTotalVisitors).isEqualTo(prisonDto.maxTotalVisitors)
    Assertions.assertThat(result.maxAdultVisitors).isEqualTo(prisonDto.maxAdultVisitors)
    Assertions.assertThat(result.maxChildVisitors).isEqualTo(prisonDto.maxChildVisitors)
    Assertions.assertThat(result.adultAgeYears).isEqualTo(prisonDto.adultAgeYears)

    verify(prisonConfigServiceSpy, times(1)).createPrison(any())
    verify(prisonRepositorySpy, times(1)).saveAndFlush(any())
  }

  @Test
  fun `when create prison called for existing prison then BAD_REQUEST is returned`() {
    // Given
    // prison already exists in DB
    val prison = PrisonEntityHelper.createPrisonDto(prisonCode = "XYZ")
    prisonEntityHelper.create(prison.code, prison.active)

    // When
    val responseSpec = callCreatePrison(webTestClient, roleVisitSchedulerHttpHeaders, prison)

    // Then
    responseSpec.expectStatus().isBadRequest
    verify(prisonConfigServiceSpy, times(1)).createPrison(any())
    verify(prisonRepositorySpy, times(0)).save(any())
  }

  @Test
  fun `on create when notice days min is greater than policy notice days max`() {
    // Given
    val staffClient = PrisonUserClientDto(29, 28, STAFF, active = true)
    val createPrisonRequest = PrisonEntityHelper.createPrisonDto(clients = listOf(staffClient))

    // When
    val responseSpec = callCreatePrison(webTestClient, roleVisitSchedulerHttpHeaders, createPrisonRequest)

    // Then
    responseSpec.expectStatus().isBadRequest.expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Policy notice days invalid AWE, max 29 , min 28 for STAFF client")
  }

  @Test
  fun `on create when max visitors is less than max adult visitors`() {
    // Given
    val createPrisonRequest = PrisonEntityHelper.createPrisonDto(maxTotalVisitors = 1, maxChildVisitors = 1, maxAdultVisitors = 10)

    // When
    val responseSpec = callCreatePrison(webTestClient, roleVisitSchedulerHttpHeaders, createPrisonRequest)

    // Then
    responseSpec.expectStatus().isBadRequest.expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Max total visitors invalid AWE, max visitors 1 should be >= 10")
  }

  @Test
  fun `on create when max visitors is less than max child visitors`() {
    // Given
    val createPrisonRequest = PrisonEntityHelper.createPrisonDto(maxTotalVisitors = 1, maxChildVisitors = 10, maxAdultVisitors = 1)

    // When
    val responseSpec = callCreatePrison(webTestClient, roleVisitSchedulerHttpHeaders, createPrisonRequest)

    // Then
    responseSpec.expectStatus().isBadRequest.expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Max total visitors invalid AWE, max visitors 1 should be >= 10")
  }

  @Test
  fun `on create when notice days min and max is less than zero error is returned`() {
    // Given
    val staffClient = PrisonUserClientDto(-1, -1, STAFF, active = true)
    val createPrisonRequest = PrisonEntityHelper.createPrisonDto(clients = listOf(staffClient))

    // When
    val responseSpec = callCreatePrison(webTestClient, roleVisitSchedulerHttpHeaders, createPrisonRequest)

    // Then
    responseSpec.expectStatus().isBadRequest.expectBody()
  }

  @Test
  fun `on create when max visitors and adult age have invalid values then errors are thrown`() {
    // Given
    val createPrisonRequest = PrisonEntityHelper.createPrisonDto(maxTotalVisitors = 0, maxAdultVisitors = 0, maxChildVisitors = -2)

    // When
    val responseSpec = callCreatePrison(webTestClient, roleVisitSchedulerHttpHeaders, createPrisonRequest)

    // Then
    val errorResponse = getErrorResponse(responseSpec)

    Assertions.assertThat(errorResponse.userMessage).isEqualTo("Invalid Arguments")
    Assertions.assertThat(errorResponse.developerMessage).contains("'maxTotalVisitors': rejected value [0]")
    Assertions.assertThat(errorResponse.developerMessage).contains("'maxAdultVisitors': rejected value [0]")
    Assertions.assertThat(errorResponse.developerMessage).contains("'maxChildVisitors': rejected value [-2]")
  }

  @Test
  fun `when update prison called for existing prison then values are updated correctly`() {
    // Given
    // prison already exists in DB
    prison = prisonEntityHelper.create()
    val staffClient = PrisonUserClientDto(1, 23, STAFF, active = true)
    val publicClient = PrisonUserClientDto(2, 28, PUBLIC, active = true)
    val updatePrisonRequest = PrisonEntityHelper.updatePrisonDto(clients = listOf(staffClient, publicClient))
    prisonEntityHelper.create(prison.code, prison.active)

    // When
    val responseSpec = callUpdatePrison(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, updatePrisonRequest)

    // Then
    responseSpec.expectStatus().isOk
    verify(prisonConfigServiceSpy, times(1)).updatePrison(prison.code, updatePrisonRequest)
    verify(prisonRepositorySpy, times(2)).saveAndFlush(any())

    val result = getPrison(responseSpec.expectBody())
    Assertions.assertThat(result.maxTotalVisitors).isNotEqualTo(prison.maxTotalVisitors)
    Assertions.assertThat(result.maxAdultVisitors).isNotEqualTo(prison.maxAdultVisitors)
    Assertions.assertThat(result.maxChildVisitors).isNotEqualTo(prison.maxChildVisitors)
    Assertions.assertThat(result.adultAgeYears).isNotEqualTo(prison.adultAgeYears)

    Assertions.assertThat(result.maxTotalVisitors).isEqualTo(updatePrisonRequest.maxTotalVisitors)
    Assertions.assertThat(result.maxAdultVisitors).isEqualTo(updatePrisonRequest.maxAdultVisitors)
    Assertions.assertThat(result.maxChildVisitors).isEqualTo(updatePrisonRequest.maxChildVisitors)
    Assertions.assertThat(result.adultAgeYears).isEqualTo(updatePrisonRequest.adultAgeYears)

    Assertions.assertThat(result.clients.size).isEqualTo(2)
    Assertions.assertThat(result.clients[0]).isEqualTo(staffClient)
    Assertions.assertThat(result.clients[1]).isEqualTo(publicClient)
  }

  @Test
  fun `on update when notice days min is greater than policy notice days max`() {
    // Given
    // prison already exists in DB
    prison = prisonEntityHelper.create(policyNoticeDaysMin = 1, policyNoticeDaysMax = 28)

    val staffClient = PrisonUserClientDto(29, 28, STAFF, active = true)
    val updatePrisonRequest = PrisonEntityHelper.updatePrisonDto(clients = listOf(staffClient))
    prisonEntityHelper.create(prison.code, prison.active)

    // When
    val responseSpec = callUpdatePrison(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, updatePrisonRequest)

    // Then
    responseSpec.expectStatus().isBadRequest.expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Policy notice days invalid MDI, max 29 , min 28 for STAFF client")
  }

  @Test
  fun `on update when max visitors is less than max adult visitors`() {
    // Given
    // prison already exists in DB
    prison = prisonEntityHelper.create()

    val updatePrisonRequest = PrisonEntityHelper.updatePrisonDto(maxTotalVisitors = 1, maxChildVisitors = 1, maxAdultVisitors = 10)
    prisonEntityHelper.create(prison.code, prison.active)

    // When
    val responseSpec = callUpdatePrison(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, updatePrisonRequest)

    // Then
    responseSpec.expectStatus().isBadRequest.expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Max total visitors invalid MDI, max visitors 1 should be >= 10")
  }

  @Test
  fun `on update when max visitors is less than max child visitors`() {
    // Given
    // prison already exists in DB
    prison = prisonEntityHelper.create()

    val updatePrisonRequest = PrisonEntityHelper.updatePrisonDto(maxTotalVisitors = 1, maxChildVisitors = 10, maxAdultVisitors = 1)
    prisonEntityHelper.create(prison.code, prison.active)

    // When
    val responseSpec = callUpdatePrison(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, updatePrisonRequest)

    // Then
    responseSpec.expectStatus().isBadRequest.expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Max total visitors invalid MDI, max visitors 1 should be >= 10")
  }

  @Test
  fun `on update when notice days min and max is less than zero error is returned`() {
    // Given
    // prison already exists in DB
    prison = prisonEntityHelper.create()
    val staffClient = PrisonUserClientDto(-1, -1, STAFF, active = true)

    val updatePrisonRequest = PrisonEntityHelper.updatePrisonDto(clients = listOf(staffClient))
    prisonEntityHelper.create(prison.code, prison.active)
    // When
    val responseSpec = callUpdatePrison(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, updatePrisonRequest)

    // Then
    responseSpec.expectStatus().isBadRequest.expectBody()
  }

  @Test
  fun `on update when max visitors and adult age have invalid values then errors are thrown`() {
    // Given
    // prison already exists in DB
    prison = prisonEntityHelper.create()

    val updatePrisonRequest = PrisonEntityHelper.updatePrisonDto(maxTotalVisitors = 0, maxAdultVisitors = 0, maxChildVisitors = -2)
    prisonEntityHelper.create(prison.code, prison.active)
    // When
    val responseSpec = callUpdatePrison(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, updatePrisonRequest)

    // Then
    val errorResponse = getErrorResponse(responseSpec)

    Assertions.assertThat(errorResponse.userMessage).isEqualTo("Invalid Arguments")
    Assertions.assertThat(errorResponse.developerMessage).contains("'maxTotalVisitors': rejected value [0]")
    Assertions.assertThat(errorResponse.developerMessage).contains("'maxAdultVisitors': rejected value [0]")
    Assertions.assertThat(errorResponse.developerMessage).contains("'maxChildVisitors': rejected value [-2]")
  }

  @Test
  fun `when we get a prison, data is returned as expected `() {
    // Given
    val prison = prisonEntityHelper.create()

    // When
    val responseSpec = callGetPrison(webTestClient, roleVisitSchedulerHttpHeaders, prison.code)

    // Then
    responseSpec.expectStatus().isOk

    val result = responseSpec.expectStatus().isOk.expectBody()
    val returnedPrison = getPrison(result)

    Assertions.assertThat(returnedPrison.code).isEqualTo(prison.code)
    Assertions.assertThat(returnedPrison.active).isTrue
  }

  private fun getPrison(returnResult: WebTestClient.BodyContentSpec): PrisonDto = objectMapper.readValue(returnResult.returnResult().responseBody, PrisonDto::class.java)
}
