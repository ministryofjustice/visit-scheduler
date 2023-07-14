package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.ACTIVATE_SESSION_TEMPLATE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.DEACTIVATE_SESSION_TEMPLATE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateVisitStatsDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGetActivateSessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionTemplateRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@DisplayName("Session template tests for activate - $ACTIVATE_SESSION_TEMPLATE and deactivate  $DEACTIVATE_SESSION_TEMPLATE")
class AdminSessionTemplateVisitsTest(
  @Autowired private val testTemplateRepository: TestSessionTemplateRepository,
  @Value("\${policy.session.booking-notice-period.maximum-days:28}")
  private val policyNoticeDaysMax: Long,
) : IntegrationTestBase() {

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  private lateinit var sessionTemplate1: SessionTemplate
  private lateinit var sessionTemplate2: SessionTemplate

  @BeforeEach
  internal fun setUp() {
    sessionTemplate1 = sessionTemplateEntityHelper.create(
      name = "Session template 1",
      validFromDate = LocalDate.now(),
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      isActive = true,
    )
    sessionTemplate2 = sessionTemplateEntityHelper.create(
      name = "Session template 2",
      validFromDate = LocalDate.now(),
      startTime = LocalTime.parse("11:00"),
      endTime = LocalTime.parse("12:00"),
      isActive = true,
    )

    testTemplateRepository.saveAndFlush(sessionTemplate1)
    testTemplateRepository.saveAndFlush(sessionTemplate2)
  }

  @Test
  fun `when session template has visits then return expected results`() {
    // Given
    val reference1 = sessionTemplate1.reference
    val reference2 = sessionTemplate2.reference

    val visitsFromDateTime = LocalDateTime.now()

    visitEntityHelper.create(sessionTemplateReference = reference1, visitRestriction = OPEN, visitStart = visitsFromDateTime.plusDays(1))
    visitEntityHelper.create(sessionTemplateReference = reference1, visitRestriction = OPEN, visitStart = visitsFromDateTime.plusDays(1))
    visitEntityHelper.create(sessionTemplateReference = reference1, visitRestriction = OPEN, visitStart = visitsFromDateTime.plusDays(2))
    visitEntityHelper.create(sessionTemplateReference = reference1, visitRestriction = OPEN, visitStart = visitsFromDateTime.plusDays(3))
    visitEntityHelper.create(sessionTemplateReference = reference1, visitRestriction = CLOSED, visitStart = visitsFromDateTime.plusDays(4))
    visitEntityHelper.create(sessionTemplateReference = reference2, visitRestriction = OPEN, visitStart = visitsFromDateTime.plusDays(3))
    visitEntityHelper.create(sessionTemplateReference = reference2, visitRestriction = CLOSED, visitStart = visitsFromDateTime.plusDays(3))
    visitEntityHelper.create(sessionTemplateReference = reference1, visitRestriction = OPEN, visitStart = visitsFromDateTime.plusDays(policyNoticeDaysMax))
    visitEntityHelper.create(sessionTemplateReference = reference1, visitRestriction = CLOSED, visitStart = visitsFromDateTime.plusDays(policyNoticeDaysMax - 1))

    // When
    val responseSpec = callGetActivateSessionTemplate(webTestClient, reference1, visitsFromDateTime.toLocalDate(), setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
    val sessionTemplateVisitStatsDto = getSessionTemplateVisitStatsDto(responseSpec)

    Assertions.assertThat(sessionTemplateVisitStatsDto.visitCount).isEqualTo(6)
    Assertions.assertThat(sessionTemplateVisitStatsDto.minimumCapacity.open).isEqualTo(2)
    Assertions.assertThat(sessionTemplateVisitStatsDto.minimumCapacity.closed).isEqualTo(1)
  }

  private fun getSessionTemplateVisitStatsDto(responseSpec: ResponseSpec) =
    objectMapper.readValue(
      responseSpec.expectBody().returnResult().responseBody,
      SessionTemplateVisitStatsDto::class.java
    )

  @Test
  fun `when session templates has no visits then return expected results`() {
    // Given
    val reference1 = sessionTemplate1.reference
    val visitsFromDateTime = LocalDateTime.now()

    // When
    val responseSpec = callGetActivateSessionTemplate(webTestClient, reference1, visitsFromDateTime.toLocalDate(), setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
    val sessionTemplateVisitStatsDto = getSessionTemplateVisitStatsDto(responseSpec)

    Assertions.assertThat(sessionTemplateVisitStatsDto.visitCount).isEqualTo(0)
    Assertions.assertThat(sessionTemplateVisitStatsDto.minimumCapacity.open).isEqualTo(0)
    Assertions.assertThat(sessionTemplateVisitStatsDto.minimumCapacity.closed).isEqualTo(0)
  }

  @Test
  fun `when session templates has visits in the past then return expected results`() {
    // Given
    val reference1 = sessionTemplate1.reference
    val visitsFromDateTime = LocalDateTime.now()

    visitEntityHelper.create(sessionTemplateReference = reference1, visitRestriction = OPEN, visitStart = visitsFromDateTime.minusDays(1))
    visitEntityHelper.create(sessionTemplateReference = reference1, visitRestriction = OPEN, visitStart = visitsFromDateTime.minusDays(1))

    // When
    val responseSpec = callGetActivateSessionTemplate(webTestClient, reference1, visitsFromDateTime.toLocalDate(), setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
    val sessionTemplateVisitStatsDto = getSessionTemplateVisitStatsDto(responseSpec)

    Assertions.assertThat(sessionTemplateVisitStatsDto.visitCount).isEqualTo(0)
    Assertions.assertThat(sessionTemplateVisitStatsDto.minimumCapacity.open).isEqualTo(0)
    Assertions.assertThat(sessionTemplateVisitStatsDto.minimumCapacity.closed).isEqualTo(0)
  }

  @Test
  fun `when session templates has visits past the policyNoticeDaysMax then return expected results`() {
    // Given
    val reference1 = sessionTemplate1.reference
    val visitsFromDateTime = LocalDateTime.now()

    visitEntityHelper.create(sessionTemplateReference = reference1, visitRestriction = OPEN, visitStart = visitsFromDateTime.plusDays(policyNoticeDaysMax))

    // When
    val responseSpec = callGetActivateSessionTemplate(webTestClient, reference1, visitsFromDateTime.toLocalDate(), setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
    val sessionTemplateVisitStatsDto = getSessionTemplateVisitStatsDto(responseSpec)

    Assertions.assertThat(sessionTemplateVisitStatsDto.visitCount).isEqualTo(0)
    Assertions.assertThat(sessionTemplateVisitStatsDto.minimumCapacity.open).isEqualTo(0)
    Assertions.assertThat(sessionTemplateVisitStatsDto.minimumCapacity.closed).isEqualTo(0)
  }
}
