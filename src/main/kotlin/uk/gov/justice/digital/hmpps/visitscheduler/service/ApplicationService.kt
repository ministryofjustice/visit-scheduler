package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ChangeApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.builder.ApplicationDtoBuilder
import uk.gov.justice.digital.hmpps.visitscheduler.exception.ExpiredVisitAmendException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.SupportNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VSiPValidationException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VisitNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType.CHANGING_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType.RESERVED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.ApplicationContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.ApplicationSupport
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.ApplicationVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import uk.gov.justice.digital.hmpps.visitscheduler.repository.ApplicationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.EventAuditRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SupportTypeRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents.VISIT_CHANGED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents.VISIT_SLOT_CHANGED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents.VISIT_SLOT_RELEASED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents.VISIT_SLOT_RESERVED_EVENT
import java.time.LocalDateTime

@Service
@Transactional
class ApplicationService(
  private val applicationRepo: ApplicationRepository,
  private val visitRepo: VisitRepository,
  private val supportTypeRepository: SupportTypeRepository,
  private val telemetryClientService: TelemetryClientService,
  private val sessionTemplateService: SessionTemplateService,
  private val eventAuditRepository: EventAuditRepository,
  private val prisonsService: PrisonsService,
  @Value("\${task.expired-visit.validity-minutes:10}") private val expiredPeriodMinutes: Int,
) {

  @Autowired
  private lateinit var applicationDtoBuilder: ApplicationDtoBuilder

  @Autowired
  private lateinit var sessionSlotService: SessionSlotService

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    const val AMEND_EXPIRED_ERROR_MESSAGE = "Visit with reference - %s is in the past, it cannot be %s"
  }

  fun createInitialApplication(createApplicationDto: CreateApplicationDto): ApplicationDto {
    val applicationDto = createApplication(createApplicationDto)

    saveEventAudit(
      createApplicationDto.actionedBy,
      applicationDto,
      applicationMethodType = NOT_KNOWN,
    )

    return applicationDto
  }

  fun createApplicationForAnExistingVisit(bookingReference: String, createApplicationDto: CreateApplicationDto): ApplicationDto {
    val visit = visitRepo.findBookedVisit(bookingReference)
    validateBookingChange(visit, createApplicationDto, bookingReference)

    val applicationDto = createApplication(createApplicationDto, visit)

    saveEventAudit(
      createApplicationDto.actionedBy,
      applicationDto,
      visit = visit,
      NOT_KNOWN,
    )

    return applicationDto
  }

  private fun createApplication(createApplicationDto: CreateApplicationDto, visit: Visit? = null): ApplicationDto {
    val sessionTemplateReference = createApplicationDto.sessionTemplateReference
    val sessionTemplate = sessionTemplateService.getSessionTemplates(sessionTemplateReference)
    val prison = prisonsService.findPrisonByCode(sessionTemplate.prisonCode)

    val sessionSlot = sessionSlotService.getSessionSlot(createApplicationDto.sessionDate, sessionTemplate, prison)
    val isReservedSlot = visit?.let {
      isReservationRequired(visit, sessionSlot, createApplicationDto.visitRestriction)
    } ?: true

    val applicationEntity = applicationRepo.saveAndFlush(
      Application(
        prisonerId = createApplicationDto.prisonerId,
        prison = prison,
        prisonId = prison.id,
        sessionSlot = sessionSlot,
        sessionSlotId = sessionSlot.id,
        reservedSlot = isReservedSlot,
        visitType = sessionTemplate.visitType,
        restriction = createApplicationDto.visitRestriction,
        completed = false,
        createdBy = createApplicationDto.actionedBy,
      ),
    )

    createApplicationDto.visitContact?.let {
      applicationEntity.visitContact = createApplicationContact(applicationEntity, it.name, it.telephone)
    }

    createApplicationDto.visitors.forEach {
      applicationEntity.visitors.add(createApplicationVisitor(applicationEntity, it.nomisPersonId, it.visitContact))
    }

    createApplicationDto.visitorSupport?.let { supportList ->
      supportList.forEach {
        if (!supportTypeRepository.existsByName(it.type)) {
          throw SupportNotFoundException("Invalid support ${it.type} not found")
        }
        applicationEntity.support.add(createApplicationSupport(applicationEntity, it.type, it.text))
      }
    }

    visit?.let {
      // add even though it's not complete
      visit.applications.add(applicationEntity)
    }

    val applicationDto = applicationDtoBuilder.build(applicationEntity)

    val eventName = if (isReservedSlot) VISIT_SLOT_RESERVED_EVENT else VISIT_CHANGED_EVENT
    telemetryClientService.trackEvent(eventName, telemetryClientService.createApplicationVisitTrackEventFromVisitEntity(applicationDto, visit))
    return applicationDto
  }

  fun changeIncompleteApplication(applicationReference: String, changeApplicationDto: ChangeApplicationDto): ApplicationDto {
    val application = getApplicationEntity(applicationReference)

    changeApplicationDto.sessionDate?.let {
      val sessionTemplateReference = changeApplicationDto.sessionTemplateReference
      val sessionTemplate = sessionTemplateService.getSessionTemplates(sessionTemplateReference)
      val prison = prisonsService.findPrisonByCode(sessionTemplate.prisonCode)

      val sessionSlot = sessionSlotService.getSessionSlot(changeApplicationDto.sessionDate, sessionTemplate, prison)
      application.sessionSlotId = sessionSlot.id
      application.sessionSlot = sessionSlot
    }

    changeApplicationDto.visitRestriction?.let { restriction -> application.restriction = restriction }
    changeApplicationDto.visitContact?.let { visitContactUpdate ->
      application.visitContact?.let { visitContact ->
        visitContact.name = visitContactUpdate.name
        visitContact.telephone = visitContactUpdate.telephone
      } ?: run {
        application.visitContact = createApplicationContact(application, visitContactUpdate.name, visitContactUpdate.telephone)
      }
    }

    changeApplicationDto.visitors?.let { visitorsUpdate ->
      application.visitors.clear()
      applicationRepo.saveAndFlush(application)
      visitorsUpdate.distinctBy { it.nomisPersonId }.forEach {
        application.visitors.add(createApplicationVisitor(application, it.nomisPersonId, it.visitContact))
      }
    }

    changeApplicationDto.visitorSupport?.let { visitSupportUpdate ->
      application.support.clear()
      applicationRepo.saveAndFlush(application)
      visitSupportUpdate.forEach {
        if (!supportTypeRepository.existsByName(it.type)) {
          throw SupportNotFoundException("Invalid support ${it.type} not found")
        }
        application.support.add(createApplicationSupport(application, it.type, it.text))
      }
    }

    val visit = this.visitRepo.findVisitByApplicationReference(applicationReference)
    visit?.let {
      application.reservedSlot = isReservationRequired(visit, application.sessionSlot, application.restriction)
    }

    val telemetryData = HashMap<String, String>()
    telemetryData["applicationReference"] = application.reference
    visit?.let {
      telemetryData["bookingReference"] = it.reference
    }
    telemetryData["reservedSlot"] = application.reservedSlot.toString()

    telemetryClientService.trackEvent(
      VISIT_SLOT_CHANGED_EVENT,
      telemetryData,
    )

    return applicationDtoBuilder.build(application)
  }

  private fun isReservationRequired(
    visit: Visit,
    newSessionSlot: SessionSlot,
    newRestriction: VisitRestriction,
  ): Boolean {
    return visit == null ||
      (
        visit.visitRestriction != newRestriction ||
          visit.sessionSlotId != newSessionSlot.id
        )
  }

  @Transactional(readOnly = true)
  fun findExpiredApplicationReferences(): List<String> {
    LOG.debug("Entered findExpiredApplicationReferences : ${getExpiredApplicationDateAndTime()}")
    return applicationRepo.findExpiredApplicationReferences(expiredPeriodMinutes)
  }

  fun getExpiredApplicationDateAndTime(): LocalDateTime {
    return LocalDateTime.now().minusMinutes(expiredPeriodMinutes.toLong())
  }

  fun deleteAllExpiredApplications(applicationReferences: List<String>) {
    applicationReferences.forEach {
      val applicationToBeDeleted = getApplicationEntity(it)
      val deleted = applicationRepo.deleteExpiredApplications(it, expiredPeriodMinutes)
      if (deleted > 0) {
        val bookEvent = telemetryClientService.createApplicationTrackEventFromVisitEntity(applicationToBeDeleted)
        telemetryClientService.trackEvent(VISIT_SLOT_RELEASED_EVENT, bookEvent)
      }
    }
  }

  fun isApplicationCompleted(reference: String): Boolean {
    return applicationRepo.isApplicationCompleted(reference)
  }

  fun getApplication(reference: String): ApplicationDto {
    return applicationDtoBuilder.build(getApplicationEntity(reference))
  }

  fun getApplicationEntity(applicationReference: String): Application {
    return applicationRepo.findApplication(applicationReference)
      ?: throw VisitNotFoundException("Application (reference $applicationReference) not found")
  }

  private fun saveEventAudit(
    actionedBy: String,
    application: ApplicationDto,
    visit: Visit? = null,
    applicationMethodType: ApplicationMethodType,
  ) {
    eventAuditRepository.saveAndFlush(
      EventAudit(
        actionedBy = actionedBy,
        bookingReference = visit?.let { visit.reference } ?: "",
        applicationReference = application.reference,
        sessionTemplateReference = application.sessionTemplateReference,
        type = if (application.reserved) RESERVED_VISIT else CHANGING_VISIT,
        applicationMethodType = applicationMethodType,
      ),
    )
  }

  private fun createApplicationContact(application: Application, name: String, telephone: String): ApplicationContact {
    return ApplicationContact(
      applicationId = application.id,
      application = application,
      name = name,
      telephone = telephone,
    )
  }

  private fun createApplicationVisitor(application: Application, personId: Long, contact: Boolean?): ApplicationVisitor {
    return ApplicationVisitor(
      applicationId = application.id,
      application = application,
      nomisPersonId = personId,
      contact = contact,
    )
  }

  private fun createApplicationSupport(application: Application, type: String, text: String?): ApplicationSupport {
    return ApplicationSupport(
      applicationId = application.id,
      application = application,
      type = type,
      text = text,
    )
  }

  private fun validateBookingChange(
    visit: Visit?,
    createApplicationDto: CreateApplicationDto,
    bookingReference: String,
  ) {
    val errors = ArrayList<String>()
    visit?.let {
      if (visit.prisonerId != createApplicationDto.prisonerId) {
        errors.add("Given prisoner ${createApplicationDto.prisonerId} is different from the original booking ($bookingReference) prisoner ${visit.prisonerId} ")
      }

      val sessionTemplate = sessionTemplateService.getSessionTemplates(createApplicationDto.sessionTemplateReference)
      if (sessionTemplate.prisonCode != visit.prison.code) {
        errors.add("Given session ${createApplicationDto.sessionTemplateReference} has a different prison from the original booking ($bookingReference) prison ${sessionTemplate.prisonCode} != ${visit.prison.code} ")
      }
    } ?: {
      if (visit == null) errors.add("Visit booking reference $bookingReference not found")
    }

    if (errors.isNotEmpty()) {
      throw VSiPValidationException(errors.toTypedArray())
    }

    validateSessionSlotDateAndTime(visit!!, "changed")
  }

  private fun validateSessionSlotDateAndTime(
    visit: Visit,
    action: String,
    allowedVisitStartDate: LocalDateTime = LocalDateTime.now(),
  ) {
    val startSessionSlotDateAndTime = sessionSlotService.getSessionTimeAndDate(visit.sessionSlot.slotDate, visit.sessionSlot.slotTime)
    // check if the existing visit is in the past
    if (startSessionSlotDateAndTime.isBefore(allowedVisitStartDate)) {
      throw ExpiredVisitAmendException(
        AMEND_EXPIRED_ERROR_MESSAGE.format(visit.reference, action),
        ExpiredVisitAmendException("trying to change an expired visit"),
      )
    }
  }
}
