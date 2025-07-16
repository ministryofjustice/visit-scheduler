package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_REQUESTS_APPROVE_VISIT_BY_REFERENCE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ApproveVisitRequestResponseDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch.PrisonerSearchResultDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callApproveVisitRequest
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitRepository
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
@DisplayName("Get $VISIT_REQUESTS_APPROVE_VISIT_BY_REFERENCE_PATH")
class ApproveVisitRequestTest : IntegrationTestBase() {

  @Autowired
  private lateinit var testVisitRepository: TestVisitRepository

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
    val visitPrimary = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      visitSubStatus = VisitSubStatus.REQUESTED,
      prisonCode = sessionTemplateDefault.prison.code,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitPrimary)

    val prisonerSearchResult = PrisonerSearchResultDto(
      prisonerNumber = visitPrimary.prisonerId,
      firstName = "John",
      lastName = "Smith",
    )

    // When
    prisonOffenderSearchMockServer.stubGetPrisoner(visitPrimary.prisonerId, prisonerSearchResult)
    val responseSpec = callApproveVisitRequest(webTestClient, visitPrimary.reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val approveVisitRequestResponse = getApproveVisitRequestResponse(responseSpec)
    assertThat(approveVisitRequestResponse.visitReference).isEqualTo(visitPrimary.reference)
    assertThat(approveVisitRequestResponse.prisonerFirstName).isEqualTo(prisonerSearchResult.firstName)
    assertThat(approveVisitRequestResponse.prisonerLastName).isEqualTo(prisonerSearchResult.lastName)

    val updatedVisit = testVisitRepository.findByReference(visitPrimary.reference)
    assertThat(updatedVisit.visitSubStatus).isEqualTo(VisitSubStatus.APPROVED)
  }

  @Test
  fun `when approve visit requests endpoint is called, but prisoner-search fails, then visit is still successfully approved`() {
    // Given
    val visitPrimary = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      visitSubStatus = VisitSubStatus.REQUESTED,
      prisonCode = sessionTemplateDefault.prison.code,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitPrimary)

    // When
    prisonOffenderSearchMockServer.stubGetPrisoner(visitPrimary.prisonerId, null)
    val responseSpec = callApproveVisitRequest(webTestClient, visitPrimary.reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val approveVisitRequestResponse = getApproveVisitRequestResponse(responseSpec)
    assertThat(approveVisitRequestResponse.visitReference).isEqualTo(visitPrimary.reference)
    assertThat(approveVisitRequestResponse.prisonerFirstName).isEqualTo(visitPrimary.prisonerId)
    assertThat(approveVisitRequestResponse.prisonerLastName).isEqualTo(visitPrimary.prisonerId)

    val updatedVisit = testVisitRepository.findByReference(visitPrimary.reference)
    assertThat(updatedVisit.visitSubStatus).isEqualTo(VisitSubStatus.APPROVED)
  }

  private fun getApproveVisitRequestResponse(responseSpec: ResponseSpec): ApproveVisitRequestResponseDto = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, ApproveVisitRequestResponseDto::class.java)
}
