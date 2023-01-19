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
  val maximumVisitorsAllowed: Short = 10
)

class VisitorCountValidator : ConstraintValidator<VisitorCountValidation, Set<VisitorDto>> {

  private var maximumVisitorsAllowed: Short = 0

  override fun initialize(constraintAnnotation: VisitorCountValidation) {
    this.maximumVisitorsAllowed = constraintAnnotation.maximumVisitorsAllowed
  }

  override fun isValid(visitors: Set<VisitorDto>?, context: ConstraintValidatorContext?): Boolean {
    // Cannot have more than max visitors allowed
    val totalVisitors = visitors?.size ?: 0
    return (totalVisitors <= maximumVisitorsAllowed)
  }
}
