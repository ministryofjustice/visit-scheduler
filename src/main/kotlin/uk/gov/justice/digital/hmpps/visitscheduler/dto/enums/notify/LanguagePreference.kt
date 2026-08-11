package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.notify

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class LanguagePreference(@get:JsonValue val code: String) {
  EN("en"),
  CY("cy"),
  ;

  companion object {
    @JvmStatic
    @JsonCreator
    fun from(value: String?): LanguagePreference = entries.firstOrNull { it.code.equals(value, ignoreCase = true) }
      ?: throw IllegalArgumentException("Invalid languagePreference: $value")
  }
}
