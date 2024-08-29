package uk.gov.justice.digital.hmpps.visitscheduler.integration.prison

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonDateBlockedDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.PrisonEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.VisitNotificationEventHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callAddPrisonExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGetPrison
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callRemovePrisonExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonExcludeDateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitNotificationEventService
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
class PrisonExcludeDatesTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @SpyBean
  lateinit var visitNotificationEventServiceSpy: VisitNotificationEventService

  @SpyBean
  lateinit var prisonExcludeDateRepositorySpy: PrisonExcludeDateRepository

  @Autowired
  lateinit var testVisitNotificationEventRepository: TestVisitNotificationEventRepository

  @Autowired
  protected lateinit var visitNotificationEventHelper: VisitNotificationEventHelper

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER_CONFIG"))
  }

  companion object {
    private const val TEST_USER = "TEST_USER"
  }

  @Test
  fun `when add exclude date called with non existent date then exclude date is successfully added`() {
    // Given
    val prison = PrisonEntityHelper.createPrisonDto(prisonCode = "XYZ")
    prisonEntityHelper.create(prison.code, prison.active)
    val excludeDate = LocalDate.now().plusDays(10)

    // When
    val responseSpec = callAddPrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, excludeDate, actionedBy = TEST_USER)

    // Then
    responseSpec.expectStatus().isOk
    verify(prisonExcludeDateRepositorySpy, times(1)).saveAndFlush(any())

    val getResponseSpec = callGetPrison(webTestClient, roleVisitSchedulerHttpHeaders, prison.code)
    val result = getResponseSpec.expectStatus().isOk.expectBody()
    val updatedPrison = getPrison(result)
    Assertions.assertThat(updatedPrison.code).isEqualTo(prison.code)
    Assertions.assertThat(updatedPrison.excludeDates).contains(excludeDate)
  }

  @Test
  fun `when add exclude date called for visit with dates then exclude date is successfully added and visits are marked for review`() {
    // Given
    val excludeDate = LocalDate.now().plusDays(10)

    val prison = prisonEntityHelper.create("XYZ")
    val sessionTemplateXYZ = sessionTemplateEntityHelper.create(prison = prison)

    prisonEntityHelper.create(prison.code, prison.active)

    // existing visit for excludeDate in same prison
    val bookedVisitForSamePrison = createApplicationAndVisit(sessionTemplate = sessionTemplateXYZ, visitStatus = VisitStatus.BOOKED, slotDate = excludeDate)

    // existing visit for excludeDate in different prison
    createApplicationAndVisit(sessionTemplate = sessionTemplateDefault, visitStatus = VisitStatus.BOOKED, slotDate = excludeDate)

    // cancelled visit for excludeDate in same prison
    createApplicationAndVisit(sessionTemplate = sessionTemplateXYZ, visitStatus = VisitStatus.CANCELLED, slotDate = excludeDate)

    // existing visit different excludeDate in same prison
    createApplicationAndVisit(sessionTemplate = sessionTemplateXYZ, visitStatus = VisitStatus.BOOKED, slotDate = excludeDate.plusDays(1))

    // When
    val responseSpec = callAddPrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, excludeDate, actionedBy = TEST_USER)

    // Then
    responseSpec.expectStatus().isOk
    verify(prisonExcludeDateRepositorySpy, times(1)).saveAndFlush(any())
    verify(visitNotificationEventServiceSpy, times(1)).handleAddPrisonVisitBlockDate(PrisonDateBlockedDto(prison.code, excludeDate))

    val getResponseSpec = callGetPrison(webTestClient, roleVisitSchedulerHttpHeaders, prison.code)
    val result = getResponseSpec.expectStatus().isOk.expectBody()
    val updatedPrison = getPrison(result)
    Assertions.assertThat(updatedPrison.code).isEqualTo(prison.code)
    Assertions.assertThat(updatedPrison.excludeDates).contains(excludeDate)

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()

    // only 1 visit for the same date with status of BOOKED will be flagged.
    Assertions.assertThat(visitNotifications).hasSize(1)
    Assertions.assertThat(visitNotifications[0].bookingReference).isEqualTo(bookedVisitForSamePrison.reference)
  }

  @Test
  fun `when add exclude date called with existing date then exclude date is not added and BAD_REQUEST is returned`() {
    // Given
    val existingExcludeDates = setOf(LocalDate.now(), LocalDate.now().plusDays(7))

    val prison = PrisonEntityHelper.createPrisonDto(prisonCode = "XYZ", excludeDates = existingExcludeDates)

    val createdPrison = prisonEntityHelper.create(prison.code, prison.active, prison.excludeDates.toList())
    val excludeDate = LocalDate.now().plusDays(7)

    // When
    val responseSpec = callAddPrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, excludeDate, actionedBy = TEST_USER)

    // Then
    responseSpec.expectStatus().isBadRequest.expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Cannot add exclude date $excludeDate to prison - ${prison.code} as it already exists")
    verify(prisonExcludeDateRepositorySpy, times(0)).save(PrisonExcludeDate(createdPrison.id, createdPrison, excludeDate, actionedBy = TEST_USER))
  }

  @Test
  fun `when remove exclude date called with existing date then exclude date is successfully removed`() {
    // Given
    val existingExcludeDates = setOf(LocalDate.now(), LocalDate.now().plusDays(7))
    val prison = PrisonEntityHelper.createPrisonDto(prisonCode = "JML")

    val createdPrison = prisonEntityHelper.create(prison.code, prison.active, existingExcludeDates.toList())
    val excludeDate = LocalDate.now().plusDays(7)

    // When
    val responseSpec = callRemovePrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, excludeDate, actionedBy = TEST_USER)

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
  fun `when remove exclude date called with existing date then exclude date is successfully removed and any notified visits are removed`() {
    // Given
    val existingExcludeDates = setOf(LocalDate.now(), LocalDate.now().plusDays(7))
    val prison = sessionTemplateDefault.prison
    val createdPrison = prisonEntityHelper.create(prison.code, prison.active, existingExcludeDates.toList())
    val excludeDate = LocalDate.now().plusDays(7)

    // existing visit for excludeDate in same prison
    val bookedVisitForSamePrison = visitEntityHelper.create(sessionTemplate = sessionTemplateDefault, visitStatus = VisitStatus.BOOKED, prisonCode = prison.code)

    visitNotificationEventHelper.create(bookedVisitForSamePrison.reference, NotificationEventType.PRISON_VISITS_BLOCKED_FOR_DATE)

    // When
    val responseSpec = callRemovePrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, excludeDate, actionedBy = TEST_USER)

    // Then
    responseSpec.expectStatus().isOk
    verify(prisonExcludeDateRepositorySpy, times(1)).deleteByPrisonIdAndExcludeDate(createdPrison.id, excludeDate)
    verify(visitNotificationEventServiceSpy, times(1)).handleRemovePrisonVisitBlockDate(PrisonDateBlockedDto(createdPrison.code, excludeDate))

    val getResponseSpec = callGetPrison(webTestClient, roleVisitSchedulerHttpHeaders, prison.code)
    val result = getResponseSpec.expectStatus().isOk.expectBody()
    val updatedPrison = getPrison(result)
    Assertions.assertThat(updatedPrison.code).isEqualTo(prison.code)
    Assertions.assertThat(updatedPrison.excludeDates).doesNotContain(excludeDate)
  }

  @Test
  fun `when remove exclude date called with non existent date then exclude date is not removed and BAD_REQUEST is returned`() {
    // Given
    val prison = PrisonEntityHelper.createPrisonDto(prisonCode = "XYZ")

    val createdPrison = prisonEntityHelper.create(prison.code, prison.active, emptyList())
    val excludeDate = LocalDate.now().plusDays(7)

    // When
    val responseSpec = callRemovePrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, excludeDate, actionedBy = TEST_USER)

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
    val responseSpec = callAddPrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, nonExistentPrisonCode, excludeDate, actionedBy = TEST_USER)

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
    val responseSpec = callRemovePrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, nonExistentPrisonCode, excludeDate, actionedBy = TEST_USER)

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
