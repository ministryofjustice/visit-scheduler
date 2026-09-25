package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.request.rules

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
class AlreadyRejectedVisitRequestRulesTest : IntegrationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  private lateinit var reservedPublicApplication: Application

  private lateinit var visitorDetails: MutableSet<BookingRequestVisitorDetailsDto>

  private val prisonCode = "DFT"

  private val visitor1Id = 321L
  private val visitor2Id = 322L
  private val visitor3Id = 323L
  private val visitor4Id = 324L

  @Autowired
  private lateinit var testEventAuditRepository: TestEventAuditRepository

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    reservedPublicApplication = applicationEntityHelper.create(sessionTemplate = sessionTemplateDefault, applicationStatus = IN_PROGRESS, userType = PUBLIC)

    visitorDetails = mutableSetOf()
    visitorDetails.add(BookingRequestVisitorDetailsDto(visitor1Id, 21))
    visitorDetails.add(BookingRequestVisitorDetailsDto(visitor2Id, 25))
    visitorDetails.add(BookingRequestVisitorDetailsDto(visitor3Id, null))
  }

  @Test
  fun `when visit was already rejected for same time and same visitor list then a session is flagged`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    // a visit for the same session and same visitor list was already rejected
    val rejectedVisit = visitEntityHelper.create(visitStatus = CANCELLED, visitSubStatus = VisitSubStatus.REJECTED, slotDate = visitDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = visitor1Id, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = visitor2Id, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = visitor3Id, visitContact = false)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT_REJECTED)
    visitEntityHelper.save(rejectedVisit)

    visitRequestRuleHelper.createAlreadyRejectedRequestRule(prisonCode, totalRejectedVisits = 1, rejectionIntervalInHours = 4)

    val prisonerId = reservedPublicApplication.prisonerId
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, "john", "smith", prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // When
    val responseResult = callGetSessions(prisonCode, prisonerId, userType = STAFF, visitorIds = listOf(visitor1Id, visitor2Id, visitor3Id), authHttpHeaders = roleVisitSchedulerHttpHeaders)

    // Then
    responseResult.expectStatus().isOk

    val sessions = getResults(responseResult.expectBody())

    // as there are already rejected visits for the same slot and date, the visit should be flagged
    val sessionsForSlotAndDate = sessions.filter { it.sessionTemplateReference == sessionTemplateDefault.reference && it.startTimestamp.toLocalDate() == visitDate }
    sessionsForSlotAndDate.forEach {
      assertThat(it.sessionPrisonRuleFailures.isNotEmpty()).isTrue
      assertThat(it.sessionPrisonRuleFailures.size).isEqualTo(1)
      assertThat(it.sessionPrisonRuleFailures[0]).isEqualTo(PrisonVisitRequestRuleType.ALREADY_REJECTED_VISIT)
    }

    val sessionsNotForVisitDate = sessions.filterNot { it.sessionTemplateReference == sessionTemplateDefault.reference && it.startTimestamp.toLocalDate() == visitDate }
    assertThat(sessionsNotForVisitDate.any { it.sessionPrisonRuleFailures.isNotEmpty() }).isFalse
  }

  @Test
  fun `when visit was already rejected for same time and same visitor list but is below the rejection limit then a session is not flagged`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    // a visit for the same session and same visitor list was already rejected
    val rejectedVisit = visitEntityHelper.create(visitStatus = CANCELLED, visitSubStatus = VisitSubStatus.REJECTED, slotDate = visitDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = visitor1Id, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = visitor2Id, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = visitor3Id, visitContact = false)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT_REJECTED)
    visitEntityHelper.save(rejectedVisit)

    // total allowed rejection limit is 2
    visitRequestRuleHelper.createAlreadyRejectedRequestRule(prisonCode, totalRejectedVisits = 2, rejectionIntervalInHours = 4)

    val prisonerId = reservedPublicApplication.prisonerId
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, "john", "smith", prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // When
    val responseResult = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = roleVisitSchedulerHttpHeaders)

    // Then
    responseResult.expectStatus().isOk

    val sessions = getResults(responseResult.expectBody())
    // as there are less than allowed rejected visits for the same slot and date, the visit should not be flagged
    assertThat(sessions.any { it.sessionPrisonRuleFailures.isNotEmpty() }).isFalse
  }

  @Test
  fun `when visit was already rejected for same time but different visitor list then a session is not flagged`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    // a visit for the same session but different visitor list was already rejected
    val rejectedVisit = visitEntityHelper.create(visitStatus = CANCELLED, visitSubStatus = VisitSubStatus.REJECTED, slotDate = visitDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = visitor1Id, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = visitor2Id, visitContact = false)
    // this visitor is different from the already rejected visit
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = visitor4Id, visitContact = false)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT_REJECTED)
    visitEntityHelper.save(rejectedVisit)

    // total allowed rejection limit is 1
    visitRequestRuleHelper.createAlreadyRejectedRequestRule(prisonCode, totalRejectedVisits = 1, rejectionIntervalInHours = 4)

    val prisonerId = reservedPublicApplication.prisonerId
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, "john", "smith", prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // When
    val responseResult = callGetSessions(prisonCode, prisonerId, userType = STAFF, visitorIds = listOf(visitor1Id, visitor2Id, visitor3Id), authHttpHeaders = roleVisitSchedulerHttpHeaders)

    // Then
    responseResult.expectStatus().isOk

    val sessions = getResults(responseResult.expectBody())
    // as the rejected visits are not for the same visitor list, the visit should not be flagged
    assertThat(sessions.any { it.sessionPrisonRuleFailures.isNotEmpty() }).isFalse
  }

  @Test
  fun `when visit was rejected for a different session but same visitor list then the other session is flagged`() {
    // Given
    val sessionTemplate1 = sessionTemplateEntityHelper.create(prisonCode = reservedPublicApplication.prison.code, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0))
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    // a visit for a different session and same visitor list was already rejected
    val rejectedVisit = visitEntityHelper.create(visitStatus = CANCELLED, visitSubStatus = VisitSubStatus.REJECTED, slotDate = visitDate, sessionTemplate = sessionTemplate1, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = visitor1Id, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = visitor2Id, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = visitor3Id, visitContact = false)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT_REJECTED)
    visitEntityHelper.save(rejectedVisit)

    // total allowed rejection limit is 1
    visitRequestRuleHelper.createAlreadyRejectedRequestRule(prisonCode, totalRejectedVisits = 1, rejectionIntervalInHours = 4)

    val prisonerId = reservedPublicApplication.prisonerId
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, "john", "smith", prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // When
    val responseResult = callGetSessions(prisonCode, prisonerId, userType = STAFF, visitorIds = listOf(visitor1Id, visitor2Id, visitor3Id), authHttpHeaders = roleVisitSchedulerHttpHeaders)

    // Then
    responseResult.expectStatus().isOk

    val sessions = getResults(responseResult.expectBody())
    // as there are already rejected visits for the same slot and date, the visit should be flagged
    val sessionsForSlotAndDate = sessions.filter { it.sessionTemplateReference == sessionTemplate1.reference && it.startTimestamp.toLocalDate() == visitDate }
    sessionsForSlotAndDate.forEach {
      assertThat(it.sessionPrisonRuleFailures.isNotEmpty()).isTrue
      assertThat(it.sessionPrisonRuleFailures.size).isEqualTo(1)
      assertThat(it.sessionPrisonRuleFailures[0]).isEqualTo(PrisonVisitRequestRuleType.ALREADY_REJECTED_VISIT)
    }

    val sessionsNotForVisitDate = sessions.filterNot { it.sessionTemplateReference == sessionTemplate1.reference && it.startTimestamp.toLocalDate() == visitDate }
    assertThat(sessionsNotForVisitDate.any { it.sessionPrisonRuleFailures.isNotEmpty() }).isFalse
  }

  @Test
  fun `when visit was already rejected for same time and same visitor list and after allowed rejection hours then a session is flagged`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    // 2 visits for the same session and same visitor list has already rejected twice and both rejections happened after allowed interval hours
    val rejectedVisit1 = visitEntityHelper.create(visitStatus = CANCELLED, visitSubStatus = VisitSubStatus.REJECTED, slotDate = visitDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    visitEntityHelper.createVisitor(visit = rejectedVisit1, nomisPersonId = visitor1Id, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit1, nomisPersonId = visitor2Id, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit1, nomisPersonId = visitor3Id, visitContact = false)
    eventAuditEntityHelper.create(visit = rejectedVisit1, type = EventAuditType.REQUESTED_VISIT)
    val rejectEventAudit = eventAuditEntityHelper.create(visit = rejectedVisit1, type = EventAuditType.REQUESTED_VISIT_REJECTED)
    visitEntityHelper.save(rejectedVisit1)

    // this visit was rejected 3 hrs and 55 minutes before
    eventAuditRepository.updateCreateTimeStamp(rejectEventAudit.id, rejectEventAudit.createTimestamp.minusHours(4).plusMinutes(5))

    val rejectedVisit2 = visitEntityHelper.create(visitStatus = CANCELLED, visitSubStatus = VisitSubStatus.REJECTED, slotDate = visitDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    visitEntityHelper.createVisitor(visit = rejectedVisit2, nomisPersonId = visitor1Id, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit2, nomisPersonId = visitor2Id, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit2, nomisPersonId = visitor3Id, visitContact = false)
    eventAuditEntityHelper.create(visit = rejectedVisit2, type = EventAuditType.REQUESTED_VISIT)
    eventAuditEntityHelper.create(visit = rejectedVisit2, type = EventAuditType.REQUESTED_VISIT_REJECTED)
    visitEntityHelper.save(rejectedVisit2)

    visitRequestRuleHelper.createAlreadyRejectedRequestRule(prisonCode, totalRejectedVisits = 2, rejectionIntervalInHours = 4)

    val prisonerId = reservedPublicApplication.prisonerId
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, "john", "smith", prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // When
    val responseResult = callGetSessions(prisonCode, prisonerId, userType = STAFF, visitorIds = listOf(visitor1Id, visitor2Id, visitor3Id), authHttpHeaders = roleVisitSchedulerHttpHeaders)

    // Then
    responseResult.expectStatus().isOk

    val sessions = getResults(responseResult.expectBody())

    // as there are already rejected visits for the same slot and date, the visit should be flagged
    val sessionsForSlotAndDate = sessions.filter { it.sessionTemplateReference == sessionTemplateDefault.reference && it.startTimestamp.toLocalDate() == visitDate }
    sessionsForSlotAndDate.forEach {
      assertThat(it.sessionPrisonRuleFailures.isNotEmpty()).isTrue
      assertThat(it.sessionPrisonRuleFailures.size).isEqualTo(1)
      assertThat(it.sessionPrisonRuleFailures[0]).isEqualTo(PrisonVisitRequestRuleType.ALREADY_REJECTED_VISIT)
    }

    val sessionsNotForVisitDate = sessions.filterNot { it.sessionTemplateReference == sessionTemplateDefault.reference && it.startTimestamp.toLocalDate() == visitDate }
    assertThat(sessionsNotForVisitDate.any { it.sessionPrisonRuleFailures.isNotEmpty() }).isFalse
  }

  @Test
  fun `when visit was already rejected for same time and same visitor list but before the rejection hours then a session is not flagged`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    // 2 visits for the same session and same visitor list has already rejected twice but 1 rejection was more than interval hours
    val rejectedVisit1 = visitEntityHelper.create(visitStatus = CANCELLED, visitSubStatus = VisitSubStatus.REJECTED, slotDate = visitDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    visitEntityHelper.createVisitor(visit = rejectedVisit1, nomisPersonId = visitor1Id, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit1, nomisPersonId = visitor2Id, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit1, nomisPersonId = visitor3Id, visitContact = false)
    eventAuditEntityHelper.create(visit = rejectedVisit1, type = EventAuditType.REQUESTED_VISIT)
    val rejectEventAudit = eventAuditEntityHelper.create(visit = rejectedVisit1, type = EventAuditType.REQUESTED_VISIT_REJECTED)
    visitEntityHelper.save(rejectedVisit1)

    // this visit was rejected 4 hours and 1 minute prior
    eventAuditRepository.updateCreateTimeStamp(rejectEventAudit.id, rejectEventAudit.createTimestamp.minusHours(4).minusMinutes(1))

    val rejectedVisit2 = visitEntityHelper.create(visitStatus = CANCELLED, visitSubStatus = VisitSubStatus.REJECTED, slotDate = visitDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    visitEntityHelper.createVisitor(visit = rejectedVisit2, nomisPersonId = visitor1Id, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit2, nomisPersonId = visitor2Id, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit2, nomisPersonId = visitor3Id, visitContact = false)
    eventAuditEntityHelper.create(visit = rejectedVisit2, type = EventAuditType.REQUESTED_VISIT)
    eventAuditEntityHelper.create(visit = rejectedVisit2, type = EventAuditType.REQUESTED_VISIT_REJECTED)
    visitEntityHelper.save(rejectedVisit2)

    // total allowed rejection limit is 2
    visitRequestRuleHelper.createAlreadyRejectedRequestRule(prisonCode, totalRejectedVisits = 2, rejectionIntervalInHours = 4)

    val prisonerId = reservedPublicApplication.prisonerId
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, "john", "smith", prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // When
    val responseResult = callGetSessions(prisonCode, prisonerId, userType = STAFF, visitorIds = listOf(visitor1Id, visitor2Id, visitor3Id), authHttpHeaders = roleVisitSchedulerHttpHeaders)

    // Then
    responseResult.expectStatus().isOk

    val sessions = getResults(responseResult.expectBody())
    // as 1 of the the rejected visit is before the rejection interval, the visit should not be flagged
    assertThat(sessions.any { it.sessionPrisonRuleFailures.isNotEmpty() }).isFalse
  }

  private fun getResults(returnResult: BodyContentSpec): Array<VisitSessionDto> = objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)
}
