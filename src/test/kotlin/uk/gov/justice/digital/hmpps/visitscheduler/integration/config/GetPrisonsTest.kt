package uk.gov.justice.digital.hmpps.visitscheduler.integration.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.cache.CacheManager
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.controller.ACTIVATE_PRISON
import uk.gov.justice.digital.hmpps.visitscheduler.controller.DEACTIVATE_PRISON
import uk.gov.justice.digital.hmpps.visitscheduler.controller.PRISON
import uk.gov.justice.digital.hmpps.visitscheduler.controller.PRISONS_CONFIG_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.PRISON_CONFIG_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.SUPPORTED_PRISONS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository
import java.time.LocalDate

@DisplayName("Get $SUPPORTED_PRISONS")
@Transactional(propagation = NOT_SUPPORTED)
class GetPrisonsTest : IntegrationTestBase() {
  @SpyBean
  private lateinit var prisonRepository: PrisonRepository

  @Autowired
  private lateinit var cacheManager: CacheManager

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  @BeforeEach
  @AfterEach
  fun cleanTests() {
    cacheManager.getCache("supported-prisons")?.clear()
    cacheManager.getCache("get-prisons")?.clear()
    cacheManager.getCache("get-prison")?.clear()
    deleteEntityHelper.deleteAll()
  }

  @Test
  fun `get supported prisons are returned in correct order`() {
    // Given
    prisonEntityHelper.create(prisonCode = "AWE")
    prisonEntityHelper.create(prisonCode = "GRE")
    prisonEntityHelper.create(prisonCode = "CDE")
    prisonEntityHelper.create(prisonCode = "BDE")
    prisonEntityHelper.create(prisonCode = "WDE")

    // When
    val responseSpec = webTestClient.get().uri(SUPPORTED_PRISONS)
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val results = getSupportedPrisonsResults(returnResult)

    assertThat(results.size).isEqualTo(5)
    assertThat(results[0]).isEqualTo("AWE")
    assertThat(results[1]).isEqualTo("BDE")
    assertThat(results[2]).isEqualTo("CDE")
    assertThat(results[3]).isEqualTo("GRE")
    assertThat(results[4]).isEqualTo("WDE")

    verify(prisonRepository, times(1)).getSupportedPrisons()
  }

  @Test
  fun `get all prisons are returned in correct order`() {
    // Given
    prisonEntityHelper.create(prisonCode = "AWE")
    prisonEntityHelper.create(prisonCode = "GRE", activePrison = false)
    prisonEntityHelper.create(prisonCode = "CDE")
    prisonEntityHelper.create(prisonCode = "BDE", activePrison = false)
    prisonEntityHelper.create(prisonCode = "WDE")

    // When
    val responseSpec = webTestClient.get().uri(PRISONS_CONFIG_PATH)
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val results = getPrisonsResults(returnResult)

    assertThat(results.size).isEqualTo(5)
    assertThat(results[0].code).isEqualTo("AWE")
    assertThat(results[0].active).isTrue
    assertThat(results[1].code).isEqualTo("BDE")
    assertThat(results[1].active).isFalse
    assertThat(results[2].code).isEqualTo("CDE")
    assertThat(results[2].active).isTrue
    assertThat(results[3].code).isEqualTo("GRE")
    assertThat(results[3].active).isFalse
    assertThat(results[4].code).isEqualTo("WDE")
    assertThat(results[4].active).isTrue

    verify(prisonRepository, times(1)).findAllByOrderByCodeAsc()
  }

  @Test
  fun `make prison active prison`() {
    // Given

    prisonEntityHelper.create(prisonCode = "AWE", activePrison = false)

    // When
    val responseSpec = webTestClient.put().uri(ACTIVATE_PRISON.replace("{prisonCode}", "AWE"))
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val result = getPrisonResults(returnResult)
    assertThat(result.code).isEqualTo("AWE")
    assertThat(result.active).isTrue

    val prisonEntity = prisonRepository.findByCode("AWE")
    prisonEntity?.let {
      assertThat(prisonEntity.code).isEqualTo("AWE")
      assertThat(prisonEntity.active).isTrue
    }
  }

