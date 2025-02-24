package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.notifications

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.BOOKED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.CHANGING_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.UPDATED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.NON_ASSOCIATION_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_RELEASED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_RESTRICTION_CHANGE_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NotificationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@DisplayName("GET /visits/notification/{prisonCode}/groups")
class FutureNotificationVisitGroupsTest : NotificationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val primaryPrisonerId = "AA11BCC"
  val secondaryPrisonerId = "XX11YZZ"
  val prisonCode = "ABC"
  lateinit var prison1: Prison
  lateinit var sessionTemplate1: SessionTemplate

  @BeforeEach
  internal fun setUp() {
    prison1 = prisonEntityHelper.create(prisonCode = prisonCode)
    sessionTemplate1 = sessionTemplateEntityHelper.create(prison = prison1)
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when notification groups is requested for given prisons`() {
    // Given
    val visitPrimary = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(visitPrimary, type = BOOKED_VISIT)
    eventAuditEntityHelper.create(visitPrimary, type = UPDATED_VISIT, actionedByValue = "IUpdatedIT")
    eventAuditEntityHelper.create(visitPrimary, type = CHANGING_VISIT, actionedByValue = "IChangeSomething")

    val visitSecondary = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = visitPrimary.sessionSlot.slotDate,
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(visitSecondary)

    val visitOther = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(4),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(visitOther)

    val visitNotification = testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPrimary.reference, NON_ASSOCIATION_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitSecondary.reference, NON_ASSOCIATION_EVENT, _reference = visitNotification.reference))
    val otherNotification = testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitOther.reference, PRISONER_RESTRICTION_CHANGE_EVENT))

    // When
    val responseSpec = callFutureNotificationVisitGroups(webTestClient, prisonCode, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val dtoArray = this.getNotificationGroupDtoDto(responseSpec)
    Assertions.assertThat(dtoArray).hasSize(2)
    with(dtoArray[0]) {
      Assertions.assertThat(reference).isEqualTo(visitNotification.reference)
      Assertions.assertThat(type).isEqualTo(NON_ASSOCIATION_EVENT)
      Assertions.assertThat(affectedVisits).hasSize(2)
      with(affectedVisits[0]) {
        Assertions.assertThat(prisonerNumber).isEqualTo(visitPrimary.prisonerId)
        Assertions.assertThat(visitDate).isEqualTo(visitPrimary.sessionSlot.slotDate)
        Assertions.assertThat(bookingReference).isEqualTo(visitPrimary.reference)
        Assertions.assertThat(lastActionedBy.userName).isEqualTo("IUpdatedIT")
      }
    }

    with(dtoArray[1]) {
      Assertions.assertThat(reference).isEqualTo(otherNotification.reference)
      Assertions.assertThat(type).isEqualTo(PRISONER_RESTRICTION_CHANGE_EVENT)
      Assertions.assertThat(affectedVisits).hasSize(1)
    }
  }

  @Test
  fun `when notification groups are requested for a prison and visits exist then those visits are returned`() {
    // Given
    val futureSessionStartTime = LocalTime.MAX.truncatedTo(ChronoUnit.MINUTES)
    val pastSessionStartTime = LocalTime.MIN
    val prisonCode = "ABC"

    val sessionTemplate1 = sessionTemplateEntityHelper.create(startTime = futureSessionStartTime, prisonCode = prisonCode)
    val sessionTemplate2 = sessionTemplateEntityHelper.create(startTime = pastSessionStartTime, prisonCode = prisonCode)
    val contact = ContactDto(name = "ABC", telephone = "0123456789", email = "abc@example.com")

    val futureVisitToday = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now(),
      visitStatus = BOOKED,
      prisonCode = sessionTemplate1.prison.code,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(futureVisitToday)
    visitEntityHelper.createContact(futureVisitToday, name = contact.name, phone = contact.telephone, email = contact.email)
    visitEntityHelper.save(futureVisitToday)

    val pastVisitToday = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now(),
      visitStatus = BOOKED,
      prisonCode = sessionTemplate2.prison.code,
      sessionTemplate = sessionTemplate2,
    )
    eventAuditEntityHelper.create(pastVisitToday)
    visitEntityHelper.createContact(pastVisitToday, name = contact.name, phone = contact.telephone, email = contact.email)
    visitEntityHelper.save(pastVisitToday)

    val futureVisitTomorrow = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      prisonCode = sessionTemplate1.prison.code,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(futureVisitTomorrow)
    visitEntityHelper.createContact(futureVisitTomorrow, name = contact.name, phone = contact.telephone, email = contact.email)
    visitEntityHelper.save(futureVisitTomorrow)

    val pastVisitYesterday = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().minusDays(1),
      visitStatus = BOOKED,
      prisonCode = sessionTemplate2.prison.code,
      sessionTemplate = sessionTemplate2,
    )
    eventAuditEntityHelper.create(pastVisitYesterday)
    visitEntityHelper.createContact(pastVisitYesterday, name = contact.name, phone = contact.telephone, email = contact.email)
    visitEntityHelper.save(pastVisitYesterday)

    val visitNotification1 = testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(futureVisitToday.reference, PRISONER_RELEASED_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(pastVisitYesterday.reference, PRISONER_RELEASED_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(pastVisitToday.reference, PRISONER_RELEASED_EVENT))
    val visitNotification4 = testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(futureVisitTomorrow.reference, PRISONER_RELEASED_EVENT))

    // When
    val responseSpec = callFutureNotificationVisitGroups(webTestClient, prisonCode, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val dtoArray = this.getNotificationGroupDtoDto(responseSpec)
    Assertions.assertThat(dtoArray).hasSize(2)
    with(dtoArray[0]) {
      Assertions.assertThat(reference).isEqualTo(visitNotification1.reference)
      Assertions.assertThat(type).isEqualTo(PRISONER_RELEASED_EVENT)
      Assertions.assertThat(affectedVisits).hasSize(1)
      with(affectedVisits[0]) {
        Assertions.assertThat(prisonerNumber).isEqualTo(futureVisitToday.prisonerId)
        Assertions.assertThat(visitDate).isEqualTo(futureVisitToday.sessionSlot.slotDate)
        Assertions.assertThat(bookingReference).isEqualTo(futureVisitToday.reference)
      }
    }

    with(dtoArray[1]) {
      Assertions.assertThat(reference).isEqualTo(visitNotification4.reference)
      Assertions.assertThat(type).isEqualTo(PRISONER_RELEASED_EVENT)
      Assertions.assertThat(affectedVisits).hasSize(1)
      with(affectedVisits[0]) {
        Assertions.assertThat(prisonerNumber).isEqualTo(futureVisitTomorrow.prisonerId)
        Assertions.assertThat(visitDate).isEqualTo(futureVisitTomorrow.sessionSlot.slotDate)
        Assertions.assertThat(bookingReference).isEqualTo(futureVisitTomorrow.reference)
      }
    }
  }

  fun getNotificationGroupDtoDto(responseSpec: ResponseSpec): Array<NotificationGroupDto> = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, Array<NotificationGroupDto>::class.java)

  fun callFutureNotificationVisitGroups(
    webTestClient: WebTestClient,
    prisonCode: String,
    authHttpHeaders: (HttpHeaders) -> Unit,
  ): ResponseSpec = webTestClient.get().uri("/visits/notification/$prisonCode/groups")
    .headers(authHttpHeaders)
    .exchange()
}
