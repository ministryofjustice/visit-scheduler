package uk.gov.justice.digital.hmpps.visitscheduler.controller.validators

import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import javax.validation.Constraint
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext
import javax.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@MustBeDocumented
@Constraint(validatedBy = [VisitorContactValidator::class])
annotation class VisitorContactValidation(
  val message: String = "{javax.validation.constraints.visitor.contact.message}",
  val groups: Array<KClass<*>> = [],
  val payload: Array<KClass<out Payload>> = []
)

class VisitorContactValidator : ConstraintValidator<VisitorContactValidation, Set<VisitorDto>> {

  override fun isValid(childList: Set<VisitorDto>?, context: ConstraintValidatorContext?): Boolean {
    var visitContactCount = 0
    childList?.let { list ->
      list.forEach {
        it.visitContact?.let { visitContact ->
          if (visitContact) visitContactCount++
        }
      }
    }
    // Cannot have more than one visit contact
    return visitContactCount <= 1
  }
}
