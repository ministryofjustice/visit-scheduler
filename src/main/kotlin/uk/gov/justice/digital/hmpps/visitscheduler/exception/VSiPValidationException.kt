package uk.gov.justice.digital.hmpps.visitscheduler.exception

import java.util.function.Supplier

class VSiPValidationException(message: String? = null) :
  RuntimeException(message),
  Supplier<VSiPValidationException> {
  override fun get(): VSiPValidationException {
    return VSiPValidationException(message)
  }
}
