package uk.gov.justice.digital.hmpps.visitscheduler.service

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.migration.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.NoteType
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Booking
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Contact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.LegacyData
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Note
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Reservation
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visitor
import uk.gov.justice.digital.hmpps.visitscheduler.repository.LegacyDataRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.ReservationRepository

@Service
@Transactional
class MigrateVisitService(
  private val legacyDataRepository: LegacyDataRepository,
  private val reservationRepository: ReservationRepository,
  private val telemetryClient: TelemetryClient,
) {

  fun migrateVisit(migrateVisitRequest: MigrateVisitRequestDto): String {

    val entity = reservationRepository.saveAndFlush(
      Reservation(
        visitRoom = migrateVisitRequest.visitRoom,
        visitStart = migrateVisitRequest.startTimestamp,
        visitEnd = migrateVisitRequest.endTimestamp,
        visitRestriction = migrateVisitRequest.visitRestriction,
      )
    )

    entity.booking = Booking(
      reservationId = entity.id,
      prisonerId = migrateVisitRequest.prisonerId,
      prisonId = migrateVisitRequest.prisonId,
      visitType = migrateVisitRequest.visitType,
      visitStatus = migrateVisitRequest.visitStatus,
      outcomeStatus = migrateVisitRequest.outcomeStatus ?: OutcomeStatus.NOT_RECORDED,
      reservation = entity,
    )
    reservationRepository.save(entity)

    migrateVisitRequest.visitContact?.let {
      entity.booking?.visitContact = createBookingContact(entity.booking!!, it.name, it.telephone)
    }

    migrateVisitRequest.visitors?.forEach {
      entity.booking?.visitors?.add(createBookingVisitor(entity.booking!!, it.nomisPersonId))
    }

    migrateVisitRequest.visitNotes?.forEach {
      entity.booking?.visitNotes?.add(createBookingNote(entity.booking!!, it.type, it.text))
    }

    legacyDataRepository.saveAndFlush(
      createLegacyData(
        entity.booking!!,
        migrateVisitRequest.legacyData?.leadVisitorId
      )
    )

    telemetryClient.trackEvent(
      "visit-scheduler-prison-visit-migrated",
      mapOf(
        "reference" to entity.reference,
        "visitRoom" to entity.visitRoom,
        "visitStart" to entity.visitStart.toString(),
        "visitRestriction" to entity.visitRestriction.name,
        "prisonerId" to entity.booking!!.prisonerId,
        "prisonId" to entity.booking!!.prisonId,
        "visitType" to entity.booking!!.visitType.name,
        "visitStatus" to entity.booking!!.visitStatus.name,
        "outcomeStatus" to entity.booking!!.outcomeStatus?.name
      ),
      null
    )

    return entity.reference // dto?
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

  private fun createBookingNote(booking: Booking, type: NoteType, text: String): Note {
    return Note(
      bookingId = booking.id,
      type = type,
      text = text,
      booking = booking
    )
  }

  private fun createLegacyData(booking: Booking, leadPersonId: Long?): LegacyData {
    return LegacyData(
      bookingId = booking.id,
      leadPersonId = leadPersonId
    )
  }
}
