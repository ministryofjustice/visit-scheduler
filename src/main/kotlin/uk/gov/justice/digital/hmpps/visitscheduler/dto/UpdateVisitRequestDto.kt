package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import java.time.LocalDateTime
import javax.validation.Valid

data class UpdateVisitRequestDto(
  @Schema(description = "Prisoner Id", example = "AF34567G", required = false)
  val prisonerId: String? = null,
  @Schema(description = "Prison Id", example = "MDI", required = false)
  val prisonId: String? = null,
  @Schema(description = "Visit Room", example = "A1", required = false)
  val visitRoom: String? = null,
  @Schema(description = "Visit Type", example = "SOCIAL", required = false)
  val visitType: VisitType? = null,
  @Schema(description = "Visit Status", example = "RESERVED", required = false)
  val visitStatus: VisitStatus? = null,
  @Schema(description = "Visit Restriction", example = "OPEN", required = false)
  val visitRestriction: VisitRestriction? = null,
  @Schema(description = "The date and time of the visit", example = "2018-12-01T13:45:00", required = false)
  val startTimestamp: LocalDateTime? = null,
  @Schema(description = "The finishing date and time of the visit", example = "2018-12-01T13:45:00", required = false)
  val endTimestamp: LocalDateTime? = null,
  @Schema(description = "Contact associated with the visit", required = false)
  @field:Valid
  val visitContact: CreateContactOnVisitRequest? = null,
  @Schema(description = "List of visitors associated with the visit", required = false)
  val visitors: List<@Valid CreateVisitorOnVisitRequestDto>? = null,
  @Schema(description = "List of additional support associated with the visit", required = false)
  val visitorSupport: List<@Valid CreateSupportOnVisitRequestDto>? = null,
)

// once set the main contact cannot be changed
