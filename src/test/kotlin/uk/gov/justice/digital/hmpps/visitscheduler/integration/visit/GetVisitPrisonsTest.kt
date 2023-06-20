package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

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
import uk.gov.justice.digital.hmpps.visitscheduler.controller.PRISONS_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository

@DisplayName("Get $PRISONS_PATH")
class GetVisitPrisonsTest : IntegrationTestBase() {
  @SpyBean
  private lateinit var prisonRepository: PrisonRepository

  @Autowired
  private lateinit var cacheManager: CacheManager

  private val visitRole = listOf("ROLE_VISIT_SCHEDULER")
  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  @BeforeEach
  @AfterEach
  fun cleanTests() {
    cacheManager.getCache("supported-prisons")?.clear()
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
    val responseSpec = webTestClient.get().uri(PRISONS_PATH)
      .headers(setAuthorisation(roles = visitRole))
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
  fun `get supported prisons supports adminRole`() {
    // Given

    // When
    val responseSpec = webTestClient.get().uri(PRISONS_PATH)
      .headers(setAuthorisation(roles = adminRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
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
    var responseSpec = webTestClient.get().uri(PRISONS_PATH)
      .headers(setAuthorisation(roles = visitRole))
      .exchange()

    // Then
    var returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    var results = getSupportedPrisonsResults(returnResult)

    assertThat(results.size).isEqualTo(5)

    // When a call to supported prisons is made a 2nd time same values are returned but from cache
    responseSpec = webTestClient.get().uri(PRISONS_PATH)
      .headers(setAuthorisation(roles = visitRole))
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
    val responseSpec = webTestClient.get().uri(PRISONS_PATH)
      .headers(setAuthorisation(roles = visitRole))
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
}
