package uk.gov.justice.digital.hmpps.visitscheduler.model

import java.util.*

@Suppress("unused")
enum class TransitionalLocationTypes {
  RECP, COURT, TAP, ECL, CSWAP;

  companion object {
    fun contains(value: String? = null): Boolean {
      return values().any { it.name == value?.let { value.uppercase() } }
    }
  }
}
