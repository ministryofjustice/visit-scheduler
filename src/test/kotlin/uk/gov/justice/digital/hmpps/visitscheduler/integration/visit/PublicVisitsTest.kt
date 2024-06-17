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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.BOOKED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.CANCELLED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.RESERVED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.helper.VisitAssertHelper
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate

@DisplayName("GET $GET_BOOKED_FUTURE_PUBLIC_VISITS_BY_BOOKER_REFERENCE")
class PublicVisitsTest : IntegrationTestBase() {

  @Autowired
  private lateinit var visitAssertHelper: VisitAssertHelper

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  private lateinit var otherSessionTemplate: SessionTemplate

  private lateinit var visit: Visit
  private lateinit var visitInDifferentPrison: Visit
  private lateinit var visitWithOtherBooker: Visit

  @BeforeEach
  internal fun createVisits() {
    otherSessionTemplate = sessionTemplateEntityHelper.create(prisonCode = "AWE")

    visitInDifferentPrison = createApplicationAndVisit(prisonerId = "AA0000A", slotDate = startDate.plusWeeks(1), sessionTemplate = otherSessionTemplate, userType = PUBLIC)
    visitInDifferentPrison = visitEntityHelper.save(visitInDifferentPrison)

    eventAuditEntityHelper.createForVisitAndApplication(visitInDifferentPrison, actionedByValue = "aTestRef", type = listOf(RESERVED_VISIT, BOOKED_VISIT))

    visit = createApplicationAndVisit(prisonerId = "FF0000CC", slotDate = startDate.plusDays(2), sessionTemplate = sessionTemplateDefault, userType = PUBLIC)
    visit = visitEntityHelper.save(visit)

    eventAuditEntityHelper.createForVisitAndApplication(visit, actionedByValue = "aTestRef", type = listOf(RESERVED_VISIT, BOOKED_VISIT))

    var visitInPast = createApplicationAndVisit(prisonerId = "FF0000CC", slotDate = LocalDate.now().minusDays(1), sessionTemplate = sessionTemplateDefault, userType = PUBLIC)
    visitInPast = visitEntityHelper.save(visitInPast)

    eventAuditEntityHelper.createForVisitAndApplication(visitInPast, actionedByValue = "aTestRef", type = listOf(RESERVED_VISIT, BOOKED_VISIT))

    var visitBookerByStaff = createApplicationAndVisit(prisonerId = "FF0000CC", slotDate = LocalDate.now().minusDays(1), sessionTemplate = sessionTemplateDefault, userType = STAFF)
    visitBookerByStaff = visitEntityHelper.save(visitBookerByStaff)

    eventAuditEntityHelper.createForVisitAndApplication(visitBookerByStaff, actionedByValue = "aTestRef", type = listOf(RESERVED_VISIT, BOOKED_VISIT))

    var visitCancelled = createApplicationAndVisit(prisonerId = "FF0000CC", slotDate = startDate.plusDays(3), sessionTemplate = sessionTemplateDefault, visitStatus = CANCELLED, userType = PUBLIC)
    visitCancelled.outcomeStatus = OutcomeStatus.CANCELLATION
    visitCancelled = visitEntityHelper.save(visitCancelled)

    eventAuditEntityHelper.createForVisitAndApplication(visitCancelled, actionedByValue = "aTestRef", type = listOf(RESERVED_VISIT, BOOKED_VISIT, CANCELLED_VISIT))

    visitWithOtherBooker = createApplicationAndVisit(prisonerId = "AA0000A", slotDate = startDate.plusWeeks(1), sessionTemplate = otherSessionTemplate, userType = PUBLIC)
    visitWithOtherBooker = visitEntityHelper.save(visitWithOtherBooker)

    eventAuditEntityHelper.createForVisitAndApplication(visitWithOtherBooker, actionedByValue = "aOtherTestRef", type = listOf(RESERVED_VISIT, BOOKED_VISIT))
  }

  @Test
  fun `when booked public visits requested by booker reference aTestRef then associated visits are returned`() {
    // Given
    val bookerReference = "aTestRef"

    // When
    val responseSpec = callPublicFutureVisitsEndPoint(bookerReference = bookerReference)

    // Then
    responseSpec.expectStatus().isOk
    val visitList = parseVisitsResponse(responseSpec)

    Assertions.assertThat(visitList.size).isEqualTo(2)
    visitAssertHelper.assertVisitDto(visitList[0], visitInDifferentPrison)
    visitAssertHelper.assertVisitDto(visitList[1], visit)
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
