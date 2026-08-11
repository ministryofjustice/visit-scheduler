package uk.gov.justice.digital.hmpps.visitscheduler.dto.alerts

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Alert dto response from alerts API")
data class AlertDto(
  @param:Schema(description = "The unique identifier assigned to the alert", example = "8cdadcf3-b003-4116-9956-c99bd8df6a00")
  val alertUuid: String,

  @param:Schema(description = "True / False based on alert status", example = "false", required = true)
  @param:JsonProperty("isActive")
  val active: Boolean,
)
