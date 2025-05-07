package uk.gov.justice.digital.hmpps.visitscheduler.helper

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus.ACCEPTED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus.IN_PROGRESS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.ApplicationContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.ApplicationSupport
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.ApplicationVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.ApplicationRepository
import java.time.LocalDate
import java.time.LocalTime

@Component
@Transactional
class ApplicationEntityHelper(
  private val applicationRepo: ApplicationRepository,
  private val prisonEntityHelper: PrisonEntityHelper,
  private val sessionSlotEntityHelper: SessionSlotEntityHelper,
) {

  companion object {

    fun createApplication(visit: Visit): Application = Application(
      prisonerId = visit.prisonerId,
      prisonId = visit.prisonId,
      prison = visit.prison,
      sessionSlotId = visit.sessionSlot.id,
      sessionSlot = visit.sessionSlot,
      visitType = visit.visitType,
      restriction = visit.visitRestriction,
      createdBy = "",
      reservedSlot = true,
      completed = true,
      userType = STAFF,
      applicationStatus = ACCEPTED,
    )

    fun createUpdateVisitApplication(visit: Visit, newSessionSlot: SessionSlot): Application {
      val application = Application(
        prisonerId = visit.prisonerId,
        prisonId = visit.prisonId,
        prison = visit.prison,
        sessionSlotId = newSessionSlot.id,
        sessionSlot = newSessionSlot,
        visitType = visit.visitType,
        restriction = visit.visitRestriction,
        createdBy = "",
        reservedSlot = true,
        completed = false,
        userType = STAFF,
        applicationStatus = IN_PROGRESS,
      )
      return application
    }
  }

  fun create(visit: Visit): Application = applicationRepo.saveAndFlush(
    createApplication(visit),
  )

  fun create(visit: Visit, sessionTemplate: SessionTemplate, newSlotDate: LocalDate): Application {
    val sessionSlot = sessionSlotEntityHelper.create(sessionTemplate.reference, visit.prison.id, newSlotDate, sessionTemplate.startTime, sessionTemplate.endTime)

    return applicationRepo.saveAndFlush(
      createUpdateVisitApplication(visit, sessionSlot),
    )
  }

  fun create(
    prisonerId: String = "testPrisonerId",
    slotDate: LocalDate? = null,
    sessionTemplate: SessionTemplate,
    visitStart: LocalTime = sessionTemplate.startTime,
    visitEnd: LocalTime = sessionTemplate.endTime,
    visitRestriction: VisitRestriction = OPEN,
    activePrison: Boolean = true,
    prisonCode: String? = sessionTemplate.prison.code,
    visitType: VisitType = sessionTemplate.visitType,
    reservedSlot: Boolean = true,
    completed: Boolean = true,
    userType: UserType = STAFF,
    createdBy: String = "",
    applicationStatus: ApplicationStatus,
  ): Application {
    val slotDateLocal = slotDate ?: run {
      sessionTemplate.validFromDate.with(sessionTemplate.dayOfWeek).plusWeeks(1)
    }

    val prison = prisonEntityHelper.create(prisonCode ?: "MDI", activePrison)
    val sessionSlot = sessionSlotEntityHelper.create(sessionTemplate.reference, prison.id, slotDateLocal, visitStart, visitEnd)

    return applicationRepo.saveAndFlush(
      Application(
        prisonerId = prisonerId,
        prisonId = prison.id,
        prison = prison,
        sessionSlotId = sessionSlot.id,
        sessionSlot = sessionSlot,
        visitType = visitType,
        restriction = visitRestriction,
        createdBy = createdBy,
        reservedSlot = reservedSlot,
        completed = completed,
        userType = userType,
        applicationStatus = applicationStatus,
      ),
    )
  }

  fun create(
    prisonerId: String = "testPrisonerId",
    slotDate: LocalDate = LocalDate.of((LocalDate.now().year + 1), 11, 1),
    visitStart: LocalTime = LocalTime.of(10, 0),
    visitEnd: LocalTime = LocalTime.of(11, 0),
    visitRestriction: VisitRestriction = OPEN,
    activePrison: Boolean = true,
    prisonCode: String?,
    visitType: VisitType = VisitType.SOCIAL,
    reservedSlot: Boolean = true,
    completed: Boolean = true,
    applicationStatus: ApplicationStatus = ACCEPTED,
  ): Application {
    val prison = prisonEntityHelper.create(prisonCode ?: "MDI", activePrison)
    val sessionSlot = sessionSlotEntityHelper.create(prison.id, slotDate, visitStart, visitEnd)

    return applicationRepo.saveAndFlush(
      Application(
        prisonerId = prisonerId,
        prisonId = prison.id,
        prison = prison,
        sessionSlotId = sessionSlot.id,
        sessionSlot = sessionSlot,
        visitType = visitType,
        restriction = visitRestriction,
        createdBy = "",
        reservedSlot = reservedSlot,
        completed = completed,
        userType = STAFF,
        applicationStatus = applicationStatus,
      ),
    )
  }

  fun createContact(
    application: Application,
    name: String,
    phone: String?,
    email: String?,
  ) {
    application.visitContact = ApplicationContact(
      applicationId = application.id,
      name = name,
      telephone = phone,
      email = email,
      application = application,
    )
  }

  fun createContact(
    application: Application,
    contact: ContactDto,
  ) {
    application.visitContact = ApplicationContact(
      applicationId = application.id,
      name = contact.name,
      telephone = contact.telephone,
      email = contact.email,
      application = application,
    )
  }

  fun createVisitor(
    application: Application,
    nomisPersonId: Long,
    visitContact: Boolean?,
  ) {
    application.visitors.add(
      ApplicationVisitor(
        nomisPersonId = nomisPersonId,
        applicationId = application.id,
        application = application,
        contact = visitContact,
      ),
    )
  }

  fun createSupport(
    application: Application,
    description: String,
  ) {
    application.support =
      ApplicationSupport(
        applicationId = application.id,
        description = description,
        application = application,
      )
  }

  fun save(application: Application): Application = applicationRepo.saveAndFlush(application)
}
