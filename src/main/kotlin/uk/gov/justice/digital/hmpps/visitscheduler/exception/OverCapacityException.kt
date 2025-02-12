package uk.gov.justice.digital.hmpps.visitscheduler.exception

import jakarta.validation.ValidationException
import java.util.function.Supplier

class OverCapacityException(message: String? = null, cause: Throwable? = null) :
  ValidationException(message, cause),
  Supplier<OverCapacityException> {
  override fun get(): OverCapacityException = OverCapacityException(message, cause)
}
