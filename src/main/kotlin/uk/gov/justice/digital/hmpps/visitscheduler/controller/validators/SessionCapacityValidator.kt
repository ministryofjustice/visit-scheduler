package uk.gov.justice.digital.hmpps.visitscheduler.controller.validators

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateCapacity
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@MustBeDocumented
@Constraint(validatedBy = [SessionCapacityValidator::class])
annotation class SessionCapacityValidation(
  val message: String = "{javax.validation.constraints.session.capacity.message}",
  val groups: Array<KClass<*>> = [],
  val payload: Array<KClass<out Payload>> = [],
)

class SessionCapacityValidator : ConstraintValidator<SessionCapacityValidation, SessionTemplateCapacity?> {
  override fun isValid(sessionTemplateCapacity: SessionTemplateCapacity?, context: ConstraintValidatorContext?): Boolean {
    // open or closed capacity needs to be greater than 0
    sessionTemplateCapacity?.let {
      return (sessionTemplateCapacity.openCapacity > 0 || sessionTemplateCapacity.closedCapacity > 0)
    }

    return true
  }
}
