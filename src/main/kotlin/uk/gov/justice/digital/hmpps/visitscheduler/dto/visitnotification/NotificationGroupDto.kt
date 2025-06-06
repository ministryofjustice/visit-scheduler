package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType

@Deprecated("no longer needed")
class NotificationGroupDto(
  @Schema(description = "notification group Reference", example = "v9*d7*ed*7u", required = true)
  @field:NotBlank
  val reference: String,
  @Schema(description = "notification event type", example = "NON_ASSOCIATION_EVENT", required = true)
  @field:NotNull
  val type: NotificationEventType,
  @Schema(description = "List of details of affected visits", required = true)
  @field:NotEmpty
  val affectedVisits: List<PrisonerVisitsNotificationDto>,
)
