package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.PRISONS_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonClientType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonClientType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonClientType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository

@DisplayName("Get $PRISONS_PATH")
class GetVisitPrisonsTest : IntegrationTestBase() {
  @MockitoSpyBean
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
    val clientType = STAFF

    prisonEntityHelper.create(prisonCode = "AWE")
    prisonEntityHelper.create(prisonCode = "GRE")
    prisonEntityHelper.create(prisonCode = "CDE")
    prisonEntityHelper.create(prisonCode = "BDE")
    prisonEntityHelper.create(prisonCode = "WDE")

    // When
    val responseSpec = requestSupportedPrisons(clientType)

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

    verify(spyPrisonRepository, times(1)).getSupportedPrisons(clientType)
  }

  @Test
  fun `get no supported prisons when staff client is inactive`() {
    // Given
    val clientType = STAFF

    val wde = prisonEntityHelper.create(prisonCode = "WDE")
    deActivateClient(wde, clientType)

    // When
    val responseSpec = requestSupportedPrisons(clientType)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val results = getSupportedPrisonsResults(returnResult)

    assertThat(results.size).isEqualTo(0)
    verify(spyPrisonRepository, times(1)).getSupportedPrisons(clientType)
  }

  @Test
  fun `get no supported prisons when public client is inactive`() {
    // Given
    val clientType = PUBLIC

    val wde = prisonEntityHelper.create(prisonCode = "WDE")
    deActivateClient(wde, clientType)

    // When
    val responseSpec = requestSupportedPrisons(clientType)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val results = getSupportedPrisonsResults(returnResult)

    assertThat(results.size).isEqualTo(0)
    verify(spyPrisonRepository, times(1)).getSupportedPrisons(clientType)
  }

  @Test
  fun `get supported prisons supports adminRole`() {
    // Given
    val clientType = STAFF

    // When
    val responseSpec = requestSupportedPrisons(clientType, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
  }

  @Test
  fun `when supported prisons is called twice cached values are not returned the second time`() {
    // Given
    val clientType = STAFF

    prisonEntityHelper.create(prisonCode = "AWE")
    prisonEntityHelper.create(prisonCode = "GRE")
    prisonEntityHelper.create(prisonCode = "CDE")
    prisonEntityHelper.create(prisonCode = "BDE")
    prisonEntityHelper.create(prisonCode = "WDE")

    // When
    var responseSpec = requestSupportedPrisons(clientType)

    // Then
    var returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    var results = getSupportedPrisonsResults(returnResult)

    assertThat(results.size).isEqualTo(5)

    // When a call to supported prisons is made a 2nd time values are not returned any longer from cache
    responseSpec = requestSupportedPrisons(clientType)

    // Then
    returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    results = getSupportedPrisonsResults(returnResult)
    assertThat(results.size).isEqualTo(5)

    // 2 calls made to DB - which means the data is not being cached anymore
    verify(spyPrisonRepository, times(2)).getSupportedPrisons(clientType)
  }

  @Test
  fun `sessions with inactive prisons are not returned`() {
    // Given
    val clientType = STAFF

    prisonEntityHelper.create(prisonCode = "GRE", activePrison = false)
    prisonEntityHelper.create(prisonCode = "CDE", activePrison = false)

    // When
    val responseSpec = requestSupportedPrisons(clientType)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val results = getSupportedPrisonsResults(returnResult)

    assertThat(results.size).isEqualTo(0)
    verify(spyPrisonRepository, times(1)).getSupportedPrisons(clientType)
  }

  private fun deActivateClient(wde: Prison, clientType: PrisonClientType) {
    val index = wde.clients.indexOfFirst { it.clientType == clientType }
    assertThat(wde.clients[index].clientType).isEqualTo(clientType)
    wde.clients[index].active = false
    testPrisonRepository.saveAndFlush(wde)
  }

  private fun requestSupportedPrisons(clientType: PrisonClientType, role: (org.springframework.http.HttpHeaders) -> Unit = setAuthorisation(roles = visitRole)): ResponseSpec = webTestClient.get().uri(PRISONS_PATH.replace("{type}", clientType.name))
    .headers(role)
    .exchange()

  private fun getSupportedPrisonsResults(returnResult: BodyContentSpec): Array<String> = objectMapper.readValue(returnResult.returnResult().responseBody, Array<String>::class.java)
}
