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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ExcludeDatesDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdateAction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdateExcludeDatesDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCreatePrison
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGetPrison
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callUpdatePrisonExcludeDates
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonExcludeDateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonConfigService
import java.time.LocalDate
import java.util.stream.Collectors

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
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
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
  fun `when update exclude dates called then exclude dates are successfully added`() {
    // Given
    val prison = PrisonDto("XYZ", true)
    prisonEntityHelper.create(prison.code, prison.active)
    val excludeDatesList = listOf(
      ExcludeDatesDto(LocalDate.now(), UpdateAction.ADD),
      ExcludeDatesDto(LocalDate.now().plusDays(7), UpdateAction.ADD),
      ExcludeDatesDto(LocalDate.now().plusDays(10), UpdateAction.ADD),
    )

    val updateExcludeDatesDto = UpdateExcludeDatesDto(excludeDatesList)
    // When
    val responseSpec = callUpdatePrisonExcludeDates(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, updateExcludeDatesDto)

    // Then
    responseSpec.expectStatus().isOk
    verify(prisonExcludeDateRepositorySpy, times(1)).saveAll(any<List<PrisonExcludeDate>>())
    verify(prisonExcludeDateRepositorySpy, times(0)).deleteByPrisonIdAndExcludeDate(any(), any())
  }

  @Test
  fun `when update exclude dates called with existing dates then exclude dates are not added`() {
    // Given
    val existingExcludeDates = setOf(LocalDate.now(), LocalDate.now().plusDays(7))

    val prison = PrisonDto("XYZ", true, existingExcludeDates)
    prisonEntityHelper.create(prison.code, prison.active)
    val excludeDatesList = listOf(
      // this date already exists
      ExcludeDatesDto(LocalDate.now(), UpdateAction.ADD),
      // this date already exists
      ExcludeDatesDto(LocalDate.now().plusDays(7), UpdateAction.ADD),
      ExcludeDatesDto(LocalDate.now().plusDays(10), UpdateAction.ADD),
    )

    val updateExcludeDatesDto = UpdateExcludeDatesDto(excludeDatesList)
    // When
    val responseSpec = callUpdatePrisonExcludeDates(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, updateExcludeDatesDto)

    // Then
    responseSpec.expectStatus().isOk
    verify(prisonExcludeDateRepositorySpy, times(1)).saveAll(any<List<PrisonExcludeDate>>())
    verify(prisonExcludeDateRepositorySpy, times(0)).deleteByPrisonIdAndExcludeDate(any(), any())
  }

  @Test
  fun `when update exclude dates called then exclude dates are successfully removed`() {
    // Given
    val excludeDatesList = listOf(
      ExcludeDatesDto(LocalDate.now(), UpdateAction.REMOVE),
      ExcludeDatesDto(LocalDate.now().plusDays(7), UpdateAction.REMOVE),
      ExcludeDatesDto(LocalDate.now().plusDays(10), UpdateAction.REMOVE),
    )
    val excludedDates = excludeDatesList.stream().map { it.excludeDate }.collect(Collectors.toSet())
    val prison = PrisonDto("XYZ", true, excludedDates)
    prisonEntityHelper.create(prison.code, prison.active, prison.excludeDates.toList())

    val updateExcludeDatesDto = UpdateExcludeDatesDto(excludeDatesList)
    // When
    val responseSpec = callUpdatePrisonExcludeDates(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, updateExcludeDatesDto)

    // Then
    responseSpec.expectStatus().isOk
    val getResponseSpec = callGetPrison(webTestClient, roleVisitSchedulerHttpHeaders, prison.code)
    val result = getResponseSpec.expectStatus().isOk.expectBody()
    val updatedPrison = getPrison(result)
    Assertions.assertThat(updatedPrison.code).isEqualTo(prison.code)
    Assertions.assertThat(updatedPrison.excludeDates).doesNotContainAnyElementsOf(excludedDates)
    verify(prisonExcludeDateRepositorySpy, times(0)).saveAll(any<List<PrisonExcludeDate>>())
    verify(prisonExcludeDateRepositorySpy, times(3)).deleteByPrisonIdAndExcludeDate(any(), any())
  }

  @Test
  fun `when update exclude dates called with non existing dates then only existing exclude dates are successfully removed`() {
    // Given
    val existingExcludeDates = setOf(LocalDate.now(), LocalDate.now().plusDays(7))

    val excludeDatesList = listOf(
      ExcludeDatesDto(LocalDate.now(), UpdateAction.REMOVE),
      ExcludeDatesDto(LocalDate.now().plusDays(7), UpdateAction.REMOVE),
      // this date does not exist on the database
      ExcludeDatesDto(LocalDate.now().plusDays(10), UpdateAction.REMOVE),
    )
    val excludedDates = excludeDatesList.stream().map { it.excludeDate }.collect(Collectors.toSet())
    val prison = PrisonDto("XYZ", true, excludedDates)
    val createdPrison = prisonEntityHelper.create(prison.code, prison.active, existingExcludeDates.toList())

    val updateExcludeDatesDto = UpdateExcludeDatesDto(excludeDatesList)
    // When
    val responseSpec = callUpdatePrisonExcludeDates(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, updateExcludeDatesDto)

    // Then
    responseSpec.expectStatus().isOk
    val getResponseSpec = callGetPrison(webTestClient, roleVisitSchedulerHttpHeaders, prison.code)
    val result = getResponseSpec.expectStatus().isOk.expectBody()
    val updatedPrison = getPrison(result)
    Assertions.assertThat(updatedPrison.code).isEqualTo(prison.code)
    Assertions.assertThat(updatedPrison.excludeDates).doesNotContainAnyElementsOf(excludedDates)
    verify(prisonExcludeDateRepositorySpy, times(0)).saveAll(any<List<PrisonExcludeDate>>())
    verify(prisonExcludeDateRepositorySpy, times(1)).deleteByPrisonIdAndExcludeDate(createdPrison.id, LocalDate.now())
    verify(prisonExcludeDateRepositorySpy, times(1)).deleteByPrisonIdAndExcludeDate(createdPrison.id, LocalDate.now().plusDays(7))
  }

  @Test
  fun `when update exclude dates called then exclude dates are successfully added and removed`() {
    // Given
    val excludeDatesList = listOf(
      ExcludeDatesDto(LocalDate.now().plusDays(2), UpdateAction.ADD),
      ExcludeDatesDto(LocalDate.now().plusDays(3), UpdateAction.ADD),
      ExcludeDatesDto(LocalDate.now().plusDays(4), UpdateAction.ADD),
      ExcludeDatesDto(LocalDate.now(), UpdateAction.REMOVE),
      ExcludeDatesDto(LocalDate.now().plusDays(7), UpdateAction.REMOVE),
      ExcludeDatesDto(LocalDate.now().plusDays(10), UpdateAction.REMOVE),
    )
    val addedExcludeDates = excludeDatesList.stream().filter { it.action == UpdateAction.ADD }.map { it.excludeDate }.collect(Collectors.toSet())
    val removedExcludeDates = excludeDatesList.stream().filter { it.action == UpdateAction.REMOVE }.map { it.excludeDate }.collect(Collectors.toSet())
    val prison = PrisonDto("TRE", true, removedExcludeDates)

    prisonEntityHelper.create(prison.code, prison.active, prison.excludeDates.toList())

    val updateExcludeDatesDto = UpdateExcludeDatesDto(excludeDatesList)
    // When
    val responseSpec = callUpdatePrisonExcludeDates(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, updateExcludeDatesDto)

    // Then
    responseSpec.expectStatus().isOk
    val getResponseSpec = callGetPrison(webTestClient, roleVisitSchedulerHttpHeaders, prison.code)
    val result = getResponseSpec.expectStatus().isOk.expectBody()
    val updatedPrison = getPrison(result)
    Assertions.assertThat(updatedPrison.code).isEqualTo(prison.code)
    Assertions.assertThat(updatedPrison.excludeDates).containsAll(addedExcludeDates)
    Assertions.assertThat(updatedPrison.excludeDates).doesNotContainAnyElementsOf(removedExcludeDates)
    verify(prisonExcludeDateRepositorySpy, times(1)).saveAll(any<List<PrisonExcludeDate>>())
    verify(prisonExcludeDateRepositorySpy, times(3)).deleteByPrisonIdAndExcludeDate(any(), any())
  }

  @Test
  fun `when update exclude dates called for non existent prison then BAD_REQUEST error code is returned `() {
    // Given
    val nonExistentPrisonCode = "QUI"
    val excludeDatesList = listOf(
      ExcludeDatesDto(LocalDate.now(), UpdateAction.ADD),
      ExcludeDatesDto(LocalDate.now().plusDays(7), UpdateAction.ADD),
      ExcludeDatesDto(LocalDate.now().plusDays(10), UpdateAction.ADD),
    )

    val updateExcludeDatesDto = UpdateExcludeDatesDto(excludeDatesList)
    // When
    val responseSpec = callUpdatePrisonExcludeDates(webTestClient, roleVisitSchedulerHttpHeaders, nonExistentPrisonCode, updateExcludeDatesDto)

    // Then
    responseSpec.expectStatus().isBadRequest
    verify(prisonExcludeDateRepositorySpy, times(0)).saveAll(any<List<PrisonExcludeDate>>())
    verify(prisonExcludeDateRepositorySpy, times(0)).deleteByPrisonIdAndExcludeDate(any(), any())
  }

  private fun getPrison(returnResult: WebTestClient.BodyContentSpec): PrisonDto {
    return objectMapper.readValue(returnResult.returnResult().responseBody, PrisonDto::class.java)
  }
}