  @Test
  fun `deactivate prison`() {
    // Given

    prisonEntityHelper.create(prisonCode = "AWE", activePrison = true)

    // When
    val responseSpec = webTestClient.put().uri(DEACTIVATE_PRISON.replace("{prisonCode}", "AWE"))
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val result = getPrisonResults(returnResult)

    assertThat(result.code).isEqualTo("AWE")
    assertThat(result.active).isFalse

    val prisonEnitiy = prisonRepository.findByCode("AWE")
    prisonEnitiy?.let {
      assertThat(prisonEnitiy.code).isEqualTo("AWE")
      assertThat(prisonEnitiy.active).isFalse
    }
  }

  @Transactional(propagation = REQUIRES_NEW)
  @Test
  fun `create prison`() {
    // Given
    val excludeDate = LocalDate.now()
    val prisonDto = PrisonDto("AWE", true, sortedSetOf(excludeDate))

    // When
    val responseSpec = webTestClient.post().uri(PRISON_CONFIG_PATH.replace("{prisonCode}", "AWE"))
      .headers(setAuthorisation(roles = requiredRole))
      .body(BodyInserters.fromValue(prisonDto))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val result = getPrisonResults(returnResult)
    assertThat(result.code).isEqualTo("AWE")
    assertThat(result.active).isTrue
    assertThat(result.excludeDates.toList()[0]).isEqualTo(excludeDate)

    val prisonEntity = prisonRepository.findByCode("AWE")
    prisonEntity?.let {
      assertThat(prisonEntity.code).isEqualTo("AWE")
      assertThat(prisonEntity.active).isTrue
      assertThat(prisonEntity.excludeDates.toList()[0].excludeDate).isEqualTo(excludeDate)
    }
  }

  @Test
  fun `create prison when it exists throws an exception`() {
    // Given
    prisonEntityHelper.create(prisonCode = "AWE", activePrison = true)

    val excludeDate = LocalDate.now()
    val prisonDto = PrisonDto("AWE", true, sortedSetOf(excludeDate))

    // When
    val responseSpec = webTestClient.post().uri(PRISON_CONFIG_PATH.replace("{prisonCode}", "AWE"))
      .headers(setAuthorisation(roles = requiredRole))
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
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val results = getPrisonResults(returnResult)
    assertThat(results.code).isEqualTo("AWE")
    assertThat(results.active).isTrue
    assertThat(results.excludeDates).contains(excludeDate)
  }

  @Test
  fun `when supported prisons is called twice cached values are returned the second time`() {
    // Given
    prisonEntityHelper.create(prisonCode = "AWE")
    prisonEntityHelper.create(prisonCode = "GRE")
    prisonEntityHelper.create(prisonCode = "CDE")
    prisonEntityHelper.create(prisonCode = "BDE")
    prisonEntityHelper.create(prisonCode = "WDE")

    // When
    var responseSpec = webTestClient.get().uri(SUPPORTED_PRISONS)
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    var returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    var results = getSupportedPrisonsResults(returnResult)

    assertThat(results.size).isEqualTo(5)

    // When a call to supported prisons is made a 2nd time same values are returned but from cache
    responseSpec = webTestClient.get().uri(SUPPORTED_PRISONS)
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    results = getSupportedPrisonsResults(returnResult)
    assertThat(results.size).isEqualTo(5)
    verify(prisonRepository, times(1)).getSupportedPrisons()
  }

  @Test
  fun `sessions with inactive prisons are not returned`() {
    // Given
    prisonEntityHelper.create(prisonCode = "GRE", activePrison = false)
    prisonEntityHelper.create(prisonCode = "CDE", activePrison = false)

    // When
    val responseSpec = webTestClient.get().uri(SUPPORTED_PRISONS)
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val results = getSupportedPrisonsResults(returnResult)

    assertThat(results.size).isEqualTo(0)
    verify(prisonRepository, times(1)).getSupportedPrisons()
  }

  private fun getSupportedPrisonsResults(returnResult: BodyContentSpec): Array<String> {
    return objectMapper.readValue(returnResult.returnResult().responseBody, Array<String>::class.java)
  }

  private fun getPrisonResults(returnResult: BodyContentSpec): PrisonDto {
    return objectMapper.readValue(returnResult.returnResult().responseBody, PrisonDto::class.java)
  }

  private fun getPrisonsResults(returnResult: BodyContentSpec): Array<PrisonDto> {
    return objectMapper.readValue(returnResult.returnResult().responseBody, Array<PrisonDto>::class.java)
  }
}
