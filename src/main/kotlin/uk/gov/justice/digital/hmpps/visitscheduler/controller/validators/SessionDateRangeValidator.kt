package uk.gov.justice.digital.hmpps.visitscheduler.controller.validators

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionDateRangeDto
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@MustBeDocumented
@Constraint(validatedBy = [SessionValidDateValidator::class])
annotation class SessionDateRangeValidation(
  val message: String = "{javax.validation.constraints.session.valid.date.message}",
  val groups: Array<KClass<*>> = [],
  val payload: Array<KClass<out Payload>> = [],
)

class SessionValidDateValidator : ConstraintValidator<SessionDateRangeValidation, SessionDateRangeDto?> {
  override fun isValid(sessionDateRange: SessionDateRangeDto?, context: ConstraintValidatorContext?): Boolean {
    sessionDateRange?.let {
      return sessionDateRange.validToDate == null ||
        sessionDateRange.validToDate.isEqual(sessionDateRange.validFromDate) ||
        sessionDateRange.validToDate.isAfter(sessionDateRange.validFromDate)
    }

    return true
  }
}
