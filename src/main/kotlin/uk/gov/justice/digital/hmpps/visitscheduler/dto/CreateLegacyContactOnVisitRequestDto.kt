package uk.gov.justice.digital.hmpps.visitscheduler.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.validation.NullableNotBlank

data class CreateLegacyContactOnVisitRequestDto(@JsonIgnore val _name: String? = UNKNOWN, @JsonIgnore val _telephone: String? = UNKNOWN) {
  @Schema(description = "Contact Name", example = "John Smith", defaultValue = UNKNOWN, required = false)
  @NullableNotBlank
  var name: String? = _name
    set(value) {
      field = value ?: UNKNOWN
    }

  @Schema(description = "Contact Phone Number", example = "01234 567890", defaultValue = UNKNOWN, required = false)
  @NullableNotBlank
  var telephone: String? = _telephone
    set(value) {
      field = value ?: UNKNOWN
    }

  companion object {
    private const val UNKNOWN = "UNKNOWN"
  }
}
