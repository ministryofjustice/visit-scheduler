package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_FUTURE_BOOKED_PUBLIC_VISITS_BY_BOOKER_REFERENCE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_PAST_BOOKED_PUBLIC_VISITS_BY_BOOKER_REFERENCE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus.APPROVED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus.REJECTED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus.REQUESTED
import uk.gov.justice.digital.hmpps.visitscheduler.helper.VisitAssertHelper
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("GET $GET_FUTURE_BOOKED_PUBLIC_VISITS_BY_BOOKER_REFERENCE")
class PastPublicVisitsTest : IntegrationTestBase() {

  @Autowired
  private lateinit var visitAssertHelper: VisitAssertHelper

  @MockitoSpyBean
  private lateinit var telemetryClient: TelemetryClient

  private lateinit var sessionTemplate1: SessionTemplate
  private lateinit var sessionTemplate2: SessionTemplate

  private lateinit var otherSessionTemplate: SessionTemplate

  private lateinit var visitFarInThePast: Visit
  private lateinit var nearestPastVisitBeforeToday: Visit
  private lateinit var visitInDifferentPrison: Visit
  private lateinit var visitWithOtherBooker: Visit
  private lateinit var pastVisitToday: Visit
  private lateinit var futureVisitToday: Visit
  private lateinit var pastRequestedVisitApproved: Visit
  private lateinit var pastRequestedVisit: Visit

  private val defaultBookerReference: String = "aTestRef"

  @BeforeEach
  internal fun createVisits() {
    // session template that has a start time 5 minutes in the future
    sessionTemplate1 = sessionTemplateEntityHelper.create(prisonCode = "DFT", startTime = LocalTime.now().plusMinutes(5), endTime = LocalTime.now().plusHours(1), dayOfWeek = LocalDate.now().dayOfWeek)
    // session template that has started 1 minute back
    sessionTemplate2 = sessionTemplateEntityHelper.create(prisonCode = "DFT", startTime = LocalTime.now().minusMinutes(1), endTime = LocalTime.now().plusHours(1), dayOfWeek = LocalDate.now().dayOfWeek)

    // session template that has a start time 5 minutes in the future
    otherSessionTemplate = sessionTemplateEntityHelper.create(prisonCode = "AWE")

    visitFarInThePast = createVisit(prisonerId = "visit far in past", actionedByValue = defaultBookerReference, visitStatus = BOOKED, VisitSubStatus.AUTO_APPROVED, sessionTemplate = sessionTemplate1, userType = PUBLIC, slotDateWeeks = -6)

    visitInDifferentPrison = createVisit(prisonerId = "visit different prison", actionedByValue = defaultBookerReference, visitStatus = BOOKED, VisitSubStatus.AUTO_APPROVED, sessionTemplate = sessionTemplate1, userType = PUBLIC, slotDateWeeks = -4)

    nearestPastVisitBeforeToday = createVisit(prisonerId = "nearest visit in past before today", actionedByValue = defaultBookerReference, visitStatus = BOOKED, VisitSubStatus.AUTO_APPROVED, sessionTemplate = sessionTemplate1, userType = PUBLIC, slotDateWeeks = -1)

    // this visit is for a session template that started 1 minute back
    pastVisitToday = createVisit(prisonerId = "today's visit in past", actionedByValue = defaultBookerReference, visitStatus = BOOKED, VisitSubStatus.AUTO_APPROVED, sessionTemplate = sessionTemplate2, userType = PUBLIC, slotDateWeeks = 0)
    // this visit is for a session template that started 5 minutes after
    futureVisitToday = createVisit(prisonerId = "today's visit in future", actionedByValue = defaultBookerReference, visitStatus = BOOKED, VisitSubStatus.AUTO_APPROVED, sessionTemplate = sessionTemplate1, userType = PUBLIC, slotDateWeeks = 0)

    val visitInFuture = createVisit(prisonerId = "visit", actionedByValue = defaultBookerReference, visitStatus = BOOKED, VisitSubStatus.AUTO_APPROVED, sessionTemplate = sessionTemplate1, userType = PUBLIC, slotDateWeeks = 1)

    val visitBookerByStaff = createVisit(prisonerId = "visit", actionedByValue = defaultBookerReference, visitStatus = BOOKED, VisitSubStatus.AUTO_APPROVED, sessionTemplate = sessionTemplate1, userType = STAFF, slotDateWeeks = -1)

    val visitCancelled = createVisit(prisonerId = "visit", actionedByValue = defaultBookerReference, visitStatus = CANCELLED, VisitSubStatus.CANCELLED, sessionTemplate = sessionTemplate1, userType = PUBLIC, slotDateWeeks = -1)

    visitWithOtherBooker = createVisit(prisonerId = "visit with other broker", actionedByValue = "aOtherTestRef", visitStatus = BOOKED, VisitSubStatus.AUTO_APPROVED, sessionTemplate = sessionTemplate1, userType = PUBLIC, slotDateWeeks = -2)

    // approved visit in the past
    pastRequestedVisitApproved = createRequestedVisit(prisonerId = "requested-and-approved-in-past", actionedByValue = "aOtherTestRef", visitStatus = BOOKED, APPROVED, sessionTemplate = sessionTemplateDefault, slotDateWeeks = -2)
    // approved but in future
    val futureRequestedVisitApproved = createRequestedVisit(prisonerId = "requested-and-approved", actionedByValue = "aOtherTestRef", visitStatus = BOOKED, APPROVED, sessionTemplate = sessionTemplateDefault, slotDateWeeks = 2)

    // requested visit in the past that was not actioned
    pastRequestedVisit = createRequestedVisit(prisonerId = "requested-and-approved-in-past", actionedByValue = "aOtherTestRef", visitStatus = BOOKED, APPROVED, sessionTemplate = sessionTemplateDefault, slotDateWeeks = -3)
    // visit not actioned in the future
    val futureRequestedVisit = createRequestedVisit(prisonerId = "requested-not-actioned", actionedByValue = "aOtherTestRef", visitStatus = BOOKED, REQUESTED, sessionTemplate = sessionTemplateDefault, slotDateWeeks = 3)

    // rejected visit in the past
    val pastRequestedVisitRejected = createRequestedVisit(prisonerId = "requested-and-rejected-in-past", actionedByValue = "aOtherTestRef", visitStatus = CANCELLED, REJECTED, sessionTemplate = sessionTemplateDefault, slotDateWeeks = -3)
    // rejected visit in the future
    val futureRequestedVisitRejected = createRequestedVisit(prisonerId = "requested-and-rejected", actionedByValue = "aOtherTestRef", visitStatus = CANCELLED, REJECTED, sessionTemplate = sessionTemplateDefault, slotDateWeeks = 3)
  }

