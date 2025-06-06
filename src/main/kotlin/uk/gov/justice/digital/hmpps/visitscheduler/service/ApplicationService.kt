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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateLegacyContactOnVisitRequestDto.Companion.UNKNOWN_TOKEN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ApplicationSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ChangeApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.CreateApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.builder.ApplicationDtoBuilder
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus.IN_PROGRESS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.ExpiredVisitAmendException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VSiPValidationException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VisitNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.ApplicationContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.ApplicationSupport
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.ApplicationVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitRestrictionStats
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import uk.gov.justice.digital.hmpps.visitscheduler.repository.ApplicationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.utils.CapitaliseUtil
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.collections.ArrayList

@Service
@Transactional
class ApplicationService(
  private val applicationRepository: ApplicationRepository,
  private val visitRepo: VisitRepository,
  private val telemetryClientService: TelemetryClientService,
  private val sessionTemplateService: SessionTemplateService,
  private val prisonsService: PrisonsService,
  @Value("\${expired.applications.validity-minutes:10}") private val expiredPeriodMinutes: Int,
) {

  @Lazy
  @Autowired
  private lateinit var visitEventAuditService: VisitEventAuditService

  @Lazy
  @Autowired
  private lateinit var slotCapacityService: SlotCapacityService

  @Autowired
  private lateinit var expiredApplicationTaskConfiguration: ExpiredApplicationTaskConfiguration

  @Autowired
  private lateinit var applicationDtoBuilder: ApplicationDtoBuilder

  @Autowired
  private lateinit var sessionSlotService: SessionSlotService

  @Autowired
  private lateinit var capitaliseUtil: CapitaliseUtil

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    const val AMEND_EXPIRED_ERROR_MESSAGE = "Visit with reference - %s is in the past, it cannot be %s"
  }

  fun createInitialApplication(createApplicationDto: CreateApplicationDto): ApplicationDto {
    val application = createApplication(createApplicationDto)
    return processInitialApplicationEvents(application, createApplicationDto)
  }

  fun createApplicationForAnExistingVisit(
    bookingReference: String,
    createApplicationDto: CreateApplicationDto,
  ): ApplicationDto {
    val existingVisit =
      visitRepo.findBookedVisit(bookingReference) ?: throw VisitNotFoundException("Visit $bookingReference not found")
    validateBookingChange(existingVisit, createApplicationDto, bookingReference)

    val application = createApplication(createApplicationDto, existingVisit)

    return processApplicationEventsForExistingVisit(application, createApplicationDto, existingVisit)
  }

  fun changeIncompleteApplication(
    applicationReference: String,
    changeApplicationDto: ChangeApplicationDto,
  ): ApplicationDto {
    val sessionTemplate = getSessionTemplate(changeApplicationDto.sessionTemplateReference)
    val prison = prisonsService.findPrisonByCode(sessionTemplate.prisonCode)

    validate(changeApplicationDto, prison)

    val application = getApplicationEntity(applicationReference)

    val sessionSlot = sessionSlotService.getSessionSlot(changeApplicationDto.sessionDate, sessionTemplate, prison)

    val visitRestriction =
      changeApplicationDto.applicationRestriction?.getVisitRestriction() ?: run { application.restriction }
    val isReservedSlot = isReservationRequired(application, sessionSlot, visitRestriction)
    if (isReservedSlot && !changeApplicationDto.allowOverBooking) {
      slotCapacityService.checkCapacityForApplicationReservation(
        sessionSlot.reference,
        visitRestriction,
        true,
      )
    }

    application.sessionSlotId = sessionSlot.id
    application.sessionSlot = sessionSlot

    changeApplicationDto.applicationRestriction?.let { restriction ->
      application.restriction = restriction.getVisitRestriction()
    }
    changeApplicationDto.visitContact?.let { visitContactUpdate ->
      application.visitContact?.let { visitContact ->
        visitContact.name = visitContactUpdate.name
        visitContact.telephone = visitContactUpdate.telephone
        visitContact.email = visitContactUpdate.email
      } ?: run {
        application.visitContact =
          createApplicationContact(application, visitContactUpdate.name, visitContactUpdate.telephone, visitContactUpdate.email)
      }
    }

    changeApplicationDto.visitors?.let { visitorsUpdate ->
      application.visitors.clear()
      applicationRepository.saveAndFlush(application)
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

    application.visit?.let {
      application.reservedSlot = isReservationRequired(it, application.sessionSlot, application.restriction)
    }

    return processChangeIncompleteApplicationEvents(application)
  }

  private fun createApplication(
    createApplicationDto: CreateApplicationDto,
    existingVisit: Visit? = null,
  ): Application {
    val sessionTemplate = getSessionTemplate(createApplicationDto.sessionTemplateReference)
    val prison = prisonsService.findPrisonByCode(sessionTemplate.prisonCode)

    validate(createApplicationDto, prison)

    val sessionSlot = sessionSlotService.getSessionSlot(createApplicationDto.sessionDate, sessionTemplate, prison)

    val isReservedSlot = existingVisit?.let {
      isReservationRequired(
        existingVisit,
        sessionSlot,
        createApplicationDto.applicationRestriction.getVisitRestriction(),
      )
    } ?: true

    if (isReservedSlot && !createApplicationDto.allowOverBooking) {
      slotCapacityService.checkCapacityForApplicationReservation(
        sessionSlot.reference,
        createApplicationDto.applicationRestriction.getVisitRestriction(),
        true,
      )
    }
    val applicationEntity = applicationRepository.saveAndFlush(
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
        createdBy = createApplicationDto.actionedBy,
        applicationStatus = IN_PROGRESS,
      ),
    )

    createApplicationDto.visitContact?.let {
      applicationEntity.visitContact = createApplicationContact(applicationEntity, it.name, it.telephone, it.email)
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

    return applicationEntity
  }

  @Transactional(propagation = REQUIRES_NEW)
  fun deleteAllExpiredApplications() {
    LOG.debug("Entered deleteExpiredApplication")
    val applicationsToBeDeleted =
      applicationRepository.findApplicationByModifyTimes(getExpiredApplicationToDeleteDateAndTime())
    applicationsToBeDeleted.forEach { applicationToBeDeleted ->
      applicationRepository.delete(applicationToBeDeleted)
      processDeleteApplicationEvents(applicationToBeDeleted)
      LOG.debug("Expired Application ${applicationToBeDeleted.reference} has been deleted")
    }
  }

  private fun getSessionTemplate(sessionTemplateReference: String): SessionTemplateDto = sessionTemplateService.getSessionTemplates(sessionTemplateReference)

  fun isExpiredApplication(modifyTimestamp: LocalDateTime): Boolean = modifyTimestamp.isBefore(getExpiredApplicationDateAndTime())

  private fun processInitialApplicationEvents(
    application: Application,
    createApplicationDto: CreateApplicationDto,
  ): ApplicationDto {
    val applicationDto = applicationDtoBuilder.build(application)
    val eventAudit = visitEventAuditService.saveApplicationEventAudit(
      createApplicationDto.actionedBy,
      applicationDto,
      applicationMethodType = NOT_KNOWN,
    )

    telemetryClientService.trackEventApplicationReserved(
      applicationDto,
      eventAudit,
    )
    return applicationDto
  }

  private fun processApplicationEventsForExistingVisit(
    application: Application,
    createApplicationDto: CreateApplicationDto,
    existingVisit: Visit,
  ): ApplicationDto {
    val applicationDto = applicationDtoBuilder.build(application)

    val eventAudit = visitEventAuditService.saveApplicationEventAudit(
      createApplicationDto.actionedBy,
      applicationDto,
      visit = existingVisit,
      NOT_KNOWN,
    )

    telemetryClientService.trackEventVisitChanged(
      applicationDto,
      existingVisit.reference,
      eventAudit,
    )

    return applicationDto
  }

  private fun processChangeIncompleteApplicationEvents(
    application: Application,
  ): ApplicationDto {
    val applicationDto = applicationDtoBuilder.build(application)
    telemetryClientService.trackEventApplicationSlotChanged(applicationDto, application.visit?.reference)
    return applicationDto
  }

  private fun processDeleteApplicationEvents(
    application: Application,
  ) {
    val dto = applicationDtoBuilder.build(application)
    telemetryClientService.trackEventApplicationDeleted(dto)
  }

  private fun isReservationRequired(
    visit: Visit,
    newSessionSlot: SessionSlot,
    newRestriction: VisitRestriction,
  ): Boolean = isReservationRequired(visit.sessionSlot, visit.visitRestriction, newSessionSlot, newRestriction)

  private fun isReservationRequired(
    application: Application,
    newSessionSlot: SessionSlot,
    newRestriction: VisitRestriction,
  ): Boolean = isReservationRequired(application.sessionSlot, application.restriction, newSessionSlot, newRestriction)

  private fun isReservationRequired(
    oldSessionSlot: SessionSlot,
    oldVisitRestriction: VisitRestriction,
    newSessionSlot: SessionSlot,
    newVisitRestriction: VisitRestriction,
  ): Boolean = newVisitRestriction != oldVisitRestriction || newSessionSlot.id != oldSessionSlot.id

  private fun getExpiredApplicationToDeleteDateAndTime(): LocalDateTime = LocalDateTime.now()
    .minusMinutes(expiredApplicationTaskConfiguration.deleteExpiredApplicationsAfterMinutes.toLong())

  fun getExpiredApplicationDateAndTime(): LocalDateTime = LocalDateTime.now().minusMinutes(expiredPeriodMinutes.toLong())

  fun isApplicationCompleted(reference: String): Boolean = applicationRepository.isApplicationCompleted(reference)

  fun getApplicationEntity(applicationReference: String): Application = applicationRepository.findApplication(applicationReference)
    ?: throw VisitNotFoundException("Application (reference $applicationReference) not found")

  fun completeApplication(applicationReference: String) {
    applicationRepository.completeApplication(applicationReference)
  }

  private fun createApplicationContact(application: Application, name: String, telephone: String?, email: String?): ApplicationContact = ApplicationContact(
    applicationId = application.id,
    application = application,
    name = name,
    telephone = telephone,
    email = email,
  )

  private fun createApplicationVisitor(application: Application, personId: Long, contact: Boolean?): ApplicationVisitor = ApplicationVisitor(
    applicationId = application.id,
    application = application,
    nomisPersonId = personId,
    contact = contact,
  )

  private fun createApplicationSupport(application: Application, description: String): ApplicationSupport = ApplicationSupport(
    applicationId = application.id,
    application = application,
    description = description,
  )

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

  fun hasActiveApplicationsForDate(nonAssociationPrisonerIds: List<String>, sessionSlotDate: LocalDate, prisonId: Long): Boolean = applicationRepository.hasActiveApplicationsForDate(
    nonAssociationPrisonerIds,
    sessionSlotDate,
    prisonId,
    getExpiredApplicationDateAndTime(),
  )

  fun hasReservations(prisonerId: String, sessionSlotId: Long, excludedApplicationReference: String?, usernameToExcludeFromReservedApplications: String?): Boolean {
    val expiredDateAndTime = getExpiredApplicationDateAndTime()

    return if (usernameToExcludeFromReservedApplications != null) {
      applicationRepository.hasReservations(
        prisonerId = prisonerId,
        sessionSlotId = sessionSlotId,
        expiredDateAndTime,
        excludedApplicationReference = excludedApplicationReference,
        usernameToExcludeFromReservedApplications = usernameToExcludeFromReservedApplications,
      )
    } else {
      applicationRepository.hasReservations(
        prisonerId = prisonerId,
        sessionSlotId = sessionSlotId,
        expiredDateAndTime,
        excludedApplicationReference = excludedApplicationReference,
      )
    }
  }

  fun getCountOfReservedSessionForOpenOrClosedRestriction(id: Long, excludedApplicationReference: String?, usernameToExcludeFromReservedApplications: String?): List<VisitRestrictionStats> = if (usernameToExcludeFromReservedApplications == null) {
    applicationRepository.getCountOfReservedSessionForOpenOrClosedRestriction(
      id,
      getExpiredApplicationDateAndTime(),
      excludedApplicationReference,
    )
  } else {
    applicationRepository.getCountOfReservedSessionForOpenOrClosedRestrictionExcludingUser(
      id,
      getExpiredApplicationDateAndTime(),
      excludedApplicationReference,
      usernameToExcludeFromReservedApplications,
    )
  }

  fun getReservedApplicationsCountForSlot(
    sessionSlotId: Long,
    restriction: VisitRestriction,
    excludedApplicationReference: String? = null,
  ): Long = if (VisitRestriction.OPEN == restriction) {
    applicationRepository.getCountOfReservedApplicationsForOpenSessionSlot(sessionSlotId, getExpiredApplicationDateAndTime(), excludedApplicationReference = excludedApplicationReference)
  } else {
    applicationRepository.getCountOfReservedApplicationsForClosedSessionSlot(sessionSlotId, getExpiredApplicationDateAndTime(), excludedApplicationReference = excludedApplicationReference)
  }

  fun createApplicationFromMigration(migrateVisitRequest: MigrateVisitRequestDto, applicationFromMigration: Application): Application {
    val applicationEntity = applicationRepository.saveAndFlush(applicationFromMigration)

    migrateVisitRequest.visitContact?.let { contact ->
      applicationEntity.visitContact = createApplicationContact(
        applicationEntity,
        if (UNKNOWN_TOKEN == contact.name || contact.name.partition { it.isLowerCase() }.first.isNotEmpty()) {
          contact.name
        } else {
          capitaliseUtil.capitalise(contact.name)
        },
        contact.telephone,
        email = null,
      )
    }

    migrateVisitRequest.visitors?.let { contactList ->
      contactList.forEach {
        applicationEntity.visitors.add(createApplicationVisitor(applicationEntity, it.nomisPersonId, null))
      }
    }

    return applicationEntity
  }
}
