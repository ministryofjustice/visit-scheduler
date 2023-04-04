package uk.gov.justice.digital.hmpps.visitscheduler.model

@Suppress("unused")
enum class TransitionalLocationTypes {
  RECP, COURT, TAP, ECL, CSWAP;

  companion object {
    fun contains(value: String): Boolean {
      return values().any { it.name == value }
    }
  }
}
