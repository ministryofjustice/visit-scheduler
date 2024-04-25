package uk.gov.justice.digital.hmpps.visitscheduler.exception

import java.util.function.Supplier

class OverCapacityException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<OverCapacityException> {
  override fun get(): OverCapacityException {
    return OverCapacityException(message, cause)
  }
}
