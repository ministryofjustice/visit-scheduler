package uk.gov.justice.digital.hmpps.visitscheduler.exception

import java.util.function.Supplier

class CapacityNotFoundException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<CapacityNotFoundException> {
  override fun get(): CapacityNotFoundException = CapacityNotFoundException(message, cause)
}
