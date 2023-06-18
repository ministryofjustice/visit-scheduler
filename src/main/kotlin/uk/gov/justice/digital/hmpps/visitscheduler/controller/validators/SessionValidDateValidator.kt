package uk.gov.justice.digital.hmpps.visitscheduler.controller.validators

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateValidDate
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@MustBeDocumented
@Constraint(validatedBy = [SessionValidDateValidator::class])
annotation class SessionValidDateValidation(
  val message: String = "{.validation.constraints.session.timings.message}",
  val groups: Array<KClass<*>> = [],
  val payload: Array<KClass<out Payload>> = [],
)

class SessionValidDateValidator : ConstraintValidator<SessionValidDateValidation, SessionTemplateValidDate?> {
  override fun isValid(sessionTemplateValidDate: SessionTemplateValidDate?, context: ConstraintValidatorContext?): Boolean {
    sessionTemplateValidDate?.let {
      return sessionTemplateValidDate.validToDate == null ||
        sessionTemplateValidDate.validToDate.isEqual(sessionTemplateValidDate.validFromDate) ||
        sessionTemplateValidDate.validToDate.isAfter(sessionTemplateValidDate.validFromDate)
    }

    return true
  }
}
