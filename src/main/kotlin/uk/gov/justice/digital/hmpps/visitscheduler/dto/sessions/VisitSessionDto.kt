package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.model.SessionConflict
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import java.time.LocalDateTime

@Schema(description = "OldVisit Session")
data class VisitSessionDto(

  @Schema(description = "Session Template Reference", example = "v9d.7ed.7u", required = true)
  @field:NotBlank
  val sessionTemplateReference: String,

  @Schema(description = "OldVisit Room", example = "Visits Main Hall", required = true)
  @field:NotBlank
  val visitRoom: String,

  @Schema(description = "The type of visits taking place within this session", example = "SOCIAL", required = true)
  @field:NotNull
  val visitType: VisitType,

  @JsonProperty("prisonId")
  @Schema(description = "The prison id", example = "LEI", required = true)
  @field:NotBlank
  val prisonCode: String,

  @Schema(
    description = "The number of concurrent visits which may take place within this session",
    example = "1",
    required = true,
  )
  @field:NotNull
  val openVisitCapacity: Int,

  @Schema(
    description = "The count of open visit bookings already reserved or booked for this session",
    example = "1",
    required = false,
  )
  var openVisitBookedCount: Int? = 0,

  @Schema(
    description = "The number of closed visits which may take place within this session",
    example = "1",
    required = true,
  )
  @field:NotNull
  val closedVisitCapacity: Int,

  @Schema(
    description = "The count of closed visit bookings already reserved or booked for this session",
    example = "1",
    required = false,
  )
  var closedVisitBookedCount: Int? = 0,

  @Schema(description = "The start timestamp for this visit session", example = "2020-11-01T12:00:00", required = true)
  @field:NotNull
  val startTimestamp: LocalDateTime,

  @Schema(description = "The end timestamp for this visit session", example = "2020-11-01T14:30:00", required = true)
  @field:NotNull
  val endTimestamp: LocalDateTime,

  @Schema(description = "Session conflicts", required = false)
  val sessionConflicts: MutableSet<@Valid SessionConflict>? = mutableSetOf(),
)
