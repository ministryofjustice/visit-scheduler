package uk.gov.justice.digital.hmpps.visitscheduler.exception

import java.util.function.Supplier
import javax.validation.ValidationException

class PrisonerNotInSuppliedPrisonException(message: String? = null, cause: Throwable? = null) :
  ValidationException(message, cause),
  Supplier<PrisonerNotInSuppliedPrisonException> {

  override fun get(): PrisonerNotInSuppliedPrisonException {
    return PrisonerNotInSuppliedPrisonException(message, cause)
  }
}
