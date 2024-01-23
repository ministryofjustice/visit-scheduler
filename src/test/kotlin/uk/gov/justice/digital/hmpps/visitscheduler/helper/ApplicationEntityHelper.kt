package uk.gov.justice.digital.hmpps.visitscheduler.helper

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.ApplicationContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.ApplicationSupport
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.ApplicationVisitor
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

  fun create(
    prisonerId: String = "FF0000AA",
    prisonCode: String = "MDI",
    slotDate: LocalDate = LocalDate.of((LocalDate.now().year + 1), 11, 1),
    visitStart: LocalTime = LocalTime.now(),
    visitEnd: LocalTime = visitStart.plusHours(1),
    visitType: VisitType = SOCIAL,
    visitRestriction: VisitRestriction = OPEN,
    activePrison: Boolean = true,
    sessionTemplateReference: String? = "sessionTemplateReference",
  ): Application {
    val prison = prisonEntityHelper.create(prisonCode, activePrison)
    val sessionSlot = sessionSlotEntityHelper.create(sessionTemplateReference, prison.id, slotDate, visitStart, visitEnd)

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
      ),
    )
  }

  fun createContact(
    application: Application,
    name: String,
    phone: String,
  ) {
    application.visitContact = ApplicationContact(
      applicationId = application.id,
      name = name,
      telephone = phone,
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
    name: String,
    details: String?,
  ) {
    application.support.add(
      ApplicationSupport(
        type = name,
        applicationId = application.id,
        text = details,
        application = application,
      ),
    )
  }

  fun save(application: Application): Application {
    return applicationRepo.saveAndFlush(application)
  }

  fun getApplication(applicationReference: String): Application? {
    return applicationRepo.findApplication(applicationReference)
  }
}
