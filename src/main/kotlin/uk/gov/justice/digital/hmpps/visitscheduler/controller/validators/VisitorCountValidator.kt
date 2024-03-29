package uk.gov.justice.digital.hmpps.visitscheduler.controller.validators

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@MustBeDocumented
@Constraint(validatedBy = [VisitorCountValidator::class])
annotation class VisitorCountValidation(
  val message: String = "{javax.validation.constraints.visitor.count.message}",
  val groups: Array<KClass<*>> = [],
  val payload: Array<KClass<out Payload>> = [],
  val maximumVisitorsAllowed: Short = 10,
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
