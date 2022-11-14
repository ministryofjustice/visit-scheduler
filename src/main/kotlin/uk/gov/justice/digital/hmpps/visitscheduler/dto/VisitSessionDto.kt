package uk.gov.justice.digital.hmpps.visitscheduler.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.SessionConflict
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import java.time.LocalDateTime
import javax.validation.Valid

@Schema(description = "Visit Session")
data class VisitSessionDto(

  @Schema(description = "session id", example = "123", required = true)
  val sessionTemplateId: Long,

  @Schema(
    description = "The Name of the visit room in which this visit session takes place",
    example = "Visit room 1",
    required = true
  )
  val visitRoomName: String,

  @Schema(description = "The type of visits taking place within this session", example = "SOCIAL", required = true)
  val visitType: VisitType,

  @JsonProperty("prisonId")
  @Schema(description = "The prison id", example = "LEI", required = true)
  val prisonCode: String,

  @Schema(
    description = "The number of concurrent visits which may take place within this session",
    example = "1",
    required = true
  )
  val openVisitCapacity: Int,

  @Schema(
    description = "The count of open visit bookings already reserved or booked for this session",
    example = "1",
    required = false
  )
  var openVisitBookedCount: Int? = 0,

  @Schema(
    description = "The number of closed visits which may take place within this session",
    example = "1",
    required = true
  )
  val closedVisitCapacity: Int,

  @Schema(
    description = "The count of closed visit bookings already reserved or booked for this session",
    example = "1",
    required = false
  )
  var closedVisitBookedCount: Int? = 0,

  @Schema(description = "The start timestamp for this visit session", example = "2020-11-01T12:00:00", required = true)
  val startTimestamp: LocalDateTime,

  @Schema(description = "The end timestamp for this visit session", example = "2020-11-01T14:30:00", required = true)
  val endTimestamp: LocalDateTime,

  @Schema(description = "Session conflicts", required = false)
  val sessionConflicts: MutableSet<@Valid SessionConflict>? = mutableSetOf(),
)
