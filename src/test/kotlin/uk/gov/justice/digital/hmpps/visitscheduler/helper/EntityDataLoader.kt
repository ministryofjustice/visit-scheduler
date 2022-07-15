package uk.gov.justice.digital.hmpps.visitscheduler.helper

import uk.gov.justice.digital.hmpps.visitscheduler.model.NoteType
import uk.gov.justice.digital.hmpps.visitscheduler.model.RestrictionType
import uk.gov.justice.digital.hmpps.visitscheduler.model.SessionFrequency.DAILY
import uk.gov.justice.digital.hmpps.visitscheduler.model.StatusType
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Booking
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Contact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Note
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Reservation
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Support
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visitor
import uk.gov.justice.digital.hmpps.visitscheduler.repository.ReservationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReservationBuilder(
  private val repository: ReservationRepository,
  private var reservation: Reservation,
) {

  fun save(): Reservation {
    return repository.saveAndFlush(reservation)
  }

  fun withVisitRoom(room: String): ReservationBuilder {
    this.reservation = reservation.copy(visitRoom = room)
    return this
  }

  fun withVisitStart(visitDateTime: LocalDateTime): ReservationBuilder {
    this.reservation = reservation.copy(visitStart = visitDateTime)
    return this
  }

  fun withVisitEnd(visitDateTime: LocalDateTime): ReservationBuilder {
    this.reservation = reservation.copy(visitEnd = visitDateTime)
    return this
  }
}

fun reservationCreator(
  repository: ReservationRepository,
  reservation: Reservation = defaultReservation(),
): ReservationBuilder {
  return ReservationBuilder(repository, reservation)
}

fun reservationDeleter(
  repository: ReservationRepository,
) {
  repository.deleteAll()
  repository.flush()
}

fun defaultReservation(): Reservation {
  return Reservation(
    visitRoom = "3B",
    visitStart = LocalDateTime.of(2021, 10, 23, 10, 30),
    visitEnd = LocalDateTime.of(2021, 10, 23, 11, 30),
    visitRestriction = RestrictionType.OPEN,
  )
}

fun defaultBooking(reservation: Reservation): Booking {
  return Booking(
    reservationId = reservation.id,
    prisonerId = "AF12345G",
    prisonId = "MDI",
    visitType = SOCIAL,
    visitStatus = StatusType.RESERVED,
    reservation = reservation
  )
}

fun createBookingContact(booking: Booking, name: String, telephone: String): Contact {
  return Contact(
    bookingId = booking.id,
    name = name,
    telephone = telephone,
    booking = booking
  )
}

fun createBookingVisitor(booking: Booking, personId: Long): Visitor {
  return Visitor(
    nomisPersonId = personId,
    bookingId = booking.id,
    booking = booking
  )
}

fun createBookingSupport(booking: Booking, type: String, text: String?): Support {
  return Support(
    type = type,
    bookingId = booking.id,
    text = text,
    booking = booking
  )
}

fun createBookingNote(booking: Booking, type: NoteType, text: String): Note {
  return Note(
    bookingId = booking.id,
    type = type,
    text = text,
    booking = booking
  )
}

class SessionTemplateBuilder(
  private val repository: SessionTemplateRepository,
  private var sessionTemplate: SessionTemplate,
) {

  fun save(): SessionTemplate =
    repository.saveAndFlush(sessionTemplate)

  fun withStartTime(startTime: LocalTime): SessionTemplateBuilder {
    this.sessionTemplate = sessionTemplate.copy(startTime = startTime)
    return this
  }

  fun withEndTime(endTime: LocalTime): SessionTemplateBuilder {
    this.sessionTemplate = sessionTemplate.copy(endTime = endTime)
    return this
  }
}

fun sessionTemplateCreator(
  repository: SessionTemplateRepository,
  sessionTemplate: SessionTemplate = defaultSessionTemplate()
): SessionTemplateBuilder {
  return SessionTemplateBuilder(repository, sessionTemplate)
}

fun sessionTemplateDeleter(
  repository: SessionTemplateRepository,
) {
  repository.deleteAll()
  repository.flush()
}

fun defaultSessionTemplate(): SessionTemplate {
  return sessionTemplate(
    prisonId = "MDI",
    startDate = LocalDate.of(2021, 10, 23),
    frequency = DAILY,
    openCapacity = 5,
    closedCapacity = 1,
    visitRoom = "3B",
    visitType = SOCIAL
  )
}
