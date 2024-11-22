package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

@Schema(description = "Visit")
data class SnsDomainEventPublishDto(
  @Schema(description = "Visit Reference", example = "v9-d7-ed-7u", required = true)
  val reference: String,
  @Schema(description = "The visit created date and time", example = "2018-12-01T13:45:00", required = true)
  @field:NotNull
  val createdTimestamp: LocalDateTime,
  @Schema(description = "The visit modified date and time", example = "2018-12-01T13:45:00", required = true)
  @field:NotNull
  val modifiedTimestamp: LocalDateTime,
  @Schema(description = "Prisoner Id", example = "AF34567G", required = true)
  val prisonerId: String,
  @Schema(description = "Event audit id", example = "12345", required = true)
  val eventAuditId: Long,

)