  @Test
  fun `when past booked public visits requested by booker reference aTestRef then associated visits are returned in the correct order`() {
    // Given
    val bookerReference = "aTestRef"

    // When
    val responseSpec = callPublicPastVisitsEndPoint(bookerReference = bookerReference)

    // Then
    responseSpec.expectStatus().isOk
    val visitList = parseVisitsResponse(responseSpec)

    Assertions.assertThat(visitList.size).isEqualTo(4)
    visitAssertHelper.assertVisitDto(visitList[0], pastVisitToday)
    visitAssertHelper.assertVisitDto(visitList[1], nearestPastVisitBeforeToday)
    visitAssertHelper.assertVisitDto(visitList[2], visitInDifferentPrison)
    visitAssertHelper.assertVisitDto(visitList[3], visitFarInThePast)
  }

  @Test
  fun `when past booked public visits requested by booker reference aOtherTestRef then associated visits are returned`() {
    // Given
    val bookerReference = "aOtherTestRef"

    // When
    val responseSpec = callPublicPastVisitsEndPoint(bookerReference = bookerReference)

    // Then
    responseSpec.expectStatus().isOk
    val visitList = parseVisitsResponse(responseSpec)

    Assertions.assertThat(visitList.size).isEqualTo(3)
    visitAssertHelper.assertVisitDto(visitList[0], visitWithOtherBooker)
    visitAssertHelper.assertVisitDto(visitList[1], pastRequestedVisitApproved)
    visitAssertHelper.assertVisitDto(visitList[2], pastRequestedVisit)
  }

  @Test
  fun `access forbidden when no role`() {
    // Given
    val noRoles = listOf<String>()

    // When
    val responseSpec = callPublicPastVisitsEndPoint(bookerReference = "aTestRole", roles = noRoles)

    // Then
    responseSpec.expectStatus().isForbidden
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `access forbidden when unknown role`() {
    // Given
    val noRoles = listOf("SOME_OTHER_ROLE_VISIT_SCHEDULER")

    // When
    val responseSpec = callPublicPastVisitsEndPoint(bookerReference = "aTestRole", roles = noRoles)

    // Then
    responseSpec.expectStatus().isForbidden
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  fun callPublicPastVisitsEndPoint(
    bookerReference: String,
    roles: List<String> = listOf("ROLE_VISIT_SCHEDULER"),
  ): ResponseSpec {
    val uri = GET_PAST_BOOKED_PUBLIC_VISITS_BY_BOOKER_REFERENCE.replace("{bookerReference}", bookerReference)
    return webTestClient.get().uri(uri)
      .headers(setAuthorisation(roles = roles))
      .exchange()
  }
}
