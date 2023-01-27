package uk.gov.justice.digital.hmpps.visitscheduler.exception

import java.util.function.Supplier

class NotFoundException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<NotFoundException> {
  override fun get(): NotFoundException {
    return NotFoundException(message, cause)
  }
}
