package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.BookingDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.BookingRequest
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.NoteDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.SupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.NoteType
import uk.gov.justice.digital.hmpps.visitscheduler.model.StatusType
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Booking
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Contact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Note
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Support
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visitor
import uk.gov.justice.digital.hmpps.visitscheduler.repository.ReservationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SupportTypeRepository
import java.util.function.Supplier

@Service
@Transactional
class BookingService(
  private val reservationRepository: ReservationRepository,
  private val supportTypeRepository: SupportTypeRepository,
) {

  fun createBooking(reference: String, request: BookingRequest): BookingDto {

    val entity = reservationRepository.findByReference(reference) ?: throw ReservationNotFoundException("Reservation $reference not found")

    if (entity.booking != null)
      throw BookingFoundException("Booking for reservation $reference already exists")

    entity.booking =
      Booking(
        reservationId = entity.id,
        prisonerId = request.prisonerId,
        prisonId = request.prisonId,
        visitType = request.visitType,
        visitStatus = StatusType.BOOKED,
        reservation = entity,
      )
    reservationRepository.saveAndFlush(entity)

    request.visitContact?.let { contact ->
      entity.booking!!.visitContact = createBookingContact(entity.booking!!, contact.name, contact.telephone)
    }

    request.visitors.forEach {
      entity.booking!!.visitors.add(createBookingVisitor(entity.booking!!, it.nomisPersonId))
    }

    request.visitorSupport?.let { supportList ->
      supportList.forEach {
        supportTypeRepository.findByName(it.type) ?: throw SupportNotFoundException("Invalid support ${it.type} not found")
        entity.booking!!.support.add(createBookingSupport(entity.booking!!, it.type, it.text))
      }
    }

    return BookingDto(entity.booking!!)
  }

  @Transactional(readOnly = true)
  fun getBooking(reference: String): BookingDto {
    val entity = reservationRepository.findByReference(reference) ?: throw ReservationNotFoundException("Reservation $reference not found")
    val booking: Booking = entity.booking ?: throw BookingNotFoundException("Booking for reservation $reference not found")

    return BookingDto(booking)
  }

  fun updateBooking(reference: String, request: BookingRequest): BookingDto {
    val entity = reservationRepository.findByReference(reference) ?: throw ReservationNotFoundException("Reservation $reference not found")

    val booking: Booking = entity.booking ?: throw BookingNotFoundException("Booking for reservation $reference not found")

    request.prisonerId.let { prisonerId -> booking.prisonerId = prisonerId }
    request.prisonId.let { prisonId -> booking.prisonId = prisonId }
    request.visitType.let { visitType -> booking.visitType = visitType }

    request.visitContact?.let { visitContactUpdate ->
      booking.visitContact?.let { visitContact ->
        visitContact.name = visitContactUpdate.name
        visitContact.telephone = visitContactUpdate.telephone
      } ?: run {
        booking.visitContact = createBookingContact(booking, visitContactUpdate.name, visitContactUpdate.telephone)
      }
    }

    request.visitors.let { visitorsUpdate ->
      booking.visitors.clear()
      reservationRepository.saveAndFlush(entity)
      visitorsUpdate.distinctBy { it.nomisPersonId }.forEach {
        booking.visitors.add(createBookingVisitor(booking, it.nomisPersonId))
      }
    }

    request.visitorSupport?.let { visitSupportUpdate ->
      booking.support.clear()
      reservationRepository.saveAndFlush(entity)
      visitSupportUpdate.forEach { supportType ->
        supportTypeRepository.findByName(supportType.type) ?: throw SupportNotFoundException("Invalid support ${supportType.type} not found")
        booking.support.add(createBookingSupport(booking, supportType.type, supportType.text))
      }
    }

    return BookingDto(booking)
  }

  fun deleteBooking(reference: String) {
    val entity = reservationRepository.findByReference(reference)

    entity?.let {
      it.booking?.let {
        entity.booking = null
        reservationRepository.saveAndFlush(entity)
      } ?: run { log.debug("Booking for reservation $reference not found") }
    } ?: run { log.debug("Reservation $reference not found") }
  }

  fun updatePrisoner(reference: String, prisonerId: String): BookingDto {
    val entity = reservationRepository.findByReference(reference) ?: throw ReservationNotFoundException("Reservation $reference not found")
    val booking: Booking = entity.booking ?: throw BookingNotFoundException("Booking for reservation $reference not found")
    prisonerId.let { id -> booking.prisonerId = id }
    return BookingDto(booking)
  }

  fun updatePrison(reference: String, prisonId: String): BookingDto {
    val entity = reservationRepository.findByReference(reference) ?: throw ReservationNotFoundException("Reservation $reference not found")
    val booking: Booking = entity.booking ?: throw BookingNotFoundException("Booking for reservation $reference not found")
    prisonId.let { id -> booking.prisonId = id }
    return BookingDto(booking)
  }

  fun updateType(reference: String, type: VisitType): BookingDto {
    val entity = reservationRepository.findByReference(reference) ?: throw ReservationNotFoundException("Reservation $reference not found")
    val booking: Booking = entity.booking ?: throw BookingNotFoundException("Booking for reservation $reference not found")
    type.let { id -> booking.visitType = id }
    return BookingDto(booking)
  }

  fun updateStatus(reference: String, status: StatusType): BookingDto {
    val entity = reservationRepository.findByReference(reference) ?: throw ReservationNotFoundException("Reservation $reference not found")
    val booking: Booking = entity.booking ?: throw BookingNotFoundException("Booking for reservation $reference not found")
    status.let { id -> booking.visitStatus = id }
    return BookingDto(booking)
  }

  fun updateContact(reference: String, contact: ContactDto): BookingDto {
    val entity = reservationRepository.findByReference(reference) ?: throw ReservationNotFoundException("Reservation $reference not found")
    val booking: Booking = entity.booking ?: throw BookingNotFoundException("Booking for reservation $reference not found")

    contact.let { visitContactUpdate ->
      booking.visitContact?.let { visitContact ->
        visitContact.name = visitContactUpdate.name
        visitContact.telephone = visitContactUpdate.telephone
      } ?: run {
        booking.visitContact = createBookingContact(booking, visitContactUpdate.name, visitContactUpdate.telephone)
      }
    }

    return BookingDto(booking)
  }

  fun updateVisitors(reference: String, visitors: Set<VisitorDto>): BookingDto {
    val entity = reservationRepository.findByReference(reference) ?: throw ReservationNotFoundException("Reservation $reference not found")
    val booking: Booking = entity.booking ?: throw BookingNotFoundException("Booking for reservation $reference not found")

    visitors.let { visitorsUpdate ->
      booking.visitors.clear()
      reservationRepository.saveAndFlush(entity)
      visitorsUpdate.distinctBy { it.nomisPersonId }.forEach {
        booking.visitors.add(createBookingVisitor(booking, it.nomisPersonId))
      }
    }

    return BookingDto(booking)
  }

  fun updateSupport(reference: String, support: Set<SupportDto>): BookingDto {
    val entity = reservationRepository.findByReference(reference) ?: throw ReservationNotFoundException("Reservation $reference not found")
    val booking: Booking = entity.booking ?: throw BookingNotFoundException("Booking for reservation $reference not found")

    support.let { visitSupportUpdate ->
      booking.support.clear()
      reservationRepository.saveAndFlush(entity)
      visitSupportUpdate.forEach {
        supportTypeRepository.findByName(it.type) ?: throw SupportNotFoundException("Invalid support ${it.type} not found")
        booking.support.add(createBookingSupport(booking, it.type, it.text))
      }
    }

    return BookingDto(booking)
  }

  fun updateNotes(reference: String, notes: Set<NoteDto>): BookingDto {
    val entity = reservationRepository.findByReference(reference) ?: throw ReservationNotFoundException("Reservation $reference not found")
    val booking: Booking = entity.booking ?: throw BookingNotFoundException("Booking for reservation $reference not found")

    // Replace
    notes.let { updateNotes ->
      entity.booking?.visitNotes?.clear()
      reservationRepository.saveAndFlush(entity)
      updateNotes.forEach { note ->
        entity.booking?.visitNotes?.add(createBookingNote(entity.booking!!, note.type, note.text))
      }
    }

    // Alt update or add
    /*notes?.let { updateNotes ->
      updateNotes.forEach { note ->
        entity.booking?.visitNotes?.removeIf { it.type == note.type }
        entity.booking?.visitNotes?.add(createBookingNote(entity.booking!!, note.type, note.text))
      }
    }*/

    return BookingDto(booking)
  }

  fun cancel(reference: String, cancelOutcome: OutcomeDto): BookingDto {
    val entity = reservationRepository.findByReference(reference) ?: throw ReservationNotFoundException("Reservation $reference not found")
    val booking: Booking = entity.booking ?: throw BookingNotFoundException("Booking for reservation $reference not found")

    if (booking.outcomeStatus != null)
      throw BookingFoundException("Booking for reservation $reference already has an outcome")

    booking.let {
      booking.visitStatus = StatusType.CANCELLED
      booking.outcomeStatus = cancelOutcome.outcomeStatus

      cancelOutcome.text?.let {
        booking.visitNotes.add(
          createBookingNote(
            booking,
            NoteType.VISIT_OUTCOMES,
            cancelOutcome.text
          )
        )
      }
      reservationRepository.saveAndFlush(entity)
    }

    return BookingDto(booking)
  }

  private fun createBookingContact(booking: Booking, name: String, telephone: String): Contact {
    return Contact(
      bookingId = booking.id,
      name = name,
      telephone = telephone,
      booking = booking
    )
  }

  private fun createBookingVisitor(booking: Booking, personId: Long): Visitor {
    return Visitor(
      nomisPersonId = personId,
      bookingId = booking.id,
      booking = booking
    )
  }

  private fun createBookingSupport(booking: Booking, type: String, text: String?): Support {
    return Support(
      type = type,
      bookingId = booking.id,
      text = text,
      booking = booking
    )
  }

  private fun createBookingNote(booking: Booking, type: NoteType, text: String): Note {
    return Note(
      bookingId = booking.id,
      type = type,
      text = text,
      booking = booking
    )
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

class BookingNotFoundException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<BookingNotFoundException> {
  override fun get(): BookingNotFoundException {
    return BookingNotFoundException(message, cause)
  }
}

class BookingFoundException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<BookingFoundException> {
  override fun get(): BookingFoundException {
    return BookingFoundException(message, cause)
  }
}
