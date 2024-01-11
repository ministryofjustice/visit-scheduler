package uk.gov.justice.digital.hmpps.visitscheduler.exception

import jakarta.validation.ValidationException
import java.util.function.Supplier

class VisitToMigrateException(message: String? = null, cause: Throwable? = null) :
  ValidationException(message, cause),
  Supplier<VisitToMigrateException> {

  override fun get(): VisitToMigrateException {
    return VisitToMigrateException(message, cause)
  }
}
