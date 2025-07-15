package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_REQUEST_COUNT_FOR_PRISON_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitRequestsCountDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCountVisitRequests
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
@DisplayName("Get $VISIT_REQUEST_COUNT_FOR_PRISON_PATH")
class CountVisitRequestsTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val primaryPrisonerId = "AA11BCC"
  val secondaryPrisonerId = "XX11YZZ"

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when visit requests count is requested for a prison, then correct count is returned`() {
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

    val visitSecondary = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      visitSubStatus = VisitSubStatus.REQUESTED,
      prisonCode = sessionTemplateDefault.prison.code,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitSecondary)

    // When
    val responseSpec = callCountVisitRequests(webTestClient, prisonCode = sessionTemplateDefault.prison.code, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val visitRequestsCountDto = getVisitRequestsCountDto(responseSpec)
    Assertions.assertThat(visitRequestsCountDto.count).isEqualTo(2)
  }

  @Test
  fun `when no requested visits exist for prison then count is zero`() {
    // Given

    val visitPrimary = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      visitSubStatus = VisitSubStatus.AUTO_APPROVED,
      prisonCode = sessionTemplateDefault.prison.code,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitPrimary)

    val visitSecondary = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      visitSubStatus = VisitSubStatus.AUTO_APPROVED,
      prisonCode = sessionTemplateDefault.prison.code,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitSecondary)

    // When
    val responseSpec = callCountVisitRequests(webTestClient, prisonCode = sessionTemplateDefault.prison.code, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val visitRequestsCountDto = getVisitRequestsCountDto(responseSpec)
    Assertions.assertThat(visitRequestsCountDto.count).isEqualTo(0)
  }

  @Test
  fun `when no visits exist at all for prison, then count is zero`() {
    // Given no visits exist at all

    // When
    val responseSpec = callCountVisitRequests(webTestClient, prisonCode = sessionTemplateDefault.prison.code, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val visitRequestsCountDto = getVisitRequestsCountDto(responseSpec)
    Assertions.assertThat(visitRequestsCountDto.count).isEqualTo(0)
  }

  fun getVisitRequestsCountDto(responseSpec: ResponseSpec): VisitRequestsCountDto = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, VisitRequestsCountDto::class.java)
}
