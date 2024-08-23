package uk.gov.justice.digital.hmpps.visitscheduler.exception

import jakarta.validation.ValidationException
import java.util.function.Supplier

class MigrateVisitInFutureException(message: String? = null, cause: Throwable? = null) :
  ValidationException(message, cause),
  Supplier<MigrateVisitInFutureException> {

  override fun get(): MigrateVisitInFutureException {
    return MigrateVisitInFutureException(message, cause)
  }
}
