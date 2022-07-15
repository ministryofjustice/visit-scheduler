package uk.gov.justice.digital.hmpps.visitscheduler.service

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visit.CreateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visit.UpdateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visit.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.NoteType
import uk.gov.justice.digital.hmpps.visitscheduler.model.StatusType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Booking
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Contact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Note
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Reservation
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Support
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.specification.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.specification.VisitSpecification
import uk.gov.justice.digital.hmpps.visitscheduler.repository.ReservationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SupportTypeRepository
import java.time.format.DateTimeFormatter
import java.util.function.Supplier

@Service
@Transactional
class VisitService(
  private val reservationRepository: ReservationRepository,
  private val supportTypeRepository: SupportTypeRepository,
  private val telemetryClient: TelemetryClient,
) {

  fun createVisit(createVisitRequest: CreateVisitRequestDto): VisitDto {

    val entity = reservationRepository.saveAndFlush(
      Reservation(
        visitRoom = createVisitRequest.visitRoom,
        visitStart = createVisitRequest.startTimestamp,
        visitEnd = createVisitRequest.endTimestamp,
        visitRestriction = createVisitRequest.visitRestriction,
      )
    )

    entity.booking = Booking(
      reservationId = entity.id,
      prisonerId = createVisitRequest.prisonerId,
      prisonId = createVisitRequest.prisonId,
      visitType = createVisitRequest.visitType,
      visitStatus = createVisitRequest.visitStatus,
      reservation = entity,
    )
    reservationRepository.save(entity)

    createVisitRequest.visitContact?.let {
      entity.booking?.visitContact = createBookingContact(entity.booking!!, it.name, it.telephone)
    }

    createVisitRequest.visitors.forEach {
      entity.booking?.visitors?.add(createBookingVisitor(entity.booking!!, it.nomisPersonId))
    }

    createVisitRequest.visitorSupport?.let { supportList ->
      supportList.forEach {
        supportTypeRepository.findByName(it.type) ?: throw SupportNotFoundException("Invalid support ${it.type} not found")
        entity.booking?.support?.add(createBookingSupport(entity.booking!!, it.type, it.text))
      }
    }

    telemetryClient.trackEvent(
      "visit-scheduler-prison-visit-created",
      mapOf(
        "reference" to entity.reference,
        "visitRoom" to entity.visitRoom,
        "visitStart" to entity.visitStart.format(DateTimeFormatter.ISO_DATE_TIME),
        "visitRestriction" to entity.visitRestriction.name,
        "prisonerId" to entity.booking!!.prisonerId,
        "prisonId" to entity.booking!!.prisonId,
        "visitType" to entity.booking!!.visitType.name,
        "visitStatus" to entity.booking!!.visitStatus.name
      ),
      null
    )

    // compatible response
    return VisitDto(entity)
  }

  @Deprecated("See find visits pageable", ReplaceWith("findVisitsByFilterPageableDescending(visitFilter).content"))
  fun findVisitsByFilter(visitFilter: VisitFilter): List<VisitDto> {
    return findVisitsByFilterPageableDescending(visitFilter).content
  }

  @Transactional(readOnly = true)
  fun findVisitsByFilterPageableDescending(visitFilter: VisitFilter, pageablePage: Int? = null, pageableSize: Int? = null): Page<VisitDto> {
    val page: Pageable = PageRequest.of(pageablePage ?: 0, pageableSize ?: MAX_RECORDS, Sort.by(Reservation::visitStart.name).descending())
    return reservationRepository.findAll(VisitSpecification(visitFilter), page).map { VisitDto(it) }
  }

  @Transactional(readOnly = true)
  fun getVisitByReference(reference: String): VisitDto {
    return VisitDto(reservationRepository.findByReference(reference) ?: throw VisitNotFoundException("Visit $reference not found"))
  }

  fun updateVisit(reference: String, updateVisitRequest: UpdateVisitRequestDto): VisitDto {
    val entity = reservationRepository.findByReference(reference) ?: throw VisitNotFoundException("Visit $reference not found")
    entity.booking ?: throw VisitNotFoundException("Visit $reference booking not found")

    updateVisitRequest.visitRoom?.let { visitRoom -> entity.visitRoom = visitRoom }
    updateVisitRequest.startTimestamp?.let { visitStart -> entity.visitStart = visitStart }
    updateVisitRequest.endTimestamp?.let { visitEnd -> entity.visitEnd = visitEnd }

    updateVisitRequest.prisonerId?.let { prisonerId -> entity.booking!!.prisonerId = prisonerId }
    updateVisitRequest.prisonId?.let { prisonId -> entity.booking!!.prisonId = prisonId }
    updateVisitRequest.visitType?.let { visitType -> entity.booking!!.visitType = visitType }
    updateVisitRequest.visitStatus?.let { status -> entity.booking!!.visitStatus = status }
    updateVisitRequest.visitRestriction?.let { visitRestriction -> entity.visitRestriction = visitRestriction }

    updateVisitRequest.visitContact?.let { visitContactUpdate ->
      entity.booking!!.visitContact?.let { visitContact ->
        visitContact.name = visitContactUpdate.name
        visitContact.telephone = visitContactUpdate.telephone
      } ?: run {
        entity.booking!!.visitContact = createBookingContact(entity.booking!!, visitContactUpdate.name, visitContactUpdate.telephone)
      }
    }

    updateVisitRequest.visitors?.let { visitorsUpdate ->
      entity.booking!!.visitors.clear()
      reservationRepository.saveAndFlush(entity)
      visitorsUpdate.distinctBy { it.nomisPersonId }.forEach {
        entity.booking!!.visitors.add(createBookingVisitor(entity.booking!!, it.nomisPersonId))
      }
    }

    updateVisitRequest.visitorSupport?.let { visitSupportUpdate ->
      entity.booking!!.support.clear()
      reservationRepository.saveAndFlush(entity)
      visitSupportUpdate.forEach {
        supportTypeRepository.findByName(it.type) ?: throw SupportNotFoundException("Invalid support ${it.type} not found")
        entity.booking!!.support.add(createBookingSupport(entity.booking!!, it.type, it.text))
      }
    }

    telemetryClient.trackEvent(
      "visit-scheduler-prison-visit-updated",
      listOfNotNull(
        "reference" to reference,
        if (updateVisitRequest.prisonerId != null) "prisonerId" to updateVisitRequest.prisonerId else null,
        if (updateVisitRequest.prisonId != null) "prisonId" to updateVisitRequest.prisonId else null,
        if (updateVisitRequest.visitType != null) "visitType" to updateVisitRequest.visitType.name else null,
        if (updateVisitRequest.visitRoom != null) "visitRoom" to updateVisitRequest.visitRoom else null,
        if (updateVisitRequest.visitRestriction != null) "visitRestriction" to updateVisitRequest.visitRestriction.name else null,
        if (updateVisitRequest.startTimestamp != null) "visitStart" to updateVisitRequest.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME) else null,
        if (updateVisitRequest.visitStatus != null) "visitStatus" to updateVisitRequest.visitStatus.name else null
      ).toMap(),
      null
    )

    return VisitDto(entity)
  }

  fun deleteVisit(reference: String) {
    val visit = reservationRepository.findByReference(reference)

    visit?.let {
      reservationRepository.delete(it)
      telemetryClient.trackEvent(
        "visit-scheduler-prison-visit-deleted",
        mapOf(
          "reference" to reference
        ),
        null
      )
    } ?: run { log.debug("Visit reference $reference not found") }
  }

  fun deleteAllVisits(visits: List<VisitDto>) {
    reservationRepository.deleteAllByReferenceIn(visits.map { it.reference }.toList())

    for (visit in visits) {
      telemetryClient.trackEvent(
        "visit-scheduler-prison-visit-deleted",
        mapOf(
          "reference" to visit.reference
        ),
        null
      )
    }
  }

  fun cancelVisit(reference: String, cancelOutcome: OutcomeDto): VisitDto {
    val entity = reservationRepository.findByReference(reference) ?: throw VisitNotFoundException("Visit $reference not found")

    entity.booking?.let {
      entity.booking!!.visitStatus = StatusType.CANCELLED
      entity.booking!!.outcomeStatus = cancelOutcome.outcomeStatus

      cancelOutcome.text?.let {
        entity.booking!!.visitNotes.add(
          createBookingNote(
            entity.booking!!,
            NoteType.VISIT_OUTCOMES,
            cancelOutcome.text
          )
        )
      }
      reservationRepository.saveAndFlush(entity)

      telemetryClient.trackEvent(
        "visit-scheduler-prison-visit-cancelled",
        mapOf(
          "reference" to reference,
          "visitStatus" to entity.booking!!.visitStatus.name,
          "outcomeStatus" to entity.booking!!.outcomeStatus?.name
        ),
        null
      )
    }

    return VisitDto(entity)
  }

  private fun createBookingNote(booking: Booking, type: NoteType, text: String): Note {
    return Note(
      bookingId = booking.id,
      type = type,
      text = text,
      booking = booking
    )
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

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val MAX_RECORDS = 10000
  }
}

class VisitNotFoundException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<VisitNotFoundException> {
  override fun get(): VisitNotFoundException {
    return VisitNotFoundException(message, cause)
  }
}

class SupportNotFoundException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<SupportNotFoundException> {
  override fun get(): SupportNotFoundException {
    return SupportNotFoundException(message, cause)
  }
}
