package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.notifications

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NotificationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType.BOOKED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType.CHANGING_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType.UPDATED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType.NON_ASSOCIATION_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType.PRISONER_RESTRICTION_CHANGE_EVENT
import java.time.LocalDateTime

@DisplayName("GET /visits/notification/{prisonCode}/groups")
class FutureNotificationVisitGroupsTest : NotificationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val primaryPrisonerId = "AA11BCC"
  val secondaryPrisonerId = "XX11YZZ"
  val prisonCode = "ABC"

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when notification groups is requested for given prisons`() {
    // Given
    val visitPrimary = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(2),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
    )
    eventAuditEntityHelper.create(visitPrimary, type = BOOKED_VISIT)
    eventAuditEntityHelper.create(visitPrimary, type = UPDATED_VISIT, actionedBy = "IUpdatedIT")
    eventAuditEntityHelper.create(visitPrimary, type = CHANGING_VISIT, actionedBy = "IChangeSomething")

    val visitSecondary = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      visitStart = visitPrimary.visitStart,
      visitStatus = BOOKED,
      prisonCode = prisonCode,
    )
    eventAuditEntityHelper.create(visitSecondary)

    val visitOther = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(2),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
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
        Assertions.assertThat(visitDate).isEqualTo(visitPrimary.visitStart.toLocalDate())
        Assertions.assertThat(bookingReference).isEqualTo(visitPrimary.reference)
        Assertions.assertThat(this.bookedByUserName).isEqualTo("IUpdatedIT")
      }
    }

    with(dtoArray[1]) {
      Assertions.assertThat(reference).isEqualTo(otherNotification.reference)
      Assertions.assertThat(type).isEqualTo(PRISONER_RESTRICTION_CHANGE_EVENT)
      Assertions.assertThat(affectedVisits).hasSize(1)
    }
  }

  fun getNotificationGroupDtoDto(responseSpec: ResponseSpec): Array<NotificationGroupDto> =
    objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, Array<NotificationGroupDto>::class.java)

  fun callFutureNotificationVisitGroups(
    webTestClient: WebTestClient,
    prisonCode: String,
    authHttpHeaders: (HttpHeaders) -> Unit,
  ): ResponseSpec {
    return webTestClient.get().uri("/visits/notification/$prisonCode/groups")
      .headers(authHttpHeaders)
      .exchange()
  }
}
