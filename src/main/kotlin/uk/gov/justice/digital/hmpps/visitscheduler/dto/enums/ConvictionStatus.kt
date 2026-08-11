package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums

enum class ConvictionStatus(val value: String) {
  CONVICTED("Convicted"),
  REMAND("Remand"), ;

  companion object {
    fun isRemand(convictionStatus: String?) = (convictionStatus != null && REMAND.value.equals(convictionStatus, ignoreCase = true))
  }
}
