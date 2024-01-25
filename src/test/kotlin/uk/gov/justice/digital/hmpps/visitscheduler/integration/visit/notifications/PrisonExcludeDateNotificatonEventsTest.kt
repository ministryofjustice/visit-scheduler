package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.notifications

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.HttpHeaders
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ReserveVisitSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonDateBlockedDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.PrisonEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callAddPrisonExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callRemovePrisonExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitBook
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitChange
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitNotificationEventService
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
class PrisonExcludeDateNotificatonEventsTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @SpyBean
  lateinit var visitNotificationEventServiceSpy: VisitNotificationEventService

  @Autowired
  lateinit var testVisitNotificationEventRepository: TestVisitNotificationEventRepository

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER_CONFIG", "ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when add exclude date added then visits are flagged for review`() {
    // Given
    val prison = PrisonEntityHelper.createPrisonDto("XYZ")
    prisonEntityHelper.create(prison.code, prison.active)
    val excludeDate = LocalDate.now().plusDays(10)

    // existing visit for excludeDate in same prison
    val bookedVisitForSamePrison = visitEntityHelper.create(visitStatus = VisitStatus.BOOKED, visitStart = excludeDate.atTime(10, 0), visitEnd = excludeDate.atTime(11, 0), prisonCode = prison.code)

    // existing visit for excludeDate in different prison (MDI)
    visitEntityHelper.create(visitStatus = VisitStatus.BOOKED, visitStart = excludeDate.atTime(10, 0), visitEnd = excludeDate.atTime(11, 0))

    // reserved visit for excludeDate in same prison
    visitEntityHelper.create(visitStatus = VisitStatus.RESERVED, visitStart = excludeDate.atTime(10, 0), visitEnd = excludeDate.atTime(11, 0), prisonCode = prison.code)

    // cancelled visit for excludeDate in same prison
    visitEntityHelper.create(visitStatus = VisitStatus.CANCELLED, visitStart = excludeDate.atTime(10, 0), visitEnd = excludeDate.atTime(11, 0), prisonCode = prison.code)

    // existing visit not for excludeDate in same prison
    visitEntityHelper.create(visitStatus = VisitStatus.BOOKED, visitStart = excludeDate.plusDays(1).atTime(10, 0), visitEnd = excludeDate.plusDays(1).atTime(11, 0), prisonCode = prison.code)

    // When
    val responseSpec = callAddPrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, excludeDate)

    // Then
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventServiceSpy, times(1)).handleAddPrisonVisitBlockDate(PrisonDateBlockedDto(prison.code, excludeDate))

    val visitNotifications = testVisitNotificationEventRepository.findAll()

    // only 1 visit for the same date with status of BOOKED will be flagged.
    Assertions.assertThat(visitNotifications).hasSize(1)
    Assertions.assertThat(visitNotifications[0].bookingReference).isEqualTo(bookedVisitForSamePrison.reference)
  }

  @Test
  fun `when exclude date is removed then visit flag is removed`() {
    // Given
    val prison = PrisonEntityHelper.createPrisonDto("XYZ")
    prisonEntityHelper.create(prison.code, prison.active)
    val excludeDate = LocalDate.now().plusDays(10)

    // existing visit for excludeDate in same prison
    val bookedVisitForSamePrison = visitEntityHelper.create(visitStatus = VisitStatus.BOOKED, visitStart = excludeDate.atTime(10, 0), visitEnd = excludeDate.atTime(11, 0), prisonCode = prison.code)

    // When
    // call add exclude dates first
    var responseSpec = callAddPrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, excludeDate)

    // Then
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventServiceSpy, times(1)).handleAddPrisonVisitBlockDate(PrisonDateBlockedDto(prison.code, excludeDate))
    var visitNotifications = testVisitNotificationEventRepository.findAll()
    Assertions.assertThat(visitNotifications[0].bookingReference).isEqualTo(bookedVisitForSamePrison.reference)

    // call remove exclude dates next
    responseSpec = callRemovePrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, excludeDate)
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventServiceSpy, times(1)).handleRemovePrisonVisitBlockDate(PrisonDateBlockedDto(prison.code, excludeDate))
    visitNotifications = testVisitNotificationEventRepository.findAll()
    Assertions.assertThat(visitNotifications).hasSize(0)
  }

  @Test
  fun `when excluded date visit is updated then visit flag is removed`() {
    // Given
    val prison = PrisonEntityHelper.createPrisonDto("XYZ")
    prisonEntityHelper.create(prison.code, prison.active)
    val excludeDate = LocalDate.now().plusDays(10)
    val sessionTemplate = sessionTemplateEntityHelper.create()

    // existing visit for excludeDate in same prison
    val bookedVisit = visitEntityHelper.create(visitStatus = VisitStatus.BOOKED, visitStart = excludeDate.atTime(10, 0), visitEnd = excludeDate.atTime(11, 0), prisonCode = prison.code, sessionTemplateReference = sessionTemplate.reference)

    // When
    // call add exclude dates first
    var responseSpec = callAddPrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, excludeDate)

    // Then
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventServiceSpy, times(1)).handleAddPrisonVisitBlockDate(PrisonDateBlockedDto(prison.code, excludeDate))
    var visitNotifications = testVisitNotificationEventRepository.findAll()
    Assertions.assertThat(visitNotifications[0].bookingReference).isEqualTo(bookedVisit.reference)

    // create a reserveVisitSlotDto with start and end timestamp different from current visit
    val reserveVisitSlotDto = ReserveVisitSlotDto(
      prisonerId = bookedVisit.prisonerId,
      startTimestamp = bookedVisit.visitStart.plusDays(1),
      endTimestamp = bookedVisit.visitEnd.plusDays(1),
      visitRestriction = bookedVisit.visitRestriction,
      visitContact = ContactDto("John Smith", "013448811538"),
      visitors = setOf(VisitorDto(123, true), VisitorDto(124, false)),
      visitorSupport = setOf(VisitorSupportDto("OTHER", "Some Text")),
      actionedBy = "John Smith",
      sessionTemplateReference = bookedVisit.sessionTemplateReference!!,
    )

    responseSpec = callVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto, bookedVisit.reference)

    val returnResult = responseSpec.expectStatus().isCreated
      .expectBody()
      .returnResult()

    val updatedVisit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)

    callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, updatedVisit.applicationReference)
    responseSpec.expectStatus().isCreated

    visitNotifications = testVisitNotificationEventRepository.findAll()
    Assertions.assertThat(visitNotifications).hasSize(0)
  }

  @Test
  fun `when excluded date visit is updated but date not changed then visit flag is not removed`() {
    // Given
    val prison = PrisonEntityHelper.createPrisonDto("XYZ")
    prisonEntityHelper.create(prison.code, prison.active)
    val excludeDate = LocalDate.now().plusDays(10)
    val sessionTemplate = sessionTemplateEntityHelper.create()

    // existing visit for excludeDate in same prison
    val bookedVisit = visitEntityHelper.create(visitStatus = VisitStatus.BOOKED, visitStart = excludeDate.atTime(10, 0), visitEnd = excludeDate.atTime(11, 0), prisonCode = prison.code, sessionTemplateReference = sessionTemplate.reference)

    // When
    // call add exclude dates first
    var responseSpec = callAddPrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, excludeDate)

    // Then
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventServiceSpy, times(1)).handleAddPrisonVisitBlockDate(PrisonDateBlockedDto(prison.code, excludeDate))
    var visitNotifications = testVisitNotificationEventRepository.findAll()
    Assertions.assertThat(visitNotifications[0].bookingReference).isEqualTo(bookedVisit.reference)

    // create a reserveVisitSlotDto but no change to visit dates
    val reserveVisitSlotDto = ReserveVisitSlotDto(
      prisonerId = bookedVisit.prisonerId,
      startTimestamp = bookedVisit.visitStart,
      endTimestamp = bookedVisit.visitEnd,
      visitRestriction = bookedVisit.visitRestriction,
      visitContact = ContactDto("John Smith", "013448811538"),
      visitors = setOf(VisitorDto(123, true), VisitorDto(124, false)),
      visitorSupport = setOf(VisitorSupportDto("OTHER", "Some Text")),
      actionedBy = "John Smith",
      sessionTemplateReference = bookedVisit.sessionTemplateReference!!,
    )

    responseSpec = callVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto, bookedVisit.reference)

    val returnResult = responseSpec.expectStatus().isCreated
      .expectBody()
      .returnResult()

    val updatedVisit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)

    callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, updatedVisit.applicationReference)
    responseSpec.expectStatus().isCreated

    visitNotifications = testVisitNotificationEventRepository.findAll()
    Assertions.assertThat(visitNotifications).hasSize(1)
  }
}
