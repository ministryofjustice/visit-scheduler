package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums

@Suppress("unused")
enum class TransitionalLocationTypes {
  RECP,
  COURT,
  TAP,
  ECL,
  CSWAP, ;

  companion object {
    fun contains(value: String? = null): Boolean = entries.any { it.name == value?.let { value.uppercase() } }
  }
}
