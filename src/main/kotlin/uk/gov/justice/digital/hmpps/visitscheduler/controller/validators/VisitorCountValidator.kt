package uk.gov.justice.digital.hmpps.visitscheduler.controller.validators

import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import javax.validation.Constraint
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext
import javax.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@MustBeDocumented
@Constraint(validatedBy = [VisitorCountValidator::class])
annotation class VisitorCountValidation(
  val message: String = "{javax.validation.constraints.visitor.count.message}",
  val groups: Array<KClass<*>> = [],
  val payload: Array<KClass<out Payload>> = [],
  val maximumVisitorsAllowed: UShort = VisitorCountValidator.MAXIMUM_VISITORS_ALLOWED,
)

class VisitorCountValidator : ConstraintValidator<VisitorCountValidation, Set<VisitorDto>> {
  companion object {
    const val MAXIMUM_VISITORS_ALLOWED: UShort = 10u
  }
  private var maximumVisitorsAllowed: UShort = MAXIMUM_VISITORS_ALLOWED

  override fun initialize(constraintAnnotation: VisitorCountValidation?) {
    constraintAnnotation?.let {
      this.maximumVisitorsAllowed = constraintAnnotation.maximumVisitorsAllowed
    }
  }

  override fun isValid(childList: Set<VisitorDto>?, context: ConstraintValidatorContext?): Boolean {
    // Cannot have more than max visitors allowed
    val total = (childList?.size ?: 0).toUShort()
    return (total <= maximumVisitorsAllowed)
  }
}
