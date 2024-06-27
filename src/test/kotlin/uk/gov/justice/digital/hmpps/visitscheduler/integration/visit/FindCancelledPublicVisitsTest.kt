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
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_CANCELLED_PUBLIC_VISITS_BY_BOOKER_REFERENCE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.helper.VisitAssertHelper
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate

@DisplayName("GET $GET_CANCELLED_PUBLIC_VISITS_BY_BOOKER_REFERENCE")
class FindCancelledPublicVisitsTest : IntegrationTestBase() {

  @Autowired
  private lateinit var visitAssertHelper: VisitAssertHelper

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  private lateinit var otherSessionTemplate: SessionTemplate

  private lateinit var visitCancelledMostRecent: Visit
  private lateinit var visitCancelledInDifferentPrison: Visit
  private lateinit var visitCancelledInPast: Visit
  private lateinit var visitWithOtherBooker: Visit
  private lateinit var visitCancelledLeastRecent: Visit

  @BeforeEach
  internal fun createVisits() {
    otherSessionTemplate = sessionTemplateEntityHelper.create(prisonCode = "AWE")

    visitCancelledLeastRecent = createVisit(prisonId = "least recent", actionedByValue = "aTestRef", visitStatus = CANCELLED, sessionTemplate = sessionTemplateDefault, PUBLIC, slotDateWeeks = 4)

    visitCancelledInDifferentPrison = createVisit(prisonId = "diff prison", actionedByValue = "aTestRef", visitStatus = CANCELLED, sessionTemplate = otherSessionTemplate, PUBLIC, slotDateWeeks = 3)

    visitWithOtherBooker = createVisit(prisonId = "diff prison", actionedByValue = "aOtherTestRef", visitStatus = CANCELLED, sessionTemplate = sessionTemplateDefault, PUBLIC, slotDateWeeks = 2)

    visitCancelledInPast = createVisit(prisonId = "in past", actionedByValue = "aTestRef", visitStatus = CANCELLED, sessionTemplate = sessionTemplateDefault, PUBLIC, slotDateWeeks = -1)

    createVisit(actionedByValue = "aTestRef", visitStatus = BOOKED, sessionTemplate = sessionTemplateDefault, userType = PUBLIC, slotDateWeeks = 1)

    createVisit(actionedByValue = "aTestRef", visitStatus = BOOKED, sessionTemplate = sessionTemplateDefault, userType = PUBLIC, slotDateWeeks = -1)

    visitCancelledMostRecent = createVisit(prisonId = "most recent", actionedByValue = "aTestRef", CANCELLED, sessionTemplateDefault, userType = PUBLIC, slotDateWeeks = 1)
  }

  @Test
  fun `when cancelled public visits requested by booker reference aTestRef then associated visits are returned`() {
    // Given
    val bookerReference = "aTestRef"

    // When
    val responseSpec = callPublicCancelledVisitsEndPoint(bookerReference = bookerReference)

    // Then
    responseSpec.expectStatus().isOk
    val visitList = parseVisitsResponse(responseSpec)

    Assertions.assertThat(visitList.size).isEqualTo(4)
    visitAssertHelper.assertVisitDto(visitList[0], visitCancelledMostRecent)
    visitAssertHelper.assertVisitDto(visitList[1], visitCancelledInPast)
    visitAssertHelper.assertVisitDto(visitList[2], visitCancelledInDifferentPrison)
    visitAssertHelper.assertVisitDto(visitList[3], visitCancelledLeastRecent)
  }

  @Test
  fun `when booked public visits requested by booker reference aOtherTestRef then associated visits are returned`() {
    // Given
    val bookerReference = "aOtherTestRef"

    // When
    val responseSpec = callPublicCancelledVisitsEndPoint(bookerReference = bookerReference)

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
    val responseSpec = callPublicCancelledVisitsEndPoint(bookerReference = "aTestRole", roles = noRoles)

    // Then
    responseSpec.expectStatus().isForbidden
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `access forbidden when unknown role`() {
    // Given
    val noRoles = listOf<String>("SOME_OTHER_ROLE_VISIT_SCHEDULER")

    // When
    val responseSpec = callPublicCancelledVisitsEndPoint(bookerReference = "aTestRole", roles = noRoles)

    // Then
    responseSpec.expectStatus().isForbidden
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  fun callPublicCancelledVisitsEndPoint(
    bookerReference: String,
    roles: List<String> = listOf("ROLE_VISIT_SCHEDULER"),
  ): ResponseSpec {
    val uri = GET_CANCELLED_PUBLIC_VISITS_BY_BOOKER_REFERENCE.replace("{bookerReference}", bookerReference)
    return webTestClient.get().uri(uri)
      .headers(setAuthorisation(roles = roles))
      .exchange()
  }
}
