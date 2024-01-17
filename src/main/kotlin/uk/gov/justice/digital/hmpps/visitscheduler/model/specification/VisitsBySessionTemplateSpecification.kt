package uk.gov.justice.digital.hmpps.visitscheduler.model.specification

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.jpa.domain.Specification
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitsBySessionTemplateFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.OldVisit
import java.time.LocalTime

class VisitsBySessionTemplateSpecification(private val filter: VisitsBySessionTemplateFilter) : Specification<OldVisit> {
  override fun toPredicate(
    root: Root<OldVisit>,
    query: CriteriaQuery<*>,
    criteriaBuilder: CriteriaBuilder,
  ): Predicate? {
    val predicates = mutableListOf<Predicate>()

    with(filter) {
      sessionTemplateReference.run {
        predicates.add(criteriaBuilder.equal(root.get<String>(OldVisit::sessionTemplateReference.name), sessionTemplateReference))
      }

      fromDate.run {
        predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get(OldVisit::visitStart.name), fromDate.atStartOfDay()))
      }

      toDate.run {
        predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get(OldVisit::visitStart.name), toDate.atTime(LocalTime.MAX)))
      }

      if (visitStatusList.isNotEmpty()) {
        val visitStatusPath = root.get<String>(OldVisit::visitStatus.name)
        predicates.add(visitStatusPath.`in`(visitStatusList))
      }

      if (!visitRestrictions.isNullOrEmpty()) {
        val visitRestrictionPath = root.get<String>(OldVisit::visitRestriction.name)
        predicates.add(visitRestrictionPath.`in`(visitRestrictions))
      }
    }

    return criteriaBuilder.and(*predicates.toTypedArray())
  }
}
