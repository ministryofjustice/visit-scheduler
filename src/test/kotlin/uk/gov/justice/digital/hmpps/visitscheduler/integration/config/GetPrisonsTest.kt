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
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_PRISON
import uk.gov.justice.digital.hmpps.visitscheduler.controller.SUPPORTED_PRISONS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository
import java.time.LocalDate

@DisplayName("Get $SUPPORTED_PRISONS")
class GetPrisonsTest : IntegrationTestBase() {
  @SpyBean
  private lateinit var prisonRepository: PrisonRepository

  @Autowired
  private lateinit var cacheManager: CacheManager

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  @BeforeEach
  @AfterEach
  fun clearCache() {
    cacheManager.getCache("supported-prisons")?.clear()
  }

  @Test
  fun `all supported prisons are returned in correct order`() {
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
  fun `get prison by prison id-code`() {
    // Given
    val excludeDate = LocalDate.now()

    prisonEntityHelper.create(prisonCode = "AWE", excludeDates = listOf(excludeDate))

    // When
    val responseSpec = webTestClient.get().uri(GET_PRISON.replace("{prisonCode}", "AWE"))
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val results = getPrisonsResults(returnResult)
    assertThat(results.code).isEqualTo("AWE")
    assertThat(results.active).isTrue()
    assertThat(results.excludeDates).contains(excludeDate)
  }

  @Test
  fun `when supported prisons is called twice cached values are returned the second time`() {
    // Given
    val today = LocalDate.now()

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

  private fun getPrisonsResults(returnResult: BodyContentSpec): PrisonDto {
    return objectMapper.readValue(returnResult.returnResult().responseBody, PrisonDto::class.java)
  }
}