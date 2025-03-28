package uk.gov.justice.digital.hmpps.visitscheduler.exception

import java.util.function.Supplier

class PrisonNotFoundException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<PrisonNotFoundException> {
  override fun get(): PrisonNotFoundException = PrisonNotFoundException(message, cause)
}
