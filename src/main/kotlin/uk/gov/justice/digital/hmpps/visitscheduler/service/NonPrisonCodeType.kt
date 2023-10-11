package uk.gov.justice.digital.hmpps.visitscheduler.service

enum class NonPrisonCodeType {
  OUT,
  TRN,
  CRT,
  TAP,
  ADM,
  REL,
  ;

  companion object {
    fun isNonPrisonCodeType(prisonCode: String?): Boolean {
      prisonCode?.let {
        return NonPrisonCodeType.entries.firstOrNull { it.name.equals(prisonCode, true) } != null
      }
      return true
    }
  }
}
