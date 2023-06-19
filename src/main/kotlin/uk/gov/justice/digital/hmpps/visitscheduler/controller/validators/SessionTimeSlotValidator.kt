package uk.gov.justice.digital.hmpps.visitscheduler.controller.validators

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@MustBeDocumented
@Constraint(validatedBy = [SessionTimeSlotValidator::class])
annotation class SessionTimeSlotValidation(
  val message: String = "{javax.validation.constraints.session.timings.message}",
  val groups: Array<KClass<*>> = [],
  val payload: Array<KClass<out Payload>> = [],
)

class SessionTimeSlotValidator : ConstraintValidator<SessionTimeSlotValidation, SessionTimeSlotDto?> {
  override fun isValid(sessionTimeSlot: SessionTimeSlotDto?, context: ConstraintValidatorContext?): Boolean {
    sessionTimeSlot?.let {
      return sessionTimeSlot.endTime > sessionTimeSlot.startTime
    }

    return true
  }
}
