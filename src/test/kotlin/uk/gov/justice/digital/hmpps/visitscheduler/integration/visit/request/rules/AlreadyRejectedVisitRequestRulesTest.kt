package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.request.rules

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import uk.gov.justice.digital.hmpps.visitscheduler.dto.BookingRequestVisitorDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus.IN_PROGRESS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonVisitRequestRuleType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.VisitBalancesDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch.PrisonerSearchResultDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestEventAuditRepository
import java.time.LocalTime

// TODO - enable this when these rules are checked the public service
@Disabled("disabled till rules are checked on the public service")
class AlreadyRejectedVisitRequestRulesTest : IntegrationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  private lateinit var reservedPublicApplication: Application

  private lateinit var visitorDetails: MutableSet<BookingRequestVisitorDetailsDto>

  private val prisonCode = "DFT"

  @Autowired
  private lateinit var testEventAuditRepository: TestEventAuditRepository

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    reservedPublicApplication = applicationEntityHelper.create(sessionTemplate = sessionTemplateDefault, applicationStatus = IN_PROGRESS, userType = PUBLIC)

    visitorDetails = mutableSetOf()
    visitorDetails.add(BookingRequestVisitorDetailsDto(321L, 21))
    visitorDetails.add(BookingRequestVisitorDetailsDto(322L, 25))
    visitorDetails.add(BookingRequestVisitorDetailsDto(323L, null))
  }

  @Test
  fun `when visit was already rejected for same time and same visitor list within rejection rule hours then session is flagged`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    // a visit for the same session and same visitor list was already rejected
    val rejectedVisit = visitEntityHelper.create(visitStatus = CANCELLED, visitSubStatus = VisitSubStatus.REJECTED, slotDate = visitDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 321L, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 322L, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 323L, visitContact = false)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT_REJECTED)
    visitEntityHelper.save(rejectedVisit)

    visitRequestRuleHelper.createAlreadyRejectedRequestRule(prisonCode, rejectionIntervalInHours = 4, totalRejectedVisits = 1)

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
    assertThat(session!!.sessionPrisonRuleFailures.size).isEqualTo(1)
    assertThat(session.sessionPrisonRuleFailures[0]).isEqualTo(PrisonVisitRequestRuleType.ALREADY_REJECTED_VISIT)
  }

  @Test
  fun `when visit was already rejected for same time and same visitor list within rejection rule hours but below allowed rejection limit then visit is not automatically rejected`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    // a visit for the same session and same visitor list was already rejected
    val rejectedVisit = visitEntityHelper.create(visitStatus = CANCELLED, visitSubStatus = VisitSubStatus.REJECTED, slotDate = visitDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 321L, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 322L, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 323L, visitContact = false)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT_REJECTED)
    visitEntityHelper.save(rejectedVisit)

    // total allowed rejection limit is 2
    visitRequestRuleHelper.createAlreadyRejectedRequestRule(prisonCode, rejectionIntervalInHours = 4, totalRejectedVisits = 2)

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
  fun `when visit was already rejected for same time but different visitor list within rejection rule hours then visit is not automatically rejected`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    // a visit for the same session but different visitor list was already rejected
    val rejectedVisit = visitEntityHelper.create(visitStatus = CANCELLED, visitSubStatus = VisitSubStatus.REJECTED, slotDate = visitDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 321L, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 322L, visitContact = false)
    // this visitor is different from the already rejected visit
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 324L, visitContact = false)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT_REJECTED)
    visitEntityHelper.save(rejectedVisit)

    // total allowed rejection limit is 1
    visitRequestRuleHelper.createAlreadyRejectedRequestRule(prisonCode, rejectionIntervalInHours = 4, totalRejectedVisits = 1)

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
  fun `when visit was already rejected but different session and same visitor list within rejection rule hours then visit is not automatically rejected`() {
    // Given
    val sessionTemplate1 = sessionTemplateEntityHelper.create(prisonCode = reservedPublicApplication.prison.code, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0))
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    // a visit for a different session and same visitor list was already rejected
    val rejectedVisit = visitEntityHelper.create(visitStatus = CANCELLED, visitSubStatus = VisitSubStatus.REJECTED, slotDate = visitDate, sessionTemplate = sessionTemplate1, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 321L, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 322L, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 323L, visitContact = false)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT_REJECTED)
    visitEntityHelper.save(rejectedVisit)

    // total allowed rejection limit is 1
    visitRequestRuleHelper.createAlreadyRejectedRequestRule(prisonCode, rejectionIntervalInHours = 4, totalRejectedVisits = 1)

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
  fun `when visit was already rejected for same time and same visitor list but before rejection rule hours then visit is not automatically rejected`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    // a visit for a different session and same visitor list was already rejected
    val rejectedVisit = visitEntityHelper.create(visitStatus = CANCELLED, visitSubStatus = VisitSubStatus.REJECTED, slotDate = visitDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 321L, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 322L, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 323L, visitContact = false)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT)
    val rejectedEventAudit = eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT_REJECTED)
    testEventAuditRepository.updateCreateTimeStamp(rejectedEventAudit.id, rejectedEventAudit.createTimestamp.minusHours(5))
    visitEntityHelper.save(rejectedVisit)

    // total allowed rejection limit is 1
    visitRequestRuleHelper.createAlreadyRejectedRequestRule(prisonCode, rejectionIntervalInHours = 4, totalRejectedVisits = 1)

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

  private fun getResults(returnResult: BodyContentSpec): Array<VisitSessionDto> = objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)
}
