package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.ValidationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.BookingRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
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
    bookingRequestDto: BookingRequestDto? = null,
    application: Application,
    existingBooking: Visit? = null,
  ) {
    try {
      when (application.userType) {
        PUBLIC -> isPublicApplicationValid(bookingRequestDto, application, existingBooking)

        STAFF -> isStaffApplicationValid(bookingRequestDto, application, existingBooking)
      }
    } catch (ve: ValidationException) {
      LOG.error("Validation failed for application reference - ${application.reference} with msg - ${ve.message}")
      throw ve
    }
  }

  private fun isPublicApplicationValid(
    bookingRequestDto: BookingRequestDto?,
    application: Application,
    existingBooking: Visit?,
  ) {
    val prison = application.prison
    val prisoner = prisonerService.getPrisoner(application.prisonerId) ?: throw ValidationException("prisoner not found")

    checkPrison(prison.code, prisoner.prisonCode)
    checkSessionSlot(application, prisoner, prison)

    // check if there are non-association visits that have been booked in after the application was created
    checkNonAssociationVisits(
      prisonerId = application.prisonerId,
      sessionDate = application.sessionSlot.slotDate,
      prisonId = application.prisonId,
    )

    // check if any double bookings for the same prisoner
    checkDoubleBookedVisits(prisonerId = application.prisonerId, sessionSlot = application.sessionSlot)

    // check prisoner's VOs - only applicable if user type = PUBLIC as staff can override VO count
    checkVOLimits(application.prisonerId)

    // check capacity for slot
    checkSlotCapacity(bookingRequestDto, application, existingBooking)
  }

  private fun isStaffApplicationValid(
    bookingRequestDto: BookingRequestDto?,
    application: Application,
    existingBooking: Visit?,
  ) {
    // check capacity for slot
    checkSlotCapacity(bookingRequestDto, application, existingBooking)
  }

  private fun checkSessionSlot(application: Application, prisoner: PrisonerDto, prison: Prison) {
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
          throw ValidationException("session slot with reference - ${sessionSlot.reference} is unavailable to prisoner")
        }
      }
        ?: throw ValidationException("session template with reference - ${sessionSlot.sessionTemplateReference} not found")
    }
  }

  private fun checkPrison(applicationPrisonCode: String, prisonerPrisonCode: String?) {
    if (applicationPrisonCode != prisonerPrisonCode) {
      throw ValidationException("application's prison code - $applicationPrisonCode is different to prison code for prisoner - $prisonerPrisonCode")
    }
  }

  private fun checkNonAssociationVisits(prisonerId: String, sessionDate: LocalDate, prisonId: Long) {
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
        throw ValidationException("non-associations for prisoner - $prisonerId have booked visits on $sessionDate at the same prison.")
      }
    }
  }

  private fun checkDoubleBookedVisits(prisonerId: String, sessionSlot: SessionSlot) {
    if (visitRepository.hasActiveVisitForDate(
        prisonerId,
        sessionSlot.id,
      )
    ) {
      throw ValidationException("There is already a visit booked for prisoner - $prisonerId on session slot - ${sessionSlot.reference}.")
    }
  }

  private fun checkVOLimits(prisonerId: String) {
    // check VO limits
    val remainingVisitBalance = prisonerService.getVisitBalance(prisonerId = prisonerId)
    if (remainingVisitBalance <= 0) {
      throw ValidationException("not enough VO balance for prisoner - $prisonerId")
    }
  }

  fun checkSlotCapacity(
    bookingRequestDto: BookingRequestDto?,
    application: Application,
    existingBooking: Visit?,
  ) {
    val allowOverBooking = bookingRequestDto?.allowOverBooking ?: false

    if (!allowOverBooking && hasSlotChangedSinceLastBooking(existingBooking, application)) {
      slotCapacityService.checkCapacityForBooking(
        application.sessionSlot.reference,
        application.restriction,
        includeReservedApplications(application),
      )
    }
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
}
