package uk.gov.justice.digital.hmpps.visitscheduler.exception

import java.util.function.Supplier

class SupportNotFoundException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<SupportNotFoundException> {
  override fun get(): SupportNotFoundException = SupportNotFoundException(message, cause)
}
