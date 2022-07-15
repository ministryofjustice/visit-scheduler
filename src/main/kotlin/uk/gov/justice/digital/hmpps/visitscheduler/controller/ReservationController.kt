package uk.gov.justice.digital.hmpps.visitscheduler.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.BookingDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.BookingRequest
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.NoteDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.ReservationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.ReservationRequest
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.SupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.RestrictionType
import uk.gov.justice.digital.hmpps.visitscheduler.model.StatusType
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.specification.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.service.BookingService
import uk.gov.justice.digital.hmpps.visitscheduler.service.ReservationService
import java.time.LocalDateTime
import javax.validation.Valid

@RestController
@RequestMapping(name = "Visit Resource", path = ["api/v1/visits"], produces = [org.springframework.http.MediaType.APPLICATION_JSON_VALUE])
class ReservationController(
  private val reservationService: ReservationService,
  private val bookingService: BookingService
) {

  @Operation(tags = ["Reservation"])
  @PostMapping("/reservation")
  fun createReservation(
    @RequestBody request: ReservationRequest
  ): ReservationDto {
    return reservationService.createReservation(request)
  }

  @Operation(tags = ["Reservation"])
  @GetMapping("/reservation/{reference}")
  fun getReservation(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String
  ): ReservationDto {
    return reservationService.getReservation(reference.trim())
  }

  @Operation(tags = ["Reservation"])
  @GetMapping("/reservation")
  fun getReservationFilteredPaged(
    @RequestParam(value = "prisonerId", required = false)
    @Parameter(
      description = "Filter results by prisoner id",
      example = "A12345DC"
    ) prisonerId: String?,
    @RequestParam(value = "prisonId", required = false)
    @Parameter(
      description = "Filter results by prison id",
      example = "MDI"
    ) prisonId: String?,
    @RequestParam(value = "startTimestamp", required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Parameter(
      description = "Filter results by visits that start on or after the given timestamp",
      example = "2021-11-03T09:00:00"
    ) startTimestamp: LocalDateTime?,
    @RequestParam(value = "endTimestamp", required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Parameter(
      description = "Filter results by visits that start on or before the given timestamp",
      example = "2021-11-03T09:00:00"
    ) endTimestamp: LocalDateTime?,
    @RequestParam(value = "nomisPersonId", required = false)
    @Parameter(
      description = "Filter results by visitor (contact id)",
      example = "12322"
    ) nomisPersonId: Long?,
    @RequestParam(value = "visitStatus", required = false)
    @Parameter(
      description = "Filter results by visit status",
      example = "BOOKED"
    ) visitStatus: StatusType?,
    @RequestParam(value = "page", required = true)
    @Parameter(
      description = "Pagination page number, starting at zero",
      example = "0"
    ) page: Int?,
    @RequestParam(value = "size", required = true)
    @Parameter(
      description = "Pagination size per page",
      example = "50"
    ) size: Int?
  ): Page<ReservationDto> {
    return reservationService.getReservation(
      VisitFilter(
        prisonerId = prisonerId?.trim(),
        prisonId = prisonId?.trim(),
        startDateTime = startTimestamp,
        endDateTime = endTimestamp,
        nomisPersonId = nomisPersonId,
        visitStatus = visitStatus
      ),
      page,
      size
    )
  }

  @Operation(tags = ["Reservation"], summary = "DEV: update reservation", hidden = true)
  @PutMapping("/reservation/{reference}")
  fun updateReservation(
    @RequestBody request: ReservationRequest,
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String
  ): ReservationDto {
    return reservationService.updateReservation(reference.trim(), request)
  }

  @Operation(tags = ["Reservation"], summary = "DEV: delete reservation", hidden = true)
  @DeleteMapping("/reservation/{reference}")
  fun deleteReservation(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String
  ) {
    reservationService.deleteReservation(reference.trim())
  }

  @Operation(tags = ["Reservation", "Amend"])
  @PatchMapping("/reservation/{reference}/room")
  fun updateReservationRoom(
    @RequestBody room: String,
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String
  ): ReservationDto {
    return reservationService.updateRoom(reference.trim(), room.trim())
  }

  @Operation(tags = ["Reservation", "Amend"])
  @PatchMapping("/reservation/{reference}/start")
  fun updateReservationStartTimestamp(
    @RequestBody startTimestamp: LocalDateTime,
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String
  ): ReservationDto {
    return reservationService.updateStartTimestamp(reference.trim(), startTimestamp)
  }

  @Operation(tags = ["Reservation", "Amend"])
  @PatchMapping("/reservation/{reference}/end")
  fun updateReservationEndTimestamp(
    @RequestBody endTimestamp: LocalDateTime,
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String
  ): ReservationDto {
    return reservationService.updateEndTimestamp(reference.trim(), endTimestamp)
  }

  @Operation(tags = ["Reservation", "Amend"])
  @PatchMapping("/reservation/{reference}/restriction")
  fun updateBookingRestriction(
    @RequestBody restriction: RestrictionType,
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String
  ): ReservationDto {
    return reservationService.updateRestriction(reference.trim(), restriction)
  }

  @Operation(tags = ["Booking"])
  @PostMapping("/reservation/{reference}/booking")
  fun createBooking(
    @RequestBody request: BookingRequest,
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String
  ): BookingDto {
    return bookingService.createBooking(reference.trim(), request)
    // domain event BOOKED
  }

  @Operation(tags = ["Booking"])
  @GetMapping("/reservation/{reference}/booking")
  fun getBooking(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String
  ): BookingDto {
    return bookingService.getBooking(reference.trim())
  }

  @Operation(tags = ["Booking"], summary = "DEV: update booking", hidden = true)
  @PutMapping("/reservation/{reference}/booking")
  fun updateBooking(
    @RequestBody request: BookingRequest,
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String
  ): BookingDto {
    return bookingService.updateBooking(reference.trim(), request)
  }

  @Operation(tags = ["Booking"], summary = "DEV: delete booking", hidden = true)
  @DeleteMapping("/reservation/{reference}/booking")
  fun deleteBooking(
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String
  ) {
    bookingService.deleteBooking(reference.trim())
  }

  @Operation(tags = ["Booking", "Amend"])
  @PatchMapping("/reservation/{reference}/booking/prisoner")
  fun updateBookingPrisoner(
    @RequestBody prisonerId: String,
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String
  ): BookingDto {
    return bookingService.updatePrisoner(reference.trim(), prisonerId.trim())
  }

  @Operation(tags = ["Booking", "Amend"])
  @PatchMapping("/reservation/{reference}/booking/prison")
  fun updateBookingPrison(
    @RequestBody prisonId: String,
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String
  ): BookingDto {
    return bookingService.updatePrison(reference.trim(), prisonId.trim())
  }

  @Operation(tags = ["Booking", "Amend"])
  @PatchMapping("/reservation/{reference}/booking/type")
  fun updateBookingType(
    @RequestBody type: VisitType,
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String
  ): BookingDto {
    return bookingService.updateType(reference.trim(), type)
  }

  @Operation(tags = ["Booking", "Amend"])
  @PatchMapping("/reservation/{reference}/booking/status")
  fun updateBookingStatus(
    @RequestBody status: StatusType,
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String
  ): BookingDto {
    return bookingService.updateStatus(reference.trim(), status)
  }

  @Operation(tags = ["Booking", "Amend"])
  @PatchMapping("/reservation/{reference}/booking/contact")
  fun updateBookingContact(
    @RequestBody contact: ContactDto,
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String
  ): BookingDto {
    return bookingService.updateContact(reference.trim(), contact)
  }

  @Operation(tags = ["Booking", "Amend"])
  @PatchMapping("/reservation/{reference}/booking/visitors")
  fun updateBookingVisitors(
    @RequestBody visitors: Set<VisitorDto>,
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String
  ): BookingDto {
    return bookingService.updateVisitors(reference.trim(), visitors)
  }

  @Operation(tags = ["Booking", "Amend"])
  @PatchMapping("/reservation/{reference}/booking/support")
  fun updateBookingSupport(
    @RequestBody support: Set<SupportDto>,
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String
  ): BookingDto {
    return bookingService.updateSupport(reference.trim(), support)
  }

  @Operation(tags = ["Booking", "Amend"])
  @PatchMapping("/reservation/{reference}/booking/notes")
  fun updateBookingNotes(
    @RequestBody notes: Set<NoteDto>,
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String
  ): BookingDto {
    return bookingService.updateNotes(reference.trim(), notes)
  }

  @Operation(tags = ["Booking", "Cancel"])
  @PatchMapping("/reservation/{reference}/booking/cancel")
  fun cancelBooking(
    @RequestBody @Valid cancelOutcome: OutcomeDto,
    @Schema(description = "reference", example = "v9-d7-ed-7u", required = true)
    @PathVariable reference: String
  ): BookingDto {
    return bookingService.cancel(reference.trim(), cancelOutcome)
    // domain event CANCELLED
  }
}
