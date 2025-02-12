package uk.gov.justice.digital.hmpps.visitscheduler.exception

import java.util.function.Supplier

class VisitNotFoundException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<VisitNotFoundException> {
  override fun get(): VisitNotFoundException = VisitNotFoundException(message, cause)
}
