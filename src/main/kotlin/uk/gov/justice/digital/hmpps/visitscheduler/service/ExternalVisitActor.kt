package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.ValidationException
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType

internal fun validateExternalVisitActionedBy(actionedBy: String?, userType: UserType) {
  if ((userType == UserType.STAFF || userType == UserType.PUBLIC) && actionedBy.isNullOrBlank()) {
    throw ValidationException("actionedBy must be provided for STAFF or PUBLIC users")
  }
}

internal fun resolveExternalVisitActionedBy(actionedBy: String?, userType: UserType, prisonerId: String): String? {
  val identity = when (userType) {
    UserType.PUBLIC -> return actionedBy
    UserType.SYSTEM -> return null
    UserType.STAFF -> requireNotNull(actionedBy)
    UserType.PRISONER -> prisonerId
  }
  if (identity.length > 60) throw ValidationException("Audit username must not exceed 60 characters")
  return identity
}
