package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "Visit Session")
data class VisitSession(

  @Schema(description = "session id", example = "123", required = true)
  val sessionTemplateId: Long,

  @Schema(description = "The Name of the visit room in which this visit session takes place", example = "Visit room 1", required = true)
  val visitRoomName: String,

  @Schema(description = "The type of visits taking place within this session - code", example = "STANDARD_SOCIAL", required = true)
  val visitType: String,

  @Schema(description = "The type of visits taking place within this session - description", example = "Standard social", required = true)
  val visitTypeDescription: String,

  @Schema(description = "The prison id", example = "LEI", required = true)
  val prisonId: String,

  @Schema(description = "Description of any session restrictions", example = "A wing only")
  val restrictions: String?,

  @Schema(description = "The number of concurrent visits which may take place within this session", example = "1", required = true)
  val openVisitCapacity: Int,

  @Schema(description = "The count of open visit bookings already reserved or booked for this session", example = "1", required = true)
  val openVisitBookedCount: Int,

  @Schema(description = "The number of closed visits which may take place within this session", example = "1", required = true)
  val closedVisitCapacity: Int,

  @Schema(description = "The count of closed visit bookings already reserved or booked for this session", example = "1", required = true)
  val closedVisitBookedCount: Int,

  @Schema(description = "The start timestamp for this visit session", example = "1", required = true)
  val startTimestamp: LocalDateTime,

  @Schema(description = "The end timestamp for this visit session", example = "1", required = true)
  val endTimestamp: LocalDateTime,

)
