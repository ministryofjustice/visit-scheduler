package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.http.HttpHeaders
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_VISIT_EVENTS_BY_BOOKER_REFERENCE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ApproveRejectionVisitRequestBodyDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.audit.EventAuditDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus.IN_PROGRESS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus.AUTO_APPROVED
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callApproveVisitRequest
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCancelVisit
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitUpdate
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase

@DisplayName("GET $GET_VISIT_EVENTS_BY_BOOKER_REFERENCE")
class PublicEventsByBookerReferenceTest : IntegrationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @MockitoSpyBean
  private lateinit var telemetryClient: TelemetryClient

  @Test
  fun `when events are requested by booker reference all relevant events for booker are returned`() {
    // Given
    val bookerReference = "aTestRef"

    val bookerVisit = createVisit(prisonerId = "XYZ", actionedByValue = bookerReference, visitStatus = BOOKED, visitSubStatus = AUTO_APPROVED, sessionTemplate = sessionTemplateDefault, userType = PUBLIC, slotDateWeeks = 1)

    var updateApplication = applicationEntityHelper.create(sessionTemplate = sessionTemplateDefault, applicationStatus = IN_PROGRESS)
    applicationEntityHelper.createContact(application = updateApplication, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = updateApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = updateApplication, description = "Some Text")
    updateApplication.visit = bookerVisit
    updateApplication.visitId = bookerVisit.id
    updateApplication = applicationEntityHelper.save(updateApplication)

    // updated by STAFF so no updated events should be returned
    callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, updateApplication.reference, userType = UserType.STAFF)

    // now request a visit
    val requestedVisit = createRequestedVisit(prisonerId = "XYZ", actionedByValue = bookerReference, visitStatus = BOOKED, visitSubStatus = VisitSubStatus.REQUESTED, sessionTemplate = sessionTemplateDefault, userType = PUBLIC, slotDateWeeks = 4)
    // STAFF approves the visit
    callApproveVisitRequest(webTestClient, requestedVisit.reference, ApproveRejectionVisitRequestBodyDto(requestedVisit.reference, actionedBy = "STAFF_USER"), roleVisitSchedulerHttpHeaders)
    // booker withdraws the requested visit
    val withDrawVisitDto = CancelVisitDto(
      cancelOutcome = OutcomeDto(OutcomeStatus.REQUESTED_VISIT_WITHDRAWN),
      bookerReference,
      userType = PUBLIC,
      applicationMethodType = ApplicationMethodType.WEBSITE,
    )
    callCancelVisit(webTestClient, roleVisitSchedulerHttpHeaders, reference = requestedVisit.reference, withDrawVisitDto)

    // finally, booker cancels the first visit
    val cancelVisitDto = CancelVisitDto(
      cancelOutcome = OutcomeDto(OutcomeStatus.CANCELLATION),
      bookerReference,
      userType = PUBLIC,
      applicationMethodType = ApplicationMethodType.WEBSITE,
    )

    callCancelVisit(webTestClient, roleVisitSchedulerHttpHeaders, reference = bookerVisit.reference, cancelVisitDto)
    // When
    val responseSpec = callEventsByBookerReference(bookerReference = bookerReference)

    // Then
    responseSpec.expectStatus().isOk
    val eventsList = getResults(responseSpec)

    assertThat(eventsList.size).isEqualTo(4)
    assertThat(eventsList[0].type).isEqualTo(EventAuditType.CANCELLED_VISIT)
    assertThat(eventsList[0].bookingReference).isEqualTo(bookerVisit.reference)
    assertThat(eventsList[1].type).isEqualTo(EventAuditType.CANCELLED_VISIT)
    assertThat(eventsList[1].bookingReference).isEqualTo(requestedVisit.reference)
    assertThat(eventsList[2].type).isEqualTo(EventAuditType.REQUESTED_VISIT)
    assertThat(eventsList[2].bookingReference).isEqualTo(requestedVisit.reference)
    assertThat(eventsList[3].type).isEqualTo(EventAuditType.BOOKED_VISIT)
    assertThat(eventsList[3].bookingReference).isEqualTo(bookerVisit.reference)
  }

  @Test
  fun `when events are requested by booker reference and no events exist an empty list is returned`() {
    // Given
    val bookerReference = "aTestRef"

    val bookerVisit = createVisit(prisonerId = "XYZ", actionedByValue = "staff-user", visitStatus = BOOKED, visitSubStatus = AUTO_APPROVED, sessionTemplate = sessionTemplateDefault, userType = UserType.STAFF, slotDateWeeks = 1)

    // finally, staff cancels the  visit
    val cancelVisitDto = CancelVisitDto(
      cancelOutcome = OutcomeDto(OutcomeStatus.CANCELLATION),
      "staff-user2",
      userType = UserType.STAFF,
      applicationMethodType = ApplicationMethodType.WEBSITE,
    )

    callCancelVisit(webTestClient, roleVisitSchedulerHttpHeaders, reference = bookerVisit.reference, cancelVisitDto)
    // When
    val responseSpec = callEventsByBookerReference(bookerReference = bookerReference)

    // Then
    responseSpec.expectStatus().isOk
    val eventsList = getResults(responseSpec)

    assertThat(eventsList.size).isEqualTo(0)
  }

  @Test
  fun `access forbidden when no role`() {
    // Given
    val noRoles = listOf<String>()

    // When
    val responseSpec = callEventsByBookerReference(bookerReference = "aTestRole", roles = noRoles)

    // Then
    responseSpec.expectStatus().isForbidden
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `access forbidden when unknown role`() {
    // Given
    val noRoles = listOf("SOME_OTHER_ROLE_VISIT_SCHEDULER")

    // When
    val responseSpec = callEventsByBookerReference(bookerReference = "aTestRole", roles = noRoles)

    // Then
    responseSpec.expectStatus().isForbidden
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  fun callEventsByBookerReference(
    bookerReference: String,
    roles: List<String> = listOf("ROLE_VISIT_SCHEDULER"),
  ): ResponseSpec {
    val uri = GET_VISIT_EVENTS_BY_BOOKER_REFERENCE.replace("{bookerReference}", bookerReference)
    return webTestClient.get().uri(uri)
      .headers(setAuthorisation(roles = roles))
      .exchange()
  }

  fun getResults(responseSpec: ResponseSpec): List<EventAuditDto> = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, Array<EventAuditDto>::class.java).toList()
}
