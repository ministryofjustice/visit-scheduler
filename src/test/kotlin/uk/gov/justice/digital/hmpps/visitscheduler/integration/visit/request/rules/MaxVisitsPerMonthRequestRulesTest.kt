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

class MaxVisitsPerMonthRequestRulesTest : IntegrationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  private lateinit var reservedPublicApplication: Application

  private val prisonCode = "DFT"

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    reservedPublicApplication = applicationEntityHelper.create(sessionTemplate = sessionTemplateDefault, applicationStatus = IN_PROGRESS, userType = PUBLIC)
  }

  @Test
  fun `when visits already booked are less than max visits allowed for that month then session for the date should not be flagged`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    // create 3 visits for last month, 3 visits for next month, 2 visits for current month on a different session template
    val sessionTemplate1 = sessionTemplateEntityHelper.create(prisonCode = prisonCode, startTime = LocalTime.now().plusMinutes(5), endTime = LocalTime.now().plusHours(1))
    createBookedVisits(visitDate.minusMonths(1), totalVisits = 3, sessionTemplate = sessionTemplate1)
    createBookedVisits(visitDate.plusMonths(1), totalVisits = 3, sessionTemplate = sessionTemplate1)
    createBookedVisits(visitDate, totalVisits = 2, sessionTemplate = sessionTemplate1)

    // cancelled visits for the month should not be considered
    createCancelledVisits(visitDate, totalVisits = 4, sessionTemplate = sessionTemplate1)

    visitRequestRuleHelper.createMaxVisitsPerMonthRule(prisonCode, 3)

    val prisonerId = reservedPublicApplication.prisonerId
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, "john", "smith", prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // When
    val responseResult = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = roleVisitSchedulerHttpHeaders)

    // Then
    responseResult.expectStatus().isOk

    val sessions = getResults(responseResult.expectBody())
    // as there are only 2 visits booked for the month and max visits allowed is 3, the visit should get auto approved
    val session = sessions.firstOrNull { it.sessionTemplateReference == sessionTemplateDefault.reference && it.startTimestamp.toLocalDate() == visitDate }
    assertThat(session).isNotNull
    assertThat(session!!.sessionPrisonRuleFailures).isEmpty()
  }

  @Test
  fun `when visits already booked are same as max visits allowed for that month then session for the date should be flagged`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    // create 3 visits for last month, 3 visits for next month and 3 visits for current month on a different session template
    val sessionTemplate1 = sessionTemplateEntityHelper.create(prisonCode = prisonCode, startTime = LocalTime.now().plusMinutes(5), endTime = LocalTime.now().plusHours(1))
    createBookedVisits(visitDate.minusMonths(1), totalVisits = 3, sessionTemplate = sessionTemplate1)
    createBookedVisits(visitDate.plusMonths(1), totalVisits = 3, sessionTemplate = sessionTemplate1)
    createBookedVisits(visitDate, totalVisits = 3, sessionTemplate = sessionTemplate1)

    visitRequestRuleHelper.createMaxVisitsPerMonthRule(prisonCode, 3)

    val prisonerId = reservedPublicApplication.prisonerId
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, "john", "smith", prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // When
    val responseResult = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = roleVisitSchedulerHttpHeaders)

    // Then
    responseResult.expectStatus().isOk

    val sessions = getResults(responseResult.expectBody())
    // as there are already 3 visits booked for the month and max visits allowed are 3, the session should be flagged
    val session = sessions.firstOrNull { it.sessionTemplateReference == sessionTemplateDefault.reference && it.startTimestamp.toLocalDate() == visitDate }
    assertThat(session).isNotNull
    assertThat(session!!.sessionPrisonRuleFailures.size).isEqualTo(1)
    assertThat(session.sessionPrisonRuleFailures[0]).isEqualTo(PrisonVisitRequestRuleType.VISITS_PER_MONTH)
  }

  @Test
  fun `when visits already booked are more than max visits allowed for prison then visit sub status is set to REQUESTED`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    // create 3 visits for last month, 3 visits for next month and 5 visits for current month on a different session template
    val sessionTemplate1 = sessionTemplateEntityHelper.create(prisonCode = prisonCode, startTime = LocalTime.now().plusMinutes(5), endTime = LocalTime.now().plusHours(1))
    createBookedVisits(visitDate.minusMonths(1), totalVisits = 3, sessionTemplate = sessionTemplate1)
    createBookedVisits(visitDate.plusMonths(1), totalVisits = 3, sessionTemplate = sessionTemplate1)
    createBookedVisits(visitDate, totalVisits = 5, sessionTemplate = sessionTemplate1)

    visitRequestRuleHelper.createMaxVisitsPerMonthRule(prisonCode, 3)

    val prisonerId = reservedPublicApplication.prisonerId
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, "john", "smith", prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)

    // When
    val responseResult = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = roleVisitSchedulerHttpHeaders)

    // Then
    responseResult.expectStatus().isOk

    val sessions = getResults(responseResult.expectBody())
    // as there are already 3 visits booked for the month and max visits allowed are 3, the session should be flagged
    val session = sessions.firstOrNull { it.sessionTemplateReference == sessionTemplateDefault.reference && it.startTimestamp.toLocalDate() == visitDate }
    assertThat(session).isNotNull
    assertThat(session!!.sessionPrisonRuleFailures.size).isEqualTo(1)
    assertThat(session.sessionPrisonRuleFailures[0]).isEqualTo(PrisonVisitRequestRuleType.VISITS_PER_MONTH)
  }

  private fun createBookedVisits(firstDatOfMonth: LocalDate, totalVisits: Int, sessionTemplate: SessionTemplate) {
    createVisits(firstDatOfMonth, totalVisits, sessionTemplate, BOOKED, VisitSubStatus.AUTO_APPROVED)
  }

  private fun createCancelledVisits(firstDatOfMonth: LocalDate, totalVisits: Int, sessionTemplate: SessionTemplate) {
    createVisits(firstDatOfMonth, totalVisits, sessionTemplate, VisitStatus.CANCELLED, VisitSubStatus.CANCELLED)
  }

  private fun createVisits(firstDatOfMonth: LocalDate, totalVisits: Int, sessionTemplate: SessionTemplate, visitStatus: VisitStatus, visitSubStatus: VisitSubStatus) {
    for (dayOfMonth in 1..totalVisits) {
      val visitDate = LocalDate.of(firstDatOfMonth.year, firstDatOfMonth.month, dayOfMonth)
      visitEntityHelper.create(visitStatus = visitStatus, visitSubStatus = visitSubStatus, slotDate = visitDate, sessionTemplate = sessionTemplate, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    }
  }

  private fun getResults(returnResult: BodyContentSpec): Array<VisitSessionDto> = objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)
}
