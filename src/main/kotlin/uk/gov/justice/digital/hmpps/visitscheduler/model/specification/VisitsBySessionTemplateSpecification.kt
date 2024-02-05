package uk.gov.justice.digital.hmpps.visitscheduler.model.specification

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.jpa.domain.Specification
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitsBySessionTemplateFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import java.time.LocalTime

class VisitsBySessionTemplateSpecification(private val filter: VisitsBySessionTemplateFilter) : Specification<Visit> {
  override fun toPredicate(
    root: Root<Visit>,
    query: CriteriaQuery<*>,
    criteriaBuilder: CriteriaBuilder,
  ): Predicate? {
    val predicates = mutableListOf<Predicate>()

    with(filter) {
      if (!sessionTemplateReference.isNullOrEmpty()) {
        predicates.add(criteriaBuilder.equal(root.get<String>(Visit::sessionTemplateReference.name), sessionTemplateReference))
      } else {
        predicates.add(criteriaBuilder.isNull(root.get<String>(Visit::sessionTemplateReference.name)))
      }

      fromDate.run {
        predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get(Visit::visitStart.name), fromDate.atStartOfDay()))
      }

      toDate.run {
        predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get(Visit::visitStart.name), toDate.atTime(LocalTime.MAX)))
      }

      if (visitStatusList.isNotEmpty()) {
        val visitStatusPath = root.get<String>(Visit::visitStatus.name)
        predicates.add(visitStatusPath.`in`(visitStatusList))
      }

      if (!visitRestrictions.isNullOrEmpty()) {
        val visitRestrictionPath = root.get<String>(Visit::visitRestriction.name)
        predicates.add(visitRestrictionPath.`in`(visitRestrictions))
      }
    }

    return criteriaBuilder.and(*predicates.toTypedArray())
  }
}
