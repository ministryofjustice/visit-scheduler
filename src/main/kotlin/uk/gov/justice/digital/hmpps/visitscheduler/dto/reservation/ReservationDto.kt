package uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.RestrictionType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Reservation
import java.time.LocalDateTime
import javax.validation.constraints.NotBlank

@Schema(description = "Reservation")
data class ReservationDto(
  @Schema(description = "Visit Reference", example = "v9-d7-ed-7u", required = true)
  val reference: String,
  @Schema(description = "Visit Room", example = "A1 L3", required = true)
  val visitRoom: String,
  @Schema(description = "The date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @field:NotBlank
  val startTimestamp: LocalDateTime,
  @Schema(description = "The finishing date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @field:NotBlank
  val endTimestamp: LocalDateTime,
  @Schema(description = "Visit Restriction", example = "OPEN", required = true)
  val visitRestriction: RestrictionType,
  @Schema(description = "The visit created date and time", example = "2018-12-01T13:45:00", required = true)
  @field:NotBlank
  val createdTimestamp: LocalDateTime,
  @Schema(description = "The visit modified date and time", example = "2018-12-01T13:45:00", required = true)
  @field:NotBlank
  val modifiedTimestamp: LocalDateTime,

  @Schema(description = "Booking", required = false)
  val booking: BookingDto? = null,
) {
  constructor(entity: Reservation) : this(
    reference = entity.reference,
    visitRoom = entity.visitRoom,
    startTimestamp = entity.visitStart,
    endTimestamp = entity.visitEnd,
    visitRestriction = entity.visitRestriction,
    createdTimestamp = entity.createTimestamp!!,
    modifiedTimestamp = entity.modifyTimestamp!!,
    booking = entity.booking?.let { BookingDto(it) }
  )
}
