package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.PRISONS_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository

@DisplayName("Get $PRISONS_PATH")
class GetVisitPrisonsTest : IntegrationTestBase() {
  @SpyBean
  private lateinit var spyPrisonRepository: PrisonRepository

  private val visitRole = listOf("ROLE_VISIT_SCHEDULER")
  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  @BeforeEach
  @AfterEach
  fun cleanTests() {
    deleteEntityHelper.deleteAll()
  }

  @Test
  fun `get supported prisons are returned in correct order`() {
    // Given
    val userType = STAFF

    prisonEntityHelper.create(prisonCode = "AWE")
    prisonEntityHelper.create(prisonCode = "GRE")
    prisonEntityHelper.create(prisonCode = "CDE")
    prisonEntityHelper.create(prisonCode = "BDE")
    prisonEntityHelper.create(prisonCode = "WDE")

    // When
    val responseSpec = requestSupportedPrisons(userType)

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

    verify(spyPrisonRepository, times(1)).getSupportedPrisons(userType)
  }

  @Test
  fun `get no supported prisons when staff client is inactive`() {
    // Given
    val userType = STAFF

    val wde = prisonEntityHelper.create(prisonCode = "WDE")
    deActivateClient(wde, userType)

    // When
    val responseSpec = requestSupportedPrisons(userType)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val results = getSupportedPrisonsResults(returnResult)

    assertThat(results.size).isEqualTo(0)
    verify(spyPrisonRepository, times(1)).getSupportedPrisons(userType)
  }

  @Test
  fun `get no supported prisons when public client is inactive`() {
    // Given
    val userType = STAFF

    val wde = prisonEntityHelper.create(prisonCode = "WDE")
    deActivateClient(wde, userType)

    // When
    val responseSpec = requestSupportedPrisons(userType)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val results = getSupportedPrisonsResults(returnResult)

    assertThat(results.size).isEqualTo(0)
    verify(spyPrisonRepository, times(1)).getSupportedPrisons(userType)
  }

  @Test
  fun `get supported prisons supports adminRole`() {
    // Given
    val userType = STAFF

    // When
    val responseSpec = requestSupportedPrisons(userType, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
  }

  @Test
  fun `when supported prisons is called twice cached values are not returned the second time`() {
    // Given
    val userType = STAFF

    prisonEntityHelper.create(prisonCode = "AWE")
    prisonEntityHelper.create(prisonCode = "GRE")
    prisonEntityHelper.create(prisonCode = "CDE")
    prisonEntityHelper.create(prisonCode = "BDE")
    prisonEntityHelper.create(prisonCode = "WDE")

    // When
    var responseSpec = requestSupportedPrisons(userType)

    // Then
    var returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    var results = getSupportedPrisonsResults(returnResult)

    assertThat(results.size).isEqualTo(5)

    // When a call to supported prisons is made a 2nd time values are not returned any longer from cache
    responseSpec = requestSupportedPrisons(userType)

    // Then
    returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    results = getSupportedPrisonsResults(returnResult)
    assertThat(results.size).isEqualTo(5)

    // 2 calls made to DB - which means the data is not being cached anymore
    verify(spyPrisonRepository, times(2)).getSupportedPrisons(userType)
  }

  @Test
  fun `sessions with inactive prisons are not returned`() {
    // Given
    val userType = STAFF

    prisonEntityHelper.create(prisonCode = "GRE", activePrison = false)
    prisonEntityHelper.create(prisonCode = "CDE", activePrison = false)

    // When
    val responseSpec = requestSupportedPrisons(userType)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val results = getSupportedPrisonsResults(returnResult)

    assertThat(results.size).isEqualTo(0)
    verify(spyPrisonRepository, times(1)).getSupportedPrisons(userType)
  }

  private fun deActivateClient(wde: Prison, userType: UserType) {
    val index = wde.clients.indexOfFirst { it.userType == userType }
    assertThat(wde.clients[index].userType).isEqualTo(userType)
    wde.clients[index].active = false
    testPrisonRepository.saveAndFlush(wde)
  }

  private fun requestSupportedPrisons(userType: UserType, role: (org.springframework.http.HttpHeaders) -> Unit = setAuthorisation(roles = visitRole)): ResponseSpec =
    webTestClient.get().uri(PRISONS_PATH.replace("{type}", userType.name))
      .headers(role)
      .exchange()

  private fun getSupportedPrisonsResults(returnResult: BodyContentSpec): Array<String> {
    return objectMapper.readValue(returnResult.returnResult().responseBody, Array<String>::class.java)
  }
}
