package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.config.ExpiredApplicationTaskConfiguration
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ApplicationSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ChangeApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.CreateApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.builder.ApplicationDtoBuilder
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.CHANGING_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.RESERVED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents.VISIT_CHANGED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents.VISIT_SLOT_CHANGED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents.VISIT_SLOT_RELEASED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents.VISIT_SLOT_RESERVED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.ExpiredVisitAmendException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VSiPValidationException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VisitNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.ApplicationContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.ApplicationSupport
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.ApplicationVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitRestrictionStats
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import uk.gov.justice.digital.hmpps.visitscheduler.repository.ApplicationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.EventAuditRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDate
import java.time.LocalDateTime

@Service
@Transactional
class ApplicationService(
  private val applicationRepo: ApplicationRepository,
  private val visitRepo: VisitRepository,
  private val telemetryClientService: TelemetryClientService,
  private val sessionTemplateService: SessionTemplateService,
  private val eventAuditRepository: EventAuditRepository,
  private val prisonsService: PrisonsService,
  @Value("\${expired.applications.validity-minutes:10}") private val expiredPeriodMinutes: Int,
) {

  @Lazy
  @Autowired
  private lateinit var slotCapacityService: SlotCapacityService

  @Autowired
  private lateinit var expiredApplicationTaskConfiguration: ExpiredApplicationTaskConfiguration

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
    val visit = visitRepo.findBookedVisit(bookingReference) ?: throw VisitNotFoundException("Visit $bookingReference not found")
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

  private fun createApplication(createApplicationDto: CreateApplicationDto, existingVisit: Visit? = null): ApplicationDto {
    val sessionTemplate = getSessionTemplate(createApplicationDto.sessionTemplateReference)
    val prison = prisonsService.findPrisonByCode(sessionTemplate.prisonCode)

    validate(createApplicationDto, prison)

    val sessionSlot = sessionSlotService.getSessionSlot(createApplicationDto.sessionDate, sessionTemplate, prison)

    val isReservedSlot = existingVisit?.let {
      isReservationRequired(existingVisit, sessionSlot, createApplicationDto.applicationRestriction.getVisitRestriction())
    } ?: true

    if (isReservedSlot && !createApplicationDto.allowOverBooking) {
      slotCapacityService.checkCapacityForApplicationReservation(
        sessionSlot.reference,
        createApplicationDto.applicationRestriction.getVisitRestriction(),
        true,
      )
    }
    val applicationEntity = applicationRepo.saveAndFlush(
      Application(
        prisonerId = createApplicationDto.prisonerId,
        prison = prison,
        prisonId = prison.id,
        sessionSlot = sessionSlot,
        sessionSlotId = sessionSlot.id,
        reservedSlot = isReservedSlot,
        visitType = sessionTemplate.visitType,
        restriction = createApplicationDto.applicationRestriction.getVisitRestriction(),
        userType = createApplicationDto.userType,
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

    createApplicationDto.visitorSupport?.let {
      if (it.description.trim().isNotEmpty()) {
        applicationEntity.support = createApplicationSupport(applicationEntity, it.description)
      }
    }

    existingVisit?.let {
      // add even though it's not complete, because an existing booking is present
      existingVisit.addApplication(applicationEntity)
    }

    val applicationDto = applicationDtoBuilder.build(applicationEntity)

    val eventName = if (isReservedSlot) VISIT_SLOT_RESERVED_EVENT else VISIT_CHANGED_EVENT
    telemetryClientService.trackEvent(eventName, telemetryClientService.createApplicationVisitTrackEventFromVisitEntity(applicationDto, existingVisit))
    return applicationDto
  }

  private fun getSessionTemplate(sessionTemplateReference: String): SessionTemplateDto {
    return sessionTemplateService.getSessionTemplates(sessionTemplateReference)
  }

  fun changeIncompleteApplication(applicationReference: String, changeApplicationDto: ChangeApplicationDto): ApplicationDto {
    val sessionTemplate = getSessionTemplate(changeApplicationDto.sessionTemplateReference)
    val prison = prisonsService.findPrisonByCode(sessionTemplate.prisonCode)

    validate(changeApplicationDto, prison)

    val application = getApplicationEntity(applicationReference)

    val sessionSlot = sessionSlotService.getSessionSlot(changeApplicationDto.sessionDate, sessionTemplate, prison)

    val restriction = changeApplicationDto.applicationRestriction?.getVisitRestriction() ?: run { application.restriction }
    val isReservedSlot = isReservationRequired(application, sessionSlot, restriction)
    if (isReservedSlot && !changeApplicationDto.allowOverBooking) {
      slotCapacityService.checkCapacityForApplicationReservation(
        sessionSlot.reference,
        restriction,
        true,
      )
    }

    application.sessionSlotId = sessionSlot.id
    application.sessionSlot = sessionSlot

    changeApplicationDto.applicationRestriction?.let { restriction -> application.restriction = restriction.getVisitRestriction() }
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
      val deleteSupport = visitSupportUpdate.description.trim().isEmpty()
      if (deleteSupport) {
        application.support = null
      } else {
        application.support?.let {
          it.description = visitSupportUpdate.description
        } ?: run {
          application.support = createApplicationSupport(application, visitSupportUpdate.description)
        }
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

  fun isExpiredApplication(modifyTimestamp: LocalDateTime): Boolean {
    return modifyTimestamp.isBefore(getExpiredApplicationDateAndTime())
  }

  private fun isReservationRequired(
    visit: Visit,
    newSessionSlot: SessionSlot,
    newRestriction: VisitRestriction,
  ): Boolean {
    return isReservationRequired(visit.sessionSlot, visit.visitRestriction, newSessionSlot, newRestriction)
  }

  private fun isReservationRequired(
    application: Application,
    newSessionSlot: SessionSlot,
    newRestriction: VisitRestriction,
  ): Boolean {
    return isReservationRequired(application.sessionSlot, application.restriction, newSessionSlot, newRestriction)
  }

  private fun isReservationRequired(
    oldSessionSlot: SessionSlot,
    oldVisitRestriction: VisitRestriction,
    newSessionSlot: SessionSlot,
    newVisitRestriction: VisitRestriction,
  ): Boolean {
    return newVisitRestriction != oldVisitRestriction || newSessionSlot.id != oldSessionSlot.id
  }

  private fun getExpiredApplicationToDeleteDateAndTime(): LocalDateTime {
    return LocalDateTime.now().minusMinutes(expiredApplicationTaskConfiguration.deleteExpiredApplicationsAfterMinutes.toLong())
  }

  fun getExpiredApplicationDateAndTime(): LocalDateTime {
    return LocalDateTime.now().minusMinutes(expiredPeriodMinutes.toLong())
  }

  @Transactional(propagation = REQUIRES_NEW)
  fun deleteAllExpiredApplications() {
    LOG.debug("Entered deleteExpiredApplication")
    val applicationsToBeDeleted = applicationRepo.findApplicationByModifyTimes(getExpiredApplicationToDeleteDateAndTime())
    applicationsToBeDeleted.forEach { applicationToBeDeleted ->
      applicationRepo.delete(applicationToBeDeleted)
      val applicationEvent = telemetryClientService.createApplicationTrackEventFromVisitEntity(applicationToBeDeleted)
      telemetryClientService.trackEvent(VISIT_SLOT_RELEASED_EVENT, applicationEvent)
      LOG.debug("Expired Application ${applicationToBeDeleted.reference} has been deleted")
    }
  }

  fun isApplicationCompleted(reference: String): Boolean {
    return applicationRepo.isApplicationCompleted(reference)
  }

  fun getApplicationEntity(applicationReference: String): Application {
    return applicationRepo.findApplication(applicationReference)
      ?: throw VisitNotFoundException("Application (reference $applicationReference) not found")
  }

  fun completeApplication(applicationReference: String) {
    applicationRepo.completeApplication(applicationReference)
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
        bookingReference = visit?.reference,
        applicationReference = application.reference,
        sessionTemplateReference = application.sessionTemplateReference,
        type = if (application.reserved) RESERVED_VISIT else CHANGING_VISIT,
        applicationMethodType = applicationMethodType,
        text = null,
        userType = application.userType,
      ),
    )
  }

  private fun createApplicationContact(application: Application, name: String, telephone: String?): ApplicationContact {
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

  private fun createApplicationSupport(application: Application, description: String): ApplicationSupport {
    return ApplicationSupport(
      applicationId = application.id,
      application = application,
      description = description,
    )
  }

  private fun validateBookingChange(
    visit: Visit,
    createApplicationDto: CreateApplicationDto,
    bookingReference: String,
  ) {
    val errors = ArrayList<String>()

    if (visit.prisonerId != createApplicationDto.prisonerId) {
      errors.add("Given prisoner ${createApplicationDto.prisonerId} is different from the original booking ($bookingReference) prisoner ${visit.prisonerId} ")
    }

    val sessionTemplate = sessionTemplateService.getSessionTemplates(createApplicationDto.sessionTemplateReference)
    if (sessionTemplate.prisonCode != visit.prison.code) {
      errors.add("Given session ${createApplicationDto.sessionTemplateReference} has a different prison from the original booking ($bookingReference) prison ${sessionTemplate.prisonCode} != ${visit.prison.code} ")
    }

    if (errors.isNotEmpty()) {
      throw VSiPValidationException(errors.toTypedArray())
    }

    validateSessionSlotDateAndTime(visit, "changed")
  }

  private fun validateSessionSlotDateAndTime(
    visit: Visit,
    action: String,
    allowedVisitStartDate: LocalDateTime = LocalDateTime.now(),
  ) {
    val startSessionSlotDateAndTime = visit.sessionSlot.slotStart
    // check if the existing visit is in the past
    if (startSessionSlotDateAndTime.isBefore(allowedVisitStartDate)) {
      throw ExpiredVisitAmendException(
        AMEND_EXPIRED_ERROR_MESSAGE.format(visit.reference, action),
        ExpiredVisitAmendException("trying to change an expired visit"),
      )
    }
  }

  private fun validate(changeApplicationDto: ChangeApplicationDto, prison: Prison) {
    validate(changeApplicationDto.visitorSupport, changeApplicationDto.visitors, prison)
  }
  private fun validate(createApplicationDto: CreateApplicationDto, prison: Prison) {
    validate(createApplicationDto.visitorSupport, createApplicationDto.visitors, prison)
  }
  private fun validate(support: ApplicationSupportDto?, visitors: Set<VisitorDto>?, prison: Prison) {
    val validationErrors = mutableListOf<String>()

    support?.let {
      val value = support.description.trim()
      if (value.isNotBlank() && value.length < 3) {
        validationErrors.add("Support value description is too small")
      }
    }

    visitors?.let {
      if (prison.maxTotalVisitors < visitors.size) {
        validationErrors.add("This application has too many Visitors for this prison ${prison.code} max visitors ${prison.maxTotalVisitors}")
      }
    }

    if (validationErrors.isNotEmpty()) {
      throw VSiPValidationException(validationErrors.toTypedArray())
    }
  }

  fun hasActiveApplicationsForDate(nonAssociationPrisonerIds: List<String>, sessionSlotDate: LocalDate, prisonId: Long): Boolean {
    return applicationRepo.hasActiveApplicationsForDate(
      nonAssociationPrisonerIds,
      sessionSlotDate,
      prisonId,
      getExpiredApplicationDateAndTime(),
    )
  }

  fun hasReservations(prisonerId: String, sessionSlotId: Long, excludedApplicationReference: String?): Boolean {
    val expiredDateAndTime = getExpiredApplicationDateAndTime()

    return applicationRepo.hasReservations(
      prisonerId = prisonerId,
      sessionSlotId = sessionSlotId,
      expiredDateAndTime,
      excludedApplicationReference = excludedApplicationReference,
    )
  }

  fun getCountOfReservedSessionForOpenOrClosedRestriction(id: Long, excludedApplicationReference: String?): List<VisitRestrictionStats> {
    return applicationRepo.getCountOfReservedSessionForOpenOrClosedRestriction(
      id,
      getExpiredApplicationDateAndTime(),
      excludedApplicationReference = excludedApplicationReference,
    )
  }

  fun getReservedApplicationsCountForSlot(
    sessionSlotId: Long,
    restriction: VisitRestriction,
    excludedApplicationReference: String? = null,
  ): Long {
    return if (VisitRestriction.OPEN == restriction) {
      applicationRepo.getCountOfReservedApplicationsForOpenSessionSlot(sessionSlotId, getExpiredApplicationDateAndTime(), excludedApplicationReference = excludedApplicationReference)
    } else {
      applicationRepo.getCountOfReservedApplicationsForClosedSessionSlot(sessionSlotId, getExpiredApplicationDateAndTime(), excludedApplicationReference = excludedApplicationReference)
    }
  }
}
