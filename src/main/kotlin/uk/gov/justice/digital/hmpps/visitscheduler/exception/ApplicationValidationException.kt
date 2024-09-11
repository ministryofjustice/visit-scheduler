package uk.gov.justice.digital.hmpps.visitscheduler.exception

import jakarta.validation.ValidationException
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationValidationErrorCodes
import java.util.function.Supplier

class ApplicationValidationException(val errorCodes: Array<ApplicationValidationErrorCodes>) :
  ValidationException("Failed to validate application"),
  Supplier<ApplicationValidationException> {
  override fun get(): ApplicationValidationException {
    return ApplicationValidationException(errorCodes)
  }

  constructor(errorCode: ApplicationValidationErrorCodes) : this(arrayOf(errorCode))
}
