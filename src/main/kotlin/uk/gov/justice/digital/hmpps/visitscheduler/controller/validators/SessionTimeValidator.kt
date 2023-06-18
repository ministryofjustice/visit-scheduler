package uk.gov.justice.digital.hmpps.visitscheduler.controller.validators

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateTime
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@MustBeDocumented
@Constraint(validatedBy = [SessionTimeValidator::class])
annotation class SessionTimeValidation(
  val message: String = "{.validation.constraints.session.timings.message}",
  val groups: Array<KClass<*>> = [],
  val payload: Array<KClass<out Payload>> = [],
)

class SessionTimeValidator : ConstraintValidator<SessionTimeValidation, SessionTemplateTime?> {
  override fun isValid(sessionTemplateTime: SessionTemplateTime?, context: ConstraintValidatorContext?): Boolean {
    sessionTemplateTime?.let {
      return sessionTemplateTime.endTime > sessionTemplateTime.startTime
    }

    return true
  }
}
