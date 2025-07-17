package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_REQUESTS_APPROVE_VISIT_BY_REFERENCE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callApproveVisitRequest
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase

@Transactional(propagation = SUPPORTS)
@DisplayName("Get $VISIT_REQUESTS_APPROVE_VISIT_BY_REFERENCE_PATH")
class ApproveVisitRequestTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val primaryPrisonerId = "AA11BCC"
  val secondaryPrisonerId = "XX11YZZ"

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when approve visit requests endpoint is called, then visit is successfully approved`() {
    // Given
    val visitPrimary = createApplicationAndVisit(sessionTemplate = sessionTemplateDefault, visitRestriction = VisitRestriction.OPEN, visitStatus = BOOKED, visitSubStatus = VisitSubStatus.REQUESTED)
    eventAuditEntityHelper.create(visitPrimary, type = EventAuditType.REQUESTED_VISIT)

    // When
    val responseSpec = callApproveVisitRequest(webTestClient, visitPrimary.reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val approvedVisit = getApproveVisitRequestResponse(responseSpec)
    assertThat(approvedVisit.reference).isEqualTo(visitPrimary.reference)
    assertThat(approvedVisit.visitSubStatus).isEqualTo(VisitSubStatus.APPROVED)
  }

  @Test
  fun `when approve visit requests endpoint is called, but no visit exists then 404 is thrown`() {
    // Given no visit exists on the visit-scheduler

    // When
    val responseSpec = callApproveVisitRequest(webTestClient, "no_visit", roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isNotFound
  }

  private fun getApproveVisitRequestResponse(responseSpec: WebTestClient.ResponseSpec): VisitDto = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, VisitDto::class.java)
}
