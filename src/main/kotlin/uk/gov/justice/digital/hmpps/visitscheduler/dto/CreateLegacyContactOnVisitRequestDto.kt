package uk.gov.justice.digital.hmpps.visitscheduler.dto

import com.fasterxml.jackson.annotation.JsonCreator
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class CreateLegacyContactOnVisitRequestDto(@field:NotBlank val name: String, @field:NotBlank val telephone: String) {
  companion object {
    const val NOTKNOWN_TOKEN = "NOT_KNOWN"

    // Deserialization kotlin data class issue when name and/or telephone  = json type of null defaults do not get set hence below
    // JsonCreator and JvmStatic code
    @JsonCreator
    @JvmStatic
    fun create(
      @Schema(description = "Contact Name", example = "John Smith", defaultValue = NOTKNOWN_TOKEN, required = false)
      name: String? = null,
      @Schema(description = "Contact Phone Number", example = "01234 567890", defaultValue = NOTKNOWN_TOKEN, required = false)
      telephone: String? = null,
    ) =
      CreateLegacyContactOnVisitRequestDto(name ?: NOTKNOWN_TOKEN, telephone ?: NOTKNOWN_TOKEN)
  }
}
