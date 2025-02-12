package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums

import java.lang.IllegalArgumentException

@Suppress("unused")
enum class SessionRestriction(
  val description: String,
) {
  OPEN("Open"),
  CLOSED("Closed"),
  ;

  companion object {
    fun get(restriction: VisitRestriction): SessionRestriction = when (restriction) {
      VisitRestriction.OPEN -> OPEN
      VisitRestriction.CLOSED -> CLOSED
      VisitRestriction.UNKNOWN -> throw IllegalArgumentException("UNKNOWN is not allowed for SessionRestriction")
    }
  }

  fun isSame(restriction: VisitRestriction): Boolean = this.name == restriction.name

  fun getVisitRestriction(): VisitRestriction = when (this) {
    OPEN -> VisitRestriction.OPEN
    CLOSED -> VisitRestriction.CLOSED
  }
}
