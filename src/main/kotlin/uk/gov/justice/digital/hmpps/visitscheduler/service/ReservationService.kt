package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.ReservationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.ReservationRequest
import uk.gov.justice.digital.hmpps.visitscheduler.model.RestrictionType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Reservation
import uk.gov.justice.digital.hmpps.visitscheduler.model.specification.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.specification.VisitSpecification
import uk.gov.justice.digital.hmpps.visitscheduler.repository.ReservationRepository
import java.time.LocalDateTime
import java.util.function.Supplier

private const val MAX_RECORDS = 10000

@Service
@Transactional
class ReservationService(
  private val reservationRepository: ReservationRepository
) {

  fun createReservation(request: ReservationRequest): ReservationDto {
    return ReservationDto(
      reservationRepository.saveAndFlush(
        Reservation(
          visitRoom = request.visitRoom,
          visitStart = request.startTimestamp,
          visitEnd = request.endTimestamp,
          visitRestriction = request.visitRestriction,
        )
      )
    )
  }

  @Transactional(readOnly = true)
  fun getReservation(reference: String): ReservationDto {
    val entity = reservationRepository.findByReference(reference) ?: throw ReservationNotFoundException("Reservation $reference not found")
    return ReservationDto(entity)
  }

  @Transactional(readOnly = true)
  fun getReservation(visitFilter: VisitFilter, pageablePage: Int? = null, pageableSize: Int? = null): Page<ReservationDto> {
    val page: Pageable = PageRequest.of(pageablePage ?: 0, pageableSize ?: MAX_RECORDS, Sort.by(Reservation::visitStart.name).descending())
    return reservationRepository.findAll(VisitSpecification(visitFilter), page).map { ReservationDto(it) }
  }

  fun updateReservation(reference: String, request: ReservationRequest): ReservationDto {
    val entity = reservationRepository.findByReference(reference) ?: throw ReservationNotFoundException("Reservation $reference not found")

    request.visitRoom.let { visitRoom -> entity.visitRoom = visitRoom }
    request.startTimestamp.let { visitStart -> entity.visitStart = visitStart }
    request.endTimestamp.let { visitEnd -> entity.visitEnd = visitEnd }
    request.visitRestriction.let { visitRestriction -> entity.visitRestriction = visitRestriction }

    return ReservationDto(reservationRepository.saveAndFlush(entity))
  }

  fun deleteReservation(reference: String) {
    val entity = reservationRepository.findByReference(reference)

    entity?.let {
      reservationRepository.delete(it)
    } ?: run { log.debug("Reservation $reference not found") }
  }

  fun updateRoom(reference: String, room: String): ReservationDto {
    val entity = reservationRepository.findByReference(reference) ?: throw ReservationNotFoundException("Reservation $reference not found")
    room.let { visitRoom -> entity.visitRoom = visitRoom }
    return ReservationDto(reservationRepository.saveAndFlush(entity))
  }

  fun updateStartTimestamp(reference: String, startTimestamp: LocalDateTime): ReservationDto {
    val entity = reservationRepository.findByReference(reference) ?: throw ReservationNotFoundException("Reservation $reference not found")
    startTimestamp.let { visitStart -> entity.visitStart = visitStart }
    return ReservationDto(reservationRepository.saveAndFlush(entity))
  }

  fun updateEndTimestamp(reference: String, endTimestamp: LocalDateTime): ReservationDto {
    val entity = reservationRepository.findByReference(reference) ?: throw ReservationNotFoundException("Reservation $reference not found")
    endTimestamp.let { visitEnd -> entity.visitEnd = visitEnd }
    return ReservationDto(reservationRepository.saveAndFlush(entity))
  }

  fun updateRestriction(reference: String, restriction: RestrictionType): ReservationDto {
    val entity = reservationRepository.findByReference(reference) ?: throw ReservationNotFoundException("Reservation $reference not found")
    restriction.let { restriction -> entity.visitRestriction = restriction }
    return ReservationDto(reservationRepository.saveAndFlush(entity))
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

class ReservationNotFoundException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<ReservationNotFoundException> {
  override fun get(): ReservationNotFoundException {
    return ReservationNotFoundException(message, cause)
  }
}
