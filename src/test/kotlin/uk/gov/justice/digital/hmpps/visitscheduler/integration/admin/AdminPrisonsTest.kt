package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.ACTIVATE_PRISON
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.ADMIN_PRISONS_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.DEACTIVATE_PRISON
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.PRISON
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.PRISON_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository
import java.time.LocalDate

@DisplayName("Admin $ADMIN_PRISONS_PATH")
class AdminPrisonsTest : IntegrationTestBase() {
  @SpyBean
  private lateinit var prisonRepository: PrisonRepository

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  @Test
  fun `get all prisons are returned in correct order`() {
    // Given
    prisonEntityHelper.create(prisonCode = "AWE")
    prisonEntityHelper.create(prisonCode = "GRE", activePrison = false)
    prisonEntityHelper.create(prisonCode = "CDE")
    prisonEntityHelper.create(prisonCode = "BDE", activePrison = false)
    prisonEntityHelper.create(prisonCode = "WDE")

    // When
    val responseSpec = webTestClient.get().uri(ADMIN_PRISONS_PATH)
      .headers(setAuthorisation(roles = adminRole))
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
      .headers(setAuthorisation(roles = adminRole))
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
      .headers(setAuthorisation(roles = adminRole))
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
    val responseSpec = webTestClient.post().uri(PRISON_ADMIN_PATH.replace("{prisonCode}", "AWE"))
      .headers(setAuthorisation(roles = adminRole))
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
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val results = getPrisonResults(returnResult)
    assertThat(results.code).isEqualTo("AWE")
    assertThat(results.active).isTrue()
    assertThat(results.excludeDates).contains(excludeDate)
  }

  private fun getPrisonResults(returnResult: BodyContentSpec): PrisonDto {
    return objectMapper.readValue(returnResult.returnResult().responseBody, PrisonDto::class.java)
  }

  private fun getPrisonsResults(returnResult: BodyContentSpec): Array<PrisonDto> {
    return objectMapper.readValue(returnResult.returnResult().responseBody, Array<PrisonDto>::class.java)
  }
}
