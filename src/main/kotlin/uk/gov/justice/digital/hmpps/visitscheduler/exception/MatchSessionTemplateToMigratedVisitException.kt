package uk.gov.justice.digital.hmpps.visitscheduler.exception

import jakarta.validation.ValidationException
import java.util.function.Supplier

class MatchSessionTemplateToMigratedVisitException(message: String? = null, cause: Throwable? = null) :
  ValidationException(message, cause),
  Supplier<MatchSessionTemplateToMigratedVisitException> {

  override fun get(): MatchSessionTemplateToMigratedVisitException = MatchSessionTemplateToMigratedVisitException(message, cause)
}
