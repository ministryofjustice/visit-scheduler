package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.BookingRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateVisitFromExternalSystemDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.builder.VisitDtoBuilder
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason.VISIT_UPDATED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.exception.ExpiredVisitAmendException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.PrisonNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VisitNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitSupport
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionSlotRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
@Transactional
class VisitStoreService(
  private val visitRepository: VisitRepository,
  private val prisonRepository: PrisonRepository,
  private val sessionSlotRepository: SessionSlotRepository,
  private val applicationValidationService: ApplicationValidationService,
  private val applicationService: ApplicationService,
  @Value("\${visit.cancel.day-limit:28}") private val visitCancellationDayLimit: Int,
) {

  @Lazy
  @Autowired
  private lateinit var visitNotificationEventService: VisitNotificationEventService

  @Autowired
  private lateinit var sessionTemplateService: SessionTemplateService

  @Autowired
  private lateinit var visitDtoBuilder: VisitDtoBuilder

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    const val AMEND_EXPIRED_ERROR_MESSAGE = "Visit with booking reference - %s is in the past, it cannot be %s"
  }

  fun checkBookingAlreadyCancelled(reference: String): VisitDto? {
    if (visitRepository.isBookingCancelled(reference)) {
      // If already cancelled then just return object and do nothing more!
      LOG.debug("The visit $reference has already been cancelled!")
      val cancelledVisit = visitRepository.findByReference(reference)!!
      return visitDtoBuilder.build(cancelledVisit)
    }

    return null
  }

  fun checkBookingAlreadyMade(applicationReference: String): VisitDto? {
    if (applicationService.isApplicationCompleted(applicationReference)) {
      LOG.debug("The application $applicationReference has already been booked!")
      // If already booked then just return object and do nothing more!
      val visit = visitRepository.findVisitByApplicationReference(applicationReference)!!
      return visitDtoBuilder.build(visit)
    }

    return null
  }

  fun createOrUpdateBooking(applicationReference: String, bookingRequestDto: BookingRequestDto): VisitDto {
    // Need to set application complete at earliest opportunity to prevent two bookings from being created, Edge case.
    applicationService.completeApplication(applicationReference)

    val application = applicationService.getApplicationEntity(applicationReference)

    val existingBooking = visitRepository.findVisitByApplicationReference(application.reference)

    // application validity checks
    applicationValidationService.validateApplication(bookingRequestDto, application, existingBooking)

    val visitRoom = sessionTemplateService.getVisitRoom(application.sessionSlot.sessionTemplateReference!!)

    val notSavedBooking = existingBooking?.let {
      validateVisitStartDate(it, "changed")
      handleVisitUpdateEvents(it)

      // Update existing booking
      it.sessionSlotId = application.sessionSlotId
      it.sessionSlot = application.sessionSlot
      it.visitType = application.visitType
      it.visitRestriction = application.restriction
      it.visitRoom = visitRoom
      it.visitStatus = BOOKED
      it
    } ?: run {
      // Create new booking
      Visit(
        prisonId = application.prisonId,
        prison = application.prison,
        prisonerId = application.prisonerId,
        sessionSlotId = application.sessionSlotId,
        sessionSlot = application.sessionSlot,
        visitType = application.visitType,
        visitRestriction = application.restriction,
        visitRoom = visitRoom,
        visitStatus = BOOKED,
        userType = application.userType,
      )
    }

    val booking = visitRepository.saveAndFlush(notSavedBooking)

    if (hasNotBeenAddedToBooking(booking, application)) {
      booking.addApplication(application)
    }

    application.visitContact?.let {
      booking.visitContact?.let { visitContact ->
        visitContact.name = it.name
        visitContact.telephone = it.telephone
        visitContact.email = it.email
      } ?: run {
        booking.visitContact = VisitContact(
          visit = booking,
          visitId = booking.id,
          name = it.name,
          telephone = it.telephone,
          email = it.email,
        )
      }
    }

    application.support?.let { applicationSupport ->
      booking.support?.let {
        it.description = applicationSupport.description
      } ?: run {
        booking.support = VisitSupport(visit = booking, visitId = booking.id, description = applicationSupport.description)
      }
    } ?: run {
      booking.support = null
    }

    application.visitors.let {
      booking.visitors.clear()
      visitRepository.saveAndFlush(booking)
      it.map { applicationVisitor ->
        with(applicationVisitor) {
          booking.visitors.add(VisitVisitor(visit = booking, visitId = booking.id, nomisPersonId = nomisPersonId, visitContact = contact))
        }
      }
    }

    val savedBooking = visitRepository.saveAndFlush(booking)
    return visitDtoBuilder.build(savedBooking)
  }

  @Transactional(readOnly = true)
  fun getBookingByApplicationReference(applicationReference: String): VisitDto? = visitRepository.findVisitByApplicationReference(applicationReference)?.let {
    visitDtoBuilder.build(it)
  }

  private fun hasNotBeenAddedToBooking(booking: Visit, application: Application): Boolean = if (booking.getApplications().isEmpty()) true else booking.getApplications().any { it.id == application.id }

  private fun validateVisitStartDate(
    visit: Visit,
    action: String,
    allowedVisitStartDate: LocalDateTime = LocalDateTime.now(),
  ) {
    if (visit.sessionSlot.slotStart.isBefore(allowedVisitStartDate)) {
      throw ExpiredVisitAmendException(
        AMEND_EXPIRED_ERROR_MESSAGE.format(visit.reference, action),
        ExpiredVisitAmendException("trying to change / cancel an expired visit"),
      )
    }
  }

  private fun handleVisitUpdateEvents(existingBooking: Visit) {
    visitNotificationEventService.deleteVisitAndPairedNotificationEvents(existingBooking.reference, VISIT_UPDATED)
  }

  fun cancelVisit(reference: String, cancelVisitDto: CancelVisitDto): VisitDto {
    val visitEntity = visitRepository.findBookedVisit(reference) ?: throw VisitNotFoundException("Visit $reference not found")
    validateCancelRequest(cancelVisitDto, visitEntity)

    val cancelOutcome = cancelVisitDto.cancelOutcome

    visitEntity.visitStatus = CANCELLED
    visitEntity.outcomeStatus = cancelOutcome.outcomeStatus

    cancelOutcome.text?.let {
      visitEntity.visitNotes.add(createVisitNote(visitEntity, VisitNoteType.VISIT_OUTCOMES, cancelOutcome.text))
    }

    return visitDtoBuilder.build(visitRepository.saveAndFlush(visitEntity))
  }

  private fun validateCancelRequest(cancelVisitDto: CancelVisitDto, visitEntity: Visit) {
    // STAFF is allowed to cancel older visits but not PUBLIC
    val allowedCancellationDate = if (cancelVisitDto.userType == UserType.STAFF) {
      getAllowedCancellationDate(visitCancellationDayLimit = visitCancellationDayLimit)
    } else {
      LocalDateTime.now()
    }
    validateVisitStartDate(
      visitEntity,
      "cancelled",
      allowedCancellationDate,
    )
  }

  private fun createVisitNote(visit: Visit, type: VisitNoteType, text: String): VisitNote = VisitNote(
    visitId = visit.id,
    type = type,
    text = text,
    visit = visit,
  )

  private fun getAllowedCancellationDate(currentDateTime: LocalDateTime = LocalDateTime.now(), visitCancellationDayLimit: Int): LocalDateTime {
    var visitCancellationDateAllowed = currentDateTime
    // check if the visit being cancelled is in the past
    if (visitCancellationDayLimit > 0) {
      visitCancellationDateAllowed = visitCancellationDateAllowed.minusDays(visitCancellationDayLimit.toLong()).truncatedTo(ChronoUnit.DAYS)
    }

    return visitCancellationDateAllowed
  }

  fun createVisitFromExternalSystem(privatePrisonVisitDto: CreateVisitFromExternalSystemDto): CreateVisitFromExternalSystemDto {
    val prison = prisonRepository.findByCode(privatePrisonVisitDto.prisonId)
      ?: throw PrisonNotFoundException("Prison ${privatePrisonVisitDto.prisonId} not found")

    val newSessionSlot = SessionSlot(
      prisonId = prison.id,
      slotDate = privatePrisonVisitDto.startTimestamp.toLocalDate(),
      slotStart = privatePrisonVisitDto.startTimestamp,
      slotEnd = privatePrisonVisitDto.endTimestamp,
    )
    val sessionSlot = sessionSlotRepository.saveAndFlush(newSessionSlot)

    val newVisit = Visit(
      prisonId = prison.id,
      prison = prison,
      prisonerId = privatePrisonVisitDto.prisonerId,
      sessionSlotId = sessionSlot.id,
      sessionSlot = sessionSlot,
      visitType = privatePrisonVisitDto.visitType,
      visitRestriction = privatePrisonVisitDto.visitRestriction,
      visitRoom = privatePrisonVisitDto.visitRoom,
      visitStatus = BOOKED,
      userType = UserType.PRISONER,
    )

    newVisit.visitors.addAll(
      privatePrisonVisitDto.visitors?.map {
        VisitVisitor(
          visitId = newVisit.id,
          nomisPersonId = it.nomisPersonId,
          visitContact = it.visitContact,
          visit = newVisit,
        )
      }.orEmpty(),
    )

    val visit = visitRepository.saveAndFlush(newVisit)

    return CreateVisitFromExternalSystemDto(
      prisonId = visit.prison.code,
      prisonerId = visit.prisonerId,
      clientVisitReference = privatePrisonVisitDto.clientVisitReference,
      visitRoom = visit.visitRoom,
      visitType = visit.visitType,
      visitRestriction = visit.visitRestriction,
      startTimestamp = privatePrisonVisitDto.startTimestamp,
      endTimestamp = privatePrisonVisitDto.endTimestamp,
      createDateTime = privatePrisonVisitDto.createDateTime,
      visitors = privatePrisonVisitDto.visitors,
      actionedBy = privatePrisonVisitDto.actionedBy,
      visitContact = privatePrisonVisitDto.visitContact,
    )
  }
}
