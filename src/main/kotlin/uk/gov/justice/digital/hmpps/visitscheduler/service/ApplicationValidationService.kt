package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.BookingRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationValidationErrorCodes
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationValidationErrorCodes.APPLICATION_INVALID_NON_ASSOCIATION_VISITS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationValidationErrorCodes.APPLICATION_INVALID_NO_SLOT_CAPACITY
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationValidationErrorCodes.APPLICATION_INVALID_NO_VO_BALANCE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationValidationErrorCodes.APPLICATION_INVALID_PRISONER_NOT_FOUND
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationValidationErrorCodes.APPLICATION_INVALID_PRISON_PRISONER_MISMATCH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationValidationErrorCodes.APPLICATION_INVALID_SESSION_NOT_AVAILABLE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationValidationErrorCodes.APPLICATION_INVALID_SESSION_TEMPLATE_NOT_FOUND
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationValidationErrorCodes.APPLICATION_INVALID_VISIT_ALREADY_BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.SYSTEM
import uk.gov.justice.digital.hmpps.visitscheduler.exception.ApplicationValidationException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.OverCapacityException
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDate

@Service
class ApplicationValidationService(
  private val prisonerSessionValidationService: PrisonerSessionValidationService,
  private val prisonerService: PrisonerService,
  @Lazy
  private val slotCapacityService: SlotCapacityService,
  private val applicationService: ApplicationService,
  private val visitRepository: VisitRepository,
  private val sessionTemplateRepository: SessionTemplateRepository,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun validateApplication(
    bookingRequestDto: BookingRequestDto? = null,
    application: Application,
    existingBooking: Visit? = null,
  ) {
    val errorCodes = when (application.userType) {
      PUBLIC -> getPublicApplicationValidationErrors(bookingRequestDto, application, existingBooking)

      STAFF -> getStaffApplicationValidationErrors(bookingRequestDto, application, existingBooking)

      SYSTEM -> getSystemApplicationValidationErrors()
    }

    if (errorCodes.isNotEmpty()) {
      throw ApplicationValidationException(errorCodes.toTypedArray())
    }
  }

  private fun getPublicApplicationValidationErrors(
    bookingRequestDto: BookingRequestDto?,
    application: Application,
    existingBooking: Visit?,
  ): List<ApplicationValidationErrorCodes> {
    val errorCodes = mutableListOf<ApplicationValidationErrorCodes>()
    val prison = application.prison
    val prisoner = prisonerService.getPrisoner(application.prisonerId) ?: run {
      LOG.info("Prisoner with id - ${application.prisonerId} not found.")
      throw ApplicationValidationException(
        APPLICATION_INVALID_PRISONER_NOT_FOUND,
      )
    }

    checkPrison(prison.code, prisoner.prisonCode)
    checkSessionSlot(application, prisoner, prison)?.also {
      errorCodes.add(it)
    }

    // check if there are non-association visits that have been booked in after the application was created
    checkNonAssociationVisits(
      prisonerId = application.prisonerId,
      sessionDate = application.sessionSlot.slotDate,
      prisonId = application.prisonId,
    )?.also {
      errorCodes.add(it)
    }

    // check if any double bookings for the same prisoner
    checkDoubleBookedVisits(prisonerId = application.prisonerId, sessionSlot = application.sessionSlot, visitReference = application.visit?.reference)?.also {
      errorCodes.add(it)
    }

    // check prisoner's VOs - only applicable if user type = PUBLIC as staff can override VO count
    checkVOLimits(prisoner)?.also {
      errorCodes.add(it)
    }

    // check capacity for slot
    checkSlotCapacity(bookingRequestDto, application, existingBooking)?.also {
      errorCodes.add(it)
    }

    return errorCodes.toList()
  }

  private fun getStaffApplicationValidationErrors(
    bookingRequestDto: BookingRequestDto?,
    application: Application,
    existingBooking: Visit?,
  ): List<ApplicationValidationErrorCodes> {
    val errorCodes = mutableListOf<ApplicationValidationErrorCodes>()
    // check capacity for slot
    checkSlotCapacity(bookingRequestDto, application, existingBooking)?.also {
      errorCodes.add(it)
    }

    // check if there are non-association visits that have been booked in after the application was created
    checkNonAssociationVisits(
      prisonerId = application.prisonerId,
      sessionDate = application.sessionSlot.slotDate,
      prisonId = application.prisonId,
    )?.also {
      errorCodes.add(it)
    }

    // check if any double bookings for the same prisoner
    checkDoubleBookedVisits(prisonerId = application.prisonerId, sessionSlot = application.sessionSlot, visitReference = application.visit?.reference)?.also {
      errorCodes.add(it)
    }

    return errorCodes.toList()
  }

  private fun getSystemApplicationValidationErrors(): List<ApplicationValidationErrorCodes> {
    // no validations
    return emptyList()
  }

  private fun checkSessionSlot(application: Application, prisoner: PrisonerDto, prison: Prison): ApplicationValidationErrorCodes? {
    val sessionSlot = application.sessionSlot

    sessionSlot.sessionTemplateReference?.let {
      val sessionTemplate = sessionTemplateRepository.findByReference(it)
      if (sessionTemplate != null) {
        val prisonerHousingLevels =
          prisonerService.getPrisonerHousingLevels(application.prisonerId, prison.code, listOf(sessionTemplate))
        val isSessionAvailableToPrisoner = prisonerSessionValidationService.isSessionAvailableToPrisoner(
          sessionTemplate = sessionTemplate,
          prisoner = prisoner,
          prisonerHousingLevels = prisonerHousingLevels,
        )
        if (!isSessionAvailableToPrisoner) {
          LOG.info("session slot with reference - ${sessionSlot.reference} is unavailable to prisoner")
          return APPLICATION_INVALID_SESSION_NOT_AVAILABLE
        }
      } else {
        LOG.info("session template with reference - ${sessionSlot.sessionTemplateReference} not found")
        return APPLICATION_INVALID_SESSION_TEMPLATE_NOT_FOUND
      }
    }

    return null
  }

  private fun checkPrison(applicationPrisonCode: String, prisonerPrisonCode: String?) {
    if (applicationPrisonCode != prisonerPrisonCode) {
      LOG.info("application's prison code - $applicationPrisonCode is different to prison code for prisoner - $prisonerPrisonCode")
      throw ApplicationValidationException(APPLICATION_INVALID_PRISON_PRISONER_MISMATCH)
    }
  }

  private fun checkNonAssociationVisits(prisonerId: String, sessionDate: LocalDate, prisonId: Long): ApplicationValidationErrorCodes? {
    // check non association visits
    val nonAssociationPrisonerIds =
      prisonerService.getPrisonerNonAssociationList(prisonerId).map { it.otherPrisonerDetails.prisonerNumber }
    if (nonAssociationPrisonerIds.isNotEmpty()) {
      if (visitRepository.hasActiveVisitsForDate(
          nonAssociationPrisonerIds,
          sessionDate,
          prisonId,
        )
      ) {
        LOG.info("non-associations for prisoner - $prisonerId have booked visits on $sessionDate at the same prison.")
        return APPLICATION_INVALID_NON_ASSOCIATION_VISITS
      }
    }

    return null
  }

  private fun checkDoubleBookedVisits(prisonerId: String, sessionSlot: SessionSlot, visitReference: String?): ApplicationValidationErrorCodes? {
    if (visitRepository.hasActiveVisitForSessionSlot(
        prisonerId = prisonerId,
        sessionSlotId = sessionSlot.id,
        excludeVisitReference = visitReference,
      )
    ) {
      LOG.info("There is already a visit booked for prisoner - $prisonerId on session slot - ${sessionSlot.reference}.")
      return APPLICATION_INVALID_VISIT_ALREADY_BOOKED
    }

    return null
  }

  private fun checkVOLimits(prisoner: PrisonerDto): ApplicationValidationErrorCodes? {
    // check VO limits if prisoner is not on Remand.
    if (prisoner.convictedStatus != "Remand") {
      val remainingVisitBalance = prisonerService.getVisitBalance(prisonerId = prisoner.prisonerId)
      if (remainingVisitBalance <= 0) {
        LOG.info("not enough VO balance for prisoner - ${prisoner.prisonerId} to book visit")
        return APPLICATION_INVALID_NO_VO_BALANCE
      }
    }
    return null
  }

  fun checkSlotCapacity(
    bookingRequestDto: BookingRequestDto?,
    application: Application,
    existingBooking: Visit?,
  ): ApplicationValidationErrorCodes? {
    val allowOverBooking = bookingRequestDto?.allowOverBooking ?: false

    if (!allowOverBooking && hasSlotChangedSinceLastBooking(existingBooking, application)) {
      try {
        slotCapacityService.checkCapacityForBooking(
          application.sessionSlot.reference,
          application.restriction,
          includeReservedApplications(application),
        )
      } catch (overCapacityException: OverCapacityException) {
        LOG.info(overCapacityException.message)
        return APPLICATION_INVALID_NO_SLOT_CAPACITY
      }
    }

    return null
  }

  private fun hasSlotChangedSinceLastBooking(
    existingBooking: Visit?,
    application: Application,
  ): Boolean = existingBooking?.let {
    it.visitRestriction != application.restriction || it.sessionSlot.id != application.sessionSlotId
  } ?: run { true }

  private fun includeReservedApplications(application: Application): Boolean = when (application.userType) {
    STAFF -> applicationService.isExpiredApplication(application.modifyTimestamp!!)
    PUBLIC -> false
    SYSTEM -> false
  }
}
