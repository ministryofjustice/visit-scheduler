package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.request.rules

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus.IN_PROGRESS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonVisitRequestRuleType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.VisitBalancesDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch.PrisonerSearchResultDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate
import java.time.LocalTime

class VisitIntervalRequestRulesTest : IntegrationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  private lateinit var reservedPublicApplication: Application

  private val prisonCode = "DFT"

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    reservedPublicApplication = applicationEntityHelper.create(sessionTemplate = sessionTemplateDefault, applicationStatus = IN_PROGRESS, userType = PUBLIC)
  }

  @Test
  fun `when visits already booked are less than allowed limit for prison then visit sub status is set to AUTO_APPROVED`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    val sessionTemplate1 = sessionTemplateEntityHelper.create(prisonCode = prisonCode, startTime = LocalTime.now().plusMinutes(5), endTime = LocalTime.now().plusHours(1))
    // 1 visit exists on the previous day and 1 on the next day
    createBookedVisits(visitDate.minusDays(1), totalVisits = 1, sessionTemplate = sessionTemplate1)
    createBookedVisits(visitDate.plusDays(1), totalVisits = 1, sessionTemplate = sessionTemplate1)

    // cancelled visits should not be considered
    createCancelledVisits(visitDate.minusDays(1), totalVisits = 4, sessionTemplate = sessionTemplate1)

    visitRequestRuleHelper.createVisitIntervalRule(prisonCode, allowedVisits = 2, intervalDays = 1)

    val prisonerId = reservedPublicApplication.prisonerId
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, "john", "smith", prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // When
    val responseResult = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = roleVisitSchedulerHttpHeaders)

    // Then
    responseResult.expectStatus().isOk

    val sessions = getResults(responseResult.expectBody())
    // as there is 1 visit before and 1 visit after the visit date and hence less than allowed, the visit should not be flagged
    val session = sessions.firstOrNull { it.sessionTemplateReference == sessionTemplateDefault.reference && it.startTimestamp.toLocalDate() == visitDate }
    assertThat(session).isNotNull
    assertThat(session!!.sessionPrisonRuleFailures).isEmpty()
  }

  @Test
  fun `when visits already booked before visit date are more than allowed limit for prison then visit sub status is set to REQUESTED`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    val sessionTemplate1 = sessionTemplateEntityHelper.create(prisonCode = prisonCode, startTime = LocalTime.now().plusMinutes(5), endTime = LocalTime.now().plusHours(1))
    // 2 visit exists on the previous day and 1 on the next day
    createBookedVisits(visitDate.minusDays(1), totalVisits = 2, sessionTemplate = sessionTemplate1)
    createBookedVisits(visitDate.plusDays(1), totalVisits = 1, sessionTemplate = sessionTemplate1)

    // cancelled visits should not be considered
    createCancelledVisits(visitDate.minusDays(1), totalVisits = 3, sessionTemplate = sessionTemplate1)

    visitRequestRuleHelper.createVisitIntervalRule(prisonCode, allowedVisits = 2, intervalDays = 1)

    val prisonerId = reservedPublicApplication.prisonerId
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, "john", "smith", prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // When
    val responseResult = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = roleVisitSchedulerHttpHeaders)

    // Then
    responseResult.expectStatus().isOk

    val sessions = getResults(responseResult.expectBody())
    // as there are 2 visits before and 1 visit after the visit date and hence more than allowed, the visit should be flagged
    val session = sessions.firstOrNull { it.sessionTemplateReference == sessionTemplateDefault.reference && it.startTimestamp.toLocalDate() == visitDate }
    assertThat(session).isNotNull
    assertThat(session!!.sessionPrisonRuleFailures.size).isEqualTo(1)
    assertThat(session.sessionPrisonRuleFailures[0]).isEqualTo(PrisonVisitRequestRuleType.VISIT_INTERVAL)
  }

  @Test
  fun `when visits already booked after visit date are more than allowed limit for prison then visit sub status is set to REQUESTED`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    val sessionTemplate1 = sessionTemplateEntityHelper.create(prisonCode = prisonCode, startTime = LocalTime.now().plusMinutes(5), endTime = LocalTime.now().plusHours(1))
    // 2 visit exists on the previous day and 1 on the next day
    createBookedVisits(visitDate.minusDays(1), totalVisits = 1, sessionTemplate = sessionTemplate1)
    createBookedVisits(visitDate.plusDays(1), totalVisits = 2, sessionTemplate = sessionTemplate1)

    // cancelled visits should not be considered
    createCancelledVisits(visitDate.minusDays(1), totalVisits = 4, sessionTemplate = sessionTemplate1)

    visitRequestRuleHelper.createVisitIntervalRule(prisonCode, allowedVisits = 2, intervalDays = 1)

    val prisonerId = reservedPublicApplication.prisonerId
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, "john", "smith", prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // When
    val responseResult = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = roleVisitSchedulerHttpHeaders)

    // Then
    responseResult.expectStatus().isOk

    val sessions = getResults(responseResult.expectBody())
    // as there are 1 visit before and 2 visits after the visit date and hence more than allowed, the visit should be flagged
    val session = sessions.firstOrNull { it.sessionTemplateReference == sessionTemplateDefault.reference && it.startTimestamp.toLocalDate() == visitDate }
    assertThat(session).isNotNull
    assertThat(session!!.sessionPrisonRuleFailures.size).isEqualTo(1)
    assertThat(session.sessionPrisonRuleFailures[0]).isEqualTo(PrisonVisitRequestRuleType.VISIT_INTERVAL)
  }

  @Test
  fun `when visits already booked for visit date are more than allowed limit for prison then visit sub status is set to REQUESTED`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    val sessionTemplate1 = sessionTemplateEntityHelper.create(prisonCode = prisonCode, startTime = LocalTime.now().plusMinutes(5), endTime = LocalTime.now().plusHours(1))
    val sessionTemplate2 = sessionTemplateEntityHelper.create(prisonCode = prisonCode, startTime = LocalTime.now().plusMinutes(5), endTime = LocalTime.now().plusHours(1))
    // 2 visit exists on the same day already
    createBookedVisits(visitDate, totalVisits = 1, sessionTemplate = sessionTemplate1)
    createBookedVisits(visitDate, totalVisits = 1, sessionTemplate = sessionTemplate2)

    // cancelled visits should not be considered
    createCancelledVisits(visitDate.minusDays(1), totalVisits = 4, sessionTemplate = sessionTemplate1)

    visitRequestRuleHelper.createVisitIntervalRule(prisonCode, allowedVisits = 2, intervalDays = 1)

    val prisonerId = reservedPublicApplication.prisonerId
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, "john", "smith", prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

