package uk.gov.justice.digital.hmpps.visitscheduler.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@MustBeDocumented
@Constraint(validatedBy = [NullableNotEmptyValidator::class])
annotation class NullableNotEmpty(
  val message: String = "{javax.validation.constraints.NotEmpty.message}",
  val groups: Array<KClass<*>> = [],
  val payload: Array<KClass<out Payload>> = [],
)

class NullableNotEmptyValidator : ConstraintValidator<NullableNotEmpty, Collection<Any>> {
  override fun isValid(value: Collection<Any>?, context: ConstraintValidatorContext?): Boolean {
    if (value == null) return true
    return value.isNotEmpty()
  }
}
