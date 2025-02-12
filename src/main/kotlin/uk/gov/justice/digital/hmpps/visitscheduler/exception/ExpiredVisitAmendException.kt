package uk.gov.justice.digital.hmpps.visitscheduler.exception

import jakarta.validation.ValidationException
import java.util.function.Supplier

class ExpiredVisitAmendException(message: String? = null, cause: Throwable? = null) :
  ValidationException(message, cause),
  Supplier<ExpiredVisitAmendException> {

  override fun get(): ExpiredVisitAmendException = ExpiredVisitAmendException(message, cause)
}
