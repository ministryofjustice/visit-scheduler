package uk.gov.justice.digital.hmpps.visitscheduler.dto.notify

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Gov Notify Create Notification")
data class NotifyCreateNotificationDto(
  @Schema(description = "The UUID of the notification", required = true)
  val notificationId: String,
  @Schema(description = "The id of the event audit which the notification is linked to", example = "123456", required = true)
  @JsonProperty("reference")
  val eventAuditReference: String,
  @Schema(description = "The timestamp for when the vsip notification service sent the notification to gov notify", required = true)
  val createdAt: LocalDateTime,
  @Schema(description = "The type of the notification", example = "email", required = true)
  val notificationType: String,
  @Schema(description = "The id the template used for the notification", example = "email", required = true)
  val templateId: String,
  @Schema(description = "The version of the template used for the notification", example = "email", required = true)
  val templateVersion: String,
)
