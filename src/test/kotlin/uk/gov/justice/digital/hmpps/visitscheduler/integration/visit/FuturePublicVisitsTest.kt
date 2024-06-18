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
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_BOOKED_FUTURE_PUBLIC_VISITS_BY_BOOKER_REFERENCE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.helper.VisitAssertHelper
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate

@DisplayName("GET $GET_BOOKED_FUTURE_PUBLIC_VISITS_BY_BOOKER_REFERENCE")
class FuturePublicVisitsTest : IntegrationTestBase() {

  @Autowired
  private lateinit var visitAssertHelper: VisitAssertHelper

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  private lateinit var otherSessionTemplate: SessionTemplate

  private lateinit var nearestVisit: Visit
  private lateinit var visitFarInTheFuture: Visit
  private lateinit var visitInDifferentPrison: Visit
  private lateinit var visitWithOtherBooker: Visit

  @BeforeEach
  internal fun createVisits() {
    otherSessionTemplate = sessionTemplateEntityHelper.create(prisonCode = "AWE")

    visitFarInTheFuture = createVisit(prisonId = "visit far away", actionedByValue = "aTestRef", visitStatus = VisitStatus.BOOKED, sessionTemplate = sessionTemplateDefault, userType = PUBLIC, slotDateWeeks = 6)

    visitInDifferentPrison = createVisit(prisonId = "visit different prison", actionedByValue = "aTestRef", visitStatus = VisitStatus.BOOKED, sessionTemplate = otherSessionTemplate, userType = PUBLIC, slotDateWeeks = 4)

    nearestVisit = createVisit(prisonId = "nearest visit", actionedByValue = "aTestRef", visitStatus = VisitStatus.BOOKED, sessionTemplate = sessionTemplateDefault, userType = PUBLIC, slotDateWeeks = 1)

    var visitInPast = createVisit(prisonId = "visit", actionedByValue = "aTestRef", visitStatus = VisitStatus.BOOKED, sessionTemplate = sessionTemplateDefault, userType = PUBLIC, slotDateWeeks = -1)

    var visitBookerByStaff = createVisit(prisonId = "visit", actionedByValue = "aTestRef", visitStatus = VisitStatus.BOOKED, sessionTemplate = sessionTemplateDefault, userType = STAFF, slotDateWeeks = 1)

    var visitCancelled = createVisit(prisonId = "visit", actionedByValue = "aTestRef", visitStatus = VisitStatus.CANCELLED, sessionTemplate = sessionTemplateDefault, userType = PUBLIC, slotDateWeeks = 1)

    visitWithOtherBooker = createVisit(prisonId = "visit with other broker", actionedByValue = "aOtherTestRef", visitStatus = VisitStatus.BOOKED, sessionTemplate = sessionTemplateDefault, userType = PUBLIC, slotDateWeeks = 2)
  }

  @Test
  fun `when booked public visits requested by booker reference aTestRef then associated visits are returned in the correct order`() {
    // Given
    val bookerReference = "aTestRef"

    // When
    val responseSpec = callPublicFutureVisitsEndPoint(bookerReference = bookerReference)

    // Then
    responseSpec.expectStatus().isOk
    val visitList = parseVisitsResponse(responseSpec)

    Assertions.assertThat(visitList.size).isEqualTo(3)
    visitAssertHelper.assertVisitDto(visitList[0], nearestVisit)
    visitAssertHelper.assertVisitDto(visitList[1], visitInDifferentPrison)
    visitAssertHelper.assertVisitDto(visitList[2], visitFarInTheFuture)
  }

  @Test
  fun `when booked public visits requested by booker reference aOtherTestRef then associated visits are returned`() {
    // Given
    val bookerReference = "aOtherTestRef"

    // When
    val responseSpec = callPublicFutureVisitsEndPoint(bookerReference = bookerReference)

    // Then
    responseSpec.expectStatus().isOk
    val visitList = parseVisitsResponse(responseSpec)

    Assertions.assertThat(visitList.size).isEqualTo(1)
    visitAssertHelper.assertVisitDto(visitList[0], visitWithOtherBooker)
  }

  @Test
  fun `access forbidden when no role`() {
    // Given
    val noRoles = listOf<String>()

    // When
    val responseSpec = callPublicFutureVisitsEndPoint(bookerReference = "aTestRole", roles = noRoles)

    // Then
    responseSpec.expectStatus().isForbidden
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `access forbidden when unknown role`() {
    // Given
    val noRoles = listOf<String>("SOME_OTHER_ROLE_VISIT_SCHEDULER")

    // When
    val responseSpec = callPublicFutureVisitsEndPoint(bookerReference = "aTestRole", roles = noRoles)

    // Then
    responseSpec.expectStatus().isForbidden
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  fun callPublicFutureVisitsEndPoint(
    bookerReference: String,
    roles: List<String> = listOf("ROLE_VISIT_SCHEDULER"),
  ): ResponseSpec {
    val uri = GET_BOOKED_FUTURE_PUBLIC_VISITS_BY_BOOKER_REFERENCE.replace("{bookerReference}", bookerReference)
    return webTestClient.get().uri(uri)
      .headers(setAuthorisation(roles = roles))
      .exchange()
  }
}
