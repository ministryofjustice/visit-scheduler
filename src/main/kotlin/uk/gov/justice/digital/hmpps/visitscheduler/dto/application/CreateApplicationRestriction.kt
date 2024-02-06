package uk.gov.justice.digital.hmpps.visitscheduler.dto.application

import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import java.lang.IllegalArgumentException

@Suppress("unused")
enum class CreateApplicationRestriction(
  val description: String,
) {
  OPEN("Open"),
  CLOSED("Closed"),
  ;

  companion object {
    fun get(restriction: VisitRestriction): CreateApplicationRestriction {
      return when (restriction) {
        VisitRestriction.OPEN -> OPEN
        VisitRestriction.CLOSED -> CLOSED
        VisitRestriction.UNKNOWN -> throw IllegalArgumentException("UNKNOWN is not allowed for CreateApplicationRestriction")
      }
    }
  }

  fun isSame(restriction: VisitRestriction): Boolean {
    return this.name == restriction.name
  }

  fun getVisitRestriction(): VisitRestriction {
    return when (this) {
      OPEN -> VisitRestriction.OPEN
      CLOSED -> VisitRestriction.CLOSED
    }
  }
}
