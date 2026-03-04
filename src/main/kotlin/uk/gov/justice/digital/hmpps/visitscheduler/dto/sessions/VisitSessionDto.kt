package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import java.time.LocalDateTime

@Schema(description = "Visit Session")
data class VisitSessionDto(

  @param:Schema(description = "Session Template Reference", example = "v9d.7ed.7u", required = true)
  @field:NotBlank
  val sessionTemplateReference: String,

  @param:Schema(description = "Visit Room", example = "Visits Main Hall", required = true)
  @field:NotBlank
  val visitRoom: String,

  @param:Schema(description = "The type of visits taking place within this session", example = "SOCIAL", required = true)
  @field:NotNull
  val visitType: VisitType,

  @param:JsonProperty("prisonId")
  @param:Schema(description = "The prison id", example = "LEI", required = true)
  @field:NotBlank
  val prisonCode: String,

  @param:Schema(
    description = "The number of concurrent visits which may take place within this session",
    example = "1",
    required = true,
  )
  @field:NotNull
  val openVisitCapacity: Int,

  @param:Schema(
    description = "The count of open visit bookings already reserved or booked for this session",
    example = "1",
    required = false,
  )
  var openVisitBookedCount: Int? = 0,

  @param:Schema(
    description = "The number of closed visits which may take place within this session",
    example = "1",
    required = true,
  )
  @field:NotNull
  val closedVisitCapacity: Int,

  @param:Schema(
    description = "The count of closed visit bookings already reserved or booked for this session",
    example = "1",
    required = false,
  )
  var closedVisitBookedCount: Int? = 0,

  @param:Schema(description = "The start timestamp for this visit session", example = "2020-11-01T12:00:00", required = true)
  @field:NotNull
  val startTimestamp: LocalDateTime,

  @param:Schema(description = "The end timestamp for this visit session", example = "2020-11-01T14:30:00", required = true)
  @field:NotNull
  val endTimestamp: LocalDateTime,

  @param:Schema(description = "Session conflicts", required = false)
  val sessionConflicts: MutableList<SessionConflictDto> = mutableListOf(),
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as VisitSessionDto

    if (sessionTemplateReference != other.sessionTemplateReference) return false
    if (visitRoom != other.visitRoom) return false
    if (visitType != other.visitType) return false
    if (prisonCode != other.prisonCode) return false
    if (startTimestamp != other.startTimestamp) return false
    if (endTimestamp != other.endTimestamp) return false

    return true
  }

  override fun hashCode(): Int {
    var result = openVisitCapacity
    result = 31 * result + (openVisitBookedCount ?: 0)
    result = 31 * result + closedVisitCapacity
    result = 31 * result + (closedVisitBookedCount ?: 0)
    result = 31 * result + sessionTemplateReference.hashCode()
    result = 31 * result + visitRoom.hashCode()
    result = 31 * result + visitType.hashCode()
    result = 31 * result + prisonCode.hashCode()
    result = 31 * result + startTimestamp.hashCode()
    result = 31 * result + endTimestamp.hashCode()
    return result
  }
}

data class SessionConflictDto(
  @Schema(description = "Session Conflict", example = "NON_ASSOCIATION", required = true)
  @field:NotNull
  val sessionConflict: SessionConflict,

  @Schema(description = "Session Conflict attributes", required = false)
  val additionalAttributes: List<List<AdditionalSessionConflictInfoDto>> = emptyList(),
)

data class AdditionalSessionConflictInfoDto(
  @Schema(description = "Attribute Name", required = true)
  @field:NotBlank
  val attributeName: String,

  @Schema(description = "Attribute value", required = true)
  @field:NotBlank
  val attributeValue: String,
)
