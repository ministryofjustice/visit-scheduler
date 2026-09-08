package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums

@Suppress("unused")
enum class PublicSessionConflict {
  AGE_RESTRICTION,
  ;

  fun toSessionConflict(): SessionConflict? = when (this) {
    AGE_RESTRICTION -> SessionConflict.AGE_RESTRICTION
  }
}