// When
    val responseResult = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = roleVisitSchedulerHttpHeaders)

    // Then
    responseResult.expectStatus().isOk

    val sessions = getResults(responseResult.expectBody())
    // as there are 1 visit before and 2 visits after the visit date and hence more than allowed, the visit should be flagged
    val session = sessions.firstOrNull { it.sessionTemplateReference == sessionTemplateDefault.reference && it.startTimestamp.toLocalDate() == visitDate }
    assertThat(session).isNotNull
    assertThat(session!!.sessionPrisonRuleFailures.size).isEqualTo(1)
    assertThat(session.sessionPrisonRuleFailures[0]).isEqualTo(PrisonVisitRequestRuleType.VISIT_INTERVAL)
  }

  private fun createBookedVisits(visitDate: LocalDate, totalVisits: Int, sessionTemplate: SessionTemplate) {
    createVisits(visitDate, totalVisits, sessionTemplate, BOOKED, VisitSubStatus.AUTO_APPROVED)
  }

  private fun createCancelledVisits(visitDate: LocalDate, totalVisits: Int, sessionTemplate: SessionTemplate) {
    createVisits(visitDate, totalVisits, sessionTemplate, VisitStatus.CANCELLED, VisitSubStatus.CANCELLED)
  }

  private fun createVisits(visitDate: LocalDate, totalVisits: Int, sessionTemplate: SessionTemplate, visitStatus: VisitStatus, visitSubStatus: VisitSubStatus) {
    (1..totalVisits).forEach { _ ->
      visitEntityHelper.create(visitStatus = visitStatus, visitSubStatus = visitSubStatus, slotDate = visitDate, sessionTemplate = sessionTemplate, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    }
  }

  private fun getResults(returnResult: BodyContentSpec): Array<VisitSessionDto> = objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)
}
