package uk.gov.justice.digital.hmpps.visitscheduler.dto

import com.fasterxml.jackson.annotation.JsonCreator
import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.constraints.NotBlank

data class CreateLegacyContactOnVisitRequestDto(@field:NotBlank val name: String, @field:NotBlank val telephone: String) {
  companion object {
    private const val UNKNOWN = "UNKNOWN"

    @JsonCreator
    @JvmStatic
    fun create(
      @Schema(description = "Contact Name", example = "John Smith", defaultValue = UNKNOWN, required = false)
      name: String? = null,
      @Schema(description = "Contact Phone Number", example = "01234 567890", defaultValue = UNKNOWN, required = false)
      telephone: String? = null
    ) =
      CreateLegacyContactOnVisitRequestDto(name ?: UNKNOWN, telephone ?: UNKNOWN)
  }
}
