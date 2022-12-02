package uk.gov.justice.digital.hmpps.visitscheduler.exception

import java.util.function.Supplier
import javax.validation.ValidationException

class ExpiredVisitAmendException(message: String? = null, cause: Throwable? = null) :
  ValidationException(message, cause),
  Supplier<ExpiredVisitAmendException> {

  override fun get(): ExpiredVisitAmendException {
    return ExpiredVisitAmendException(message, cause)
  }
}
