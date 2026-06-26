package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.notifications

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_PRISONER_MERGE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerMergeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatPrisonerMerged
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.ActionedBy
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.ActionedByRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestApplicationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitRepository
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_NOTIFICATION_PRISONER_MERGE_PATH")
class PrisonerMergeNotificationControllerTest : NotificationTestBase() {
  @Autowired
  private lateinit var testVisitRepository: TestVisitRepository

  @Autowired
  private lateinit var testApplicationRepository: TestApplicationRepository

  @Autowired
  private lateinit var actionedByRepository: ActionedByRepository

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val oldPrisonerId = "A1234AA"
  val newPrisonerId = "BB456DD"
  val prisonCode = "ABC"
  val otherPrisonCode = "DEF"

  lateinit var prison1: Prison
  lateinit var prison2: Prison
  lateinit var sessionTemplate1: SessionTemplate
  lateinit var otherPrisonSessionTemplate: SessionTemplate

  @BeforeEach
  internal fun setUp() {
    prison1 = prisonEntityHelper.create(prisonCode = prisonCode)
    prison2 = prisonEntityHelper.create(prisonCode = otherPrisonCode)

    sessionTemplate1 = sessionTemplateEntityHelper.create(prison = prison1)
    otherPrisonSessionTemplate = sessionTemplateEntityHelper.create(prison = prison2)

    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when a prisoner is merged then prisonerId on all visits, applications and actioned by for old prisoner id are updated to new prisonerId`() {
    // Given
    val today = LocalDate.now()
    val actionedBy = ActionedBy(
      userType = UserType.PRISONER,
      bookerReference = null,
      userName = oldPrisonerId,
    )
    actionedByRepository.save(actionedBy)

    // today's BOOKED visit
    createVisitAndAssociatedApplication(prisonerId = oldPrisonerId, slotDate = today, sessionTemplate = sessionTemplate1, visitStatus = BOOKED)

    // future BOOKED visit
    createVisitAndAssociatedApplication(prisonerId = oldPrisonerId, slotDate = today.plusDays(2), visitStatus = BOOKED, sessionTemplate = sessionTemplate1)

    // future BOOKED visit in different prison
    createVisitAndAssociatedApplication(prisonerId = oldPrisonerId, slotDate = today.plusDays(4), visitStatus = BOOKED, sessionTemplate = otherPrisonSessionTemplate)

    // future CANCELLED visit
    createVisitAndAssociatedApplication(prisonerId = oldPrisonerId, slotDate = today.plusDays(2), visitStatus = CANCELLED, sessionTemplate = sessionTemplate1)

    // past BOOKED visit
    createVisitAndAssociatedApplication(prisonerId = oldPrisonerId, slotDate = today.minusDays(2), visitStatus = BOOKED, sessionTemplate = sessionTemplate1)

    // past CANCELLED visit
    createVisitAndAssociatedApplication(prisonerId = oldPrisonerId, slotDate = today.minusDays(3), visitStatus = CANCELLED, sessionTemplate = sessionTemplate1)

    // When
    val notificationDto = PrisonerMergeNotificationDto(oldPrisonerId = oldPrisonerId, newPrisonerId = newPrisonerId)
    val responseSpec = callNotifyVSiPThatPrisonerMerged(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    val visits = testVisitRepository.findAll()
    assertThat(visits).hasSize(6)
    assertThat(visits).noneMatch { it.prisonerId == oldPrisonerId }
    assertThat(visits).allMatch { it.prisonerId == newPrisonerId }

    val applications = testApplicationRepository.findAll()
    assertThat(applications).hasSize(6)
    assertThat(applications).noneMatch { it.prisonerId == oldPrisonerId }
    assertThat(applications).allMatch { it.prisonerId == newPrisonerId }

    val auditEvents = testEventAuditRepository.findAll()
    assertThat(auditEvents).hasSize(6)
    auditEvents.forEach {
      assertAuditEvent(
        eventAudit = it,
        expectedType = EventAuditType.PRISONER_MERGED,
        expectedApplicationMethodType = ApplicationMethodType.NOT_APPLICABLE,
        expectedText = "Prisoner merge event occurred - old prisoner number ${notificationDto.oldPrisonerId}, new prisoner number - ${notificationDto.newPrisonerId}",
      )
    }

    val actionedByValues = actionedByRepository.findAll()
    assertThat(actionedByValues).hasSize(2)
    assertThat(actionedByValues).noneMatch { it.userName == oldPrisonerId && it.userType == UserType.PRISONER }
    assertThat(actionedByValues).anyMatch { it.userName == newPrisonerId && it.userType == UserType.PRISONER }
  }

  @Test
  fun `when new actioned by already exists old prisonerID is not updated to new prisonerId`() {
    // Given
    var actionedBy = ActionedBy(
      userType = UserType.PRISONER,
      bookerReference = null,
      userName = oldPrisonerId,
    )
    actionedByRepository.save(actionedBy)

    actionedBy = ActionedBy(
      userType = UserType.PRISONER,
      bookerReference = null,
      userName = newPrisonerId,
    )
    actionedByRepository.save(actionedBy)

    // When
    val notificationDto = PrisonerMergeNotificationDto(oldPrisonerId = oldPrisonerId, newPrisonerId = newPrisonerId)
    val responseSpec = callNotifyVSiPThatPrisonerMerged(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk

    val actionedByValues = actionedByRepository.findAll()
    assertThat(actionedByValues).hasSize(3)
    assertThat(actionedByValues).anyMatch { it.userName == oldPrisonerId && it.userType == UserType.PRISONER }
    assertThat(actionedByValues).anyMatch { it.userName == newPrisonerId && it.userType == UserType.PRISONER }
    assertThat(actionedByValues).anyMatch { it.userName == null && it.userType == UserType.SYSTEM }
  }

  private fun createVisitAndAssociatedApplication(prisonerId: String, slotDate: LocalDate, sessionTemplate: SessionTemplate, visitStatus: VisitStatus): Visit {
    val visitorId = 1L

    val visit = createApplicationAndVisit(
      prisonerId = prisonerId,
      slotDate = slotDate,
      visitStatus = visitStatus,
      sessionTemplate = sessionTemplate,
      visitSubStatus = if (visitStatus == BOOKED) VisitSubStatus.AUTO_APPROVED else VisitSubStatus.CANCELLED,
    )

    visit.visitors.add(
      VisitVisitor(
        nomisPersonId = visitorId,
        visitId = visit.id,
        visit = visit,
        visitContact = true,
      ),
    )

    visitEntityHelper.save(visit)

    return visit
  }

  private fun assertAuditEvent(
    eventAudit: EventAudit,
    expectedType: EventAuditType,
    expectedApplicationMethodType: ApplicationMethodType,
    expectedText: String,
  ) {
    assertThat(eventAudit.type).isEqualTo(expectedType)
    assertThat(eventAudit.applicationMethodType).isEqualTo(expectedApplicationMethodType)
    assertThat(eventAudit.text).isEqualTo(expectedText)
  }
}
