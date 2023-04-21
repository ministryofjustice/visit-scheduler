package uk.gov.justice.digital.hmpps.visitscheduler.exception

import jakarta.validation.ValidationException
import java.util.function.Supplier

class MigratedVisitCapacityGroupMatchException(message: String? = null, cause: Throwable? = null) :
  ValidationException(message, cause),
  Supplier<MigratedVisitCapacityGroupMatchException> {

  override fun get(): MigratedVisitCapacityGroupMatchException {
    return MigratedVisitCapacityGroupMatchException(message, cause)
  }
}
