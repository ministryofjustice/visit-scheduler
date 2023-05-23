package uk.gov.justice.digital.hmpps.visitscheduler.integration.prison

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callAddPrisonExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCreatePrison
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGetPrison
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callRemovePrisonExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonExcludeDateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonConfigService
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
class PrisonConfigTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @SpyBean
  lateinit var prisonConfigServiceSpy: PrisonConfigService

  @SpyBean
  lateinit var prisonRepositorySpy: PrisonRepository

  @SpyBean
  lateinit var prisonExcludeDateRepositorySpy: PrisonExcludeDateRepository

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER_CONFIG"))
  }

  @Test
  fun `when create prison called with unique code prison is created`() {
    // Given
    val prison = PrisonDto("XYZ", true)
    // When

    val responseSpec = callCreatePrison(webTestClient, roleVisitSchedulerHttpHeaders, prison)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val result = getPrison(returnResult)

    Assertions.assertThat(result.code).isEqualTo(prison.code)
    Assertions.assertThat(result.active).isTrue
    Assertions.assertThat(result.excludeDates).isEmpty()

    verify(prisonConfigServiceSpy, times(1)).createPrison(any())
    verify(prisonRepositorySpy, times(1)).saveAndFlush(any())
  }

  @Test
  fun `when create prison called for existing prison then BAD_REQUEST is returned`() {
    // Given
    // prison already exists in DB
    val prison = PrisonDto("XYZ", true)
    prisonEntityHelper.create(prison.code, prison.active)

    // When
    val responseSpec = callCreatePrison(webTestClient, roleVisitSchedulerHttpHeaders, prison)

    // Then
    responseSpec.expectStatus().isBadRequest
    verify(prisonConfigServiceSpy, times(1)).createPrison(any())
    verify(prisonRepositorySpy, times(0)).save(any())
  }

  @Test
  fun `when add exclude date called with non existent date then exclude date is successfully added`() {
    // Given
    val prison = PrisonDto("XYZ", true)
    prisonEntityHelper.create(prison.code, prison.active)
    val excludeDate = LocalDate.now().plusDays(10)

    // When
    val responseSpec = callAddPrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, excludeDate)

    // Then
    responseSpec.expectStatus().isOk
    verify(prisonExcludeDateRepositorySpy, times(1)).save(any())

    val getResponseSpec = callGetPrison(webTestClient, roleVisitSchedulerHttpHeaders, prison.code)
    val result = getResponseSpec.expectStatus().isOk.expectBody()
    val updatedPrison = getPrison(result)
    Assertions.assertThat(updatedPrison.code).isEqualTo(prison.code)
    Assertions.assertThat(updatedPrison.excludeDates).contains(excludeDate)
  }

  @Test
  fun `when add exclude date called with existing date then exclude date is not added and BAD_REQUEST is returned`() {
    // Given
    val existingExcludeDates = setOf(LocalDate.now(), LocalDate.now().plusDays(7))

    val prison = PrisonDto("XYZ", true, existingExcludeDates)
    val createdPrison = prisonEntityHelper.create(prison.code, prison.active, prison.excludeDates.toList())
    val excludeDate = LocalDate.now().plusDays(7)

    // When
    val responseSpec = callAddPrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, excludeDate)

    // Then
    responseSpec.expectStatus().isBadRequest.expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Cannot add exclude date $excludeDate to prison - ${prison.code} as it already exists")
    verify(prisonExcludeDateRepositorySpy, times(0)).save(PrisonExcludeDate(createdPrison.id, createdPrison, excludeDate))
  }

  @Test
  fun `when remove exclude date called with existing date then exclude date is successfully removed`() {
    // Given
    val existingExcludeDates = setOf(LocalDate.now(), LocalDate.now().plusDays(7))
    val prison = PrisonDto("JML", true)
    val createdPrison = prisonEntityHelper.create(prison.code, prison.active, existingExcludeDates.toList())
    val excludeDate = LocalDate.now().plusDays(7)

    // When
    val responseSpec = callRemovePrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, excludeDate)

    // Then
    responseSpec.expectStatus().isOk
    verify(prisonExcludeDateRepositorySpy, times(1)).deleteByPrisonIdAndExcludeDate(createdPrison.id, excludeDate)

    val getResponseSpec = callGetPrison(webTestClient, roleVisitSchedulerHttpHeaders, prison.code)
    val result = getResponseSpec.expectStatus().isOk.expectBody()
    val updatedPrison = getPrison(result)
    Assertions.assertThat(updatedPrison.code).isEqualTo(prison.code)
    Assertions.assertThat(updatedPrison.excludeDates).doesNotContain(excludeDate)
  }

  @Test
  fun `when remove exclude date called with non existent date then exclude date is not removed and BAD_REQUEST is returned`() {
    // Given
    val prison = PrisonDto("XYZ", true)
    val createdPrison = prisonEntityHelper.create(prison.code, prison.active, emptyList())
    val excludeDate = LocalDate.now().plusDays(7)

    // When
    val responseSpec = callRemovePrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, excludeDate)

    // Then
    responseSpec.expectStatus().isBadRequest.expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Cannot remove exclude date $excludeDate from prison - ${prison.code} as it does not exist")
    verify(prisonExcludeDateRepositorySpy, times(0)).deleteByPrisonIdAndExcludeDate(createdPrison.id, excludeDate)
  }

  @Test
  fun `when add exclude dates called for non existent prison then BAD_REQUEST error code is returned `() {
    // Given
    val nonExistentPrisonCode = "QUI"
    val excludeDate = LocalDate.now().plusDays(7)

    // When
    val responseSpec = callAddPrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, nonExistentPrisonCode, excludeDate)

    // Then
    responseSpec.expectStatus().isBadRequest.expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Prison code $nonExistentPrisonCode not found!")
    verify(prisonExcludeDateRepositorySpy, times(0)).save(any())
    verify(prisonExcludeDateRepositorySpy, times(0)).deleteByPrisonIdAndExcludeDate(any(), any())
  }

  @Test
  fun `when remove exclude dates called for non existent prison then BAD_REQUEST error code is returned `() {
    // Given
    val nonExistentPrisonCode = "QUI"
    val excludeDate = LocalDate.now().plusDays(7)

    // When
    val responseSpec = callRemovePrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, nonExistentPrisonCode, excludeDate)

    // Then
    responseSpec.expectStatus().isBadRequest.expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Prison code $nonExistentPrisonCode not found!")
    verify(prisonExcludeDateRepositorySpy, times(0)).save(any())
    verify(prisonExcludeDateRepositorySpy, times(0)).deleteByPrisonIdAndExcludeDate(any(), any())
  }

  private fun getPrison(returnResult: WebTestClient.BodyContentSpec): PrisonDto {
    return objectMapper.readValue(returnResult.returnResult().responseBody, PrisonDto::class.java)
  }
}