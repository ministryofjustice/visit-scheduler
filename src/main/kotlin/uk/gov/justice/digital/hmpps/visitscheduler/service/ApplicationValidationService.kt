package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.exception.OverCapacityException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VSiPValidationException
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

  fun isApplicationValid(
    application: Application,
    existingBooking: Visit? = null,
    applicationValidationEvent: ApplicationValidationEvent,
    isReservedSlot: Boolean,
    allowOverBooking: Boolean,
  ) {
    val errorMessages = when (application.userType) {
      PUBLIC -> isPublicApplicationValid(
        application = application,
        existingBooking = existingBooking,
        applicationValidationEvent = applicationValidationEvent,
        allowOverBooking = allowOverBooking,
        isReservedSlot = isReservedSlot,
      )

      STAFF -> isStaffApplicationValid(
        application = application,
        existingBooking = existingBooking,
        applicationValidationEvent = applicationValidationEvent,
        allowOverBooking = allowOverBooking,
        isReservedSlot = isReservedSlot,
      )
    }

    if (errorMessages.isNotEmpty()) {
      throw VSiPValidationException(errorMessages.toTypedArray())
    }
  }

  private fun isPublicApplicationValid(
    application: Application,
    existingBooking: Visit?,
    applicationValidationEvent: ApplicationValidationEvent,
    allowOverBooking: Boolean,
    isReservedSlot: Boolean,
  ): List<String> {
    val errorMessages = mutableListOf<String>()
    val prison = application.prison
    val prisoner = prisonerService.getPrisoner(application.prisonerId) ?: throw VSiPValidationException("prisoner not found")

    checkPrison(prison.code, prisoner.prisonCode)
    checkSessionSlot(application, prisoner, prison)?.also {
      errorMessages.add(it)
    }

    // TODO - enable this for application creation at a later stage
    if (applicationValidationEvent == ApplicationValidationEvent.BOOKING) {
      // check if there are non-association visits that have been booked in after the application was created
      checkNonAssociationVisits(
        prisonerId = application.prisonerId,
        sessionDate = application.sessionSlot.slotDate,
        prisonId = application.prisonId,
      )?.also {
        errorMessages.add(it)
      }

      // check if any double bookings for the same prisoner
      checkDoubleBookedVisits(
        prisonerId = application.prisonerId,
        sessionSlot = application.sessionSlot,
        visitReference = application.visit?.reference,
      )?.also {
        errorMessages.add(it)
      }

      // check prisoner's VOs - only applicable if user type = PUBLIC as staff can override VO count
      checkVOLimits(application.prisonerId)?.also {
        errorMessages.add(it)
      }
    }

    // check capacity for slot
    checkSlotCapacity(application, existingBooking, allowOverBooking = allowOverBooking, applicationValidationEvent = applicationValidationEvent, isReservedSlot = isReservedSlot)?.also {
      errorMessages.add(it)
    }

    return errorMessages.toList()
  }

  private fun isStaffApplicationValid(
    application: Application,
    existingBooking: Visit?,
    applicationValidationEvent: ApplicationValidationEvent,
    allowOverBooking: Boolean,
    isReservedSlot: Boolean,
  ): List<String> {
    val errorMessages = mutableListOf<String>()
    // check capacity for slot
    checkSlotCapacity(application, existingBooking, allowOverBooking = allowOverBooking, applicationValidationEvent = applicationValidationEvent, isReservedSlot = isReservedSlot)?.also {
      errorMessages.add(it)
    }

    return errorMessages
  }

  private fun checkSessionSlot(application: Application, prisoner: PrisonerDto, prison: Prison): String? {
    val sessionSlot = application.sessionSlot

    sessionSlot.sessionTemplateReference?.let {
      val sessionTemplate = sessionTemplateRepository.findByReference(it)
      sessionTemplate?.let {
        val prisonerHousingLevels =
          prisonerService.getPrisonerHousingLevels(application.prisonerId, prison.code, listOf(sessionTemplate))
        val isSessionAvailableToPrisoner = prisonerSessionValidationService.isSessionAvailableToPrisoner(
          sessionTemplate = sessionTemplate,
          prisoner = prisoner,
          prisonerHousingLevels = prisonerHousingLevels,
        )
        if (!isSessionAvailableToPrisoner) {
          return "session slot with reference - ${sessionSlot.reference} is unavailable to prisoner"
        }
      } ?: return "session template with reference - ${sessionSlot.sessionTemplateReference} not found"
    }

    return null
  }

  private fun checkPrison(applicationPrisonCode: String, prisonerPrisonCode: String?) {
    if (applicationPrisonCode != prisonerPrisonCode) {
      throw VSiPValidationException("application's prison code - $applicationPrisonCode is different to prison code for prisoner - $prisonerPrisonCode")
    }
  }

  private fun checkNonAssociationVisits(prisonerId: String, sessionDate: LocalDate, prisonId: Long): String? {
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
        return "non-associations for prisoner - $prisonerId have booked visits on $sessionDate at the same prison."
      }
    }

    return null
  }

  private fun checkDoubleBookedVisits(prisonerId: String, sessionSlot: SessionSlot, visitReference: String?): String? {
    if (visitRepository.hasActiveVisitForSessionSlot(
        prisonerId = prisonerId,
        sessionSlotId = sessionSlot.id,
        excludeVisitReference = visitReference,
      )
    ) {
      return "There is already a visit booked for prisoner - $prisonerId on session slot - ${sessionSlot.reference}."
    }

    return null
  }

  private fun checkVOLimits(prisonerId: String): String? {
    // check VO limits
    val remainingVisitBalance = prisonerService.getVisitBalance(prisonerId = prisonerId)
    if (remainingVisitBalance <= 0) {
      return "not enough VO balance for prisoner - $prisonerId"
    }

    return null
  }

  fun checkSlotCapacity(
    application: Application,
    existingBooking: Visit?,
    applicationValidationEvent: ApplicationValidationEvent,
    allowOverBooking: Boolean,
    isReservedSlot: Boolean,
  ): String? {
    try {
      when (applicationValidationEvent) {
        ApplicationValidationEvent.CREATE_APPLICATION -> {
          if (isReservedSlot && !allowOverBooking) {
            slotCapacityService.checkCapacityForApplicationReservation(
              application.sessionSlot.reference,
              application.restriction,
              incReservedApplications = true,
            )
          }
        }

        ApplicationValidationEvent.BOOKING -> {
          if (!allowOverBooking && hasSlotChangedSinceLastBooking(existingBooking, application)) {
            slotCapacityService.checkCapacityForBooking(
              application.sessionSlot.reference,
              application.restriction,
              (isReservedSlot) && includeReservedApplications(application),
            )
          }
        }
      }
    } catch (overCapacityException: OverCapacityException) {
      return overCapacityException.message
    }

    return null
  }

  private fun hasSlotChangedSinceLastBooking(
    existingBooking: Visit?,
    application: Application,
  ): Boolean {
    return existingBooking?.let {
      it.visitRestriction != application.restriction || it.sessionSlot.id != application.sessionSlotId
    } ?: run { true }
  }

  private fun includeReservedApplications(application: Application): Boolean {
    return when (application.userType) {
      STAFF -> applicationService.isExpiredApplication(application.modifyTimestamp!!)
      PUBLIC -> false
    }
  }

  enum class ApplicationValidationEvent {
    CREATE_APPLICATION,
    BOOKING,
  }
}
