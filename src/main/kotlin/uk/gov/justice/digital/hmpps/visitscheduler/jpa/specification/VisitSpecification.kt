package uk.gov.justice.digital.hmpps.visitscheduler.jpa.specification

import org.springframework.data.jpa.domain.Specification
import uk.gov.justice.digital.hmpps.visitscheduler.data.filter.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitVisitorPk
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Predicate
import javax.persistence.criteria.Root

class VisitSpecification(private val filter: VisitFilter) : Specification<Visit> {
  override fun toPredicate(
    root: Root<Visit>,
    query: CriteriaQuery<*>,
    criteriaBuilder: CriteriaBuilder
  ): Predicate? {
    val predicates = mutableListOf<Predicate>()

    filter.prisonerId?.run {
      predicates.add(criteriaBuilder.equal(root.get<String>(Visit::prisonerId.name), this))
    }

    filter.prisonId?.run {
      predicates.add(criteriaBuilder.equal(root.get<String>(Visit::prisonId.name), this))
    }

    filter.startDateTime?.run {
      predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get(Visit::visitStart.name), this))
    }

    filter.endDateTime?.run {
      predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get(Visit::visitStart.name), this))
    }

    filter.nomisPersonId?.run {
      predicates.add(
        criteriaBuilder.equal(
          root.join<Visit, MutableList<VisitVisitor>>(Visit::visitors.name).get<VisitVisitorPk>(VisitVisitor::id.name)
            .get<Long>(VisitVisitorPk::nomisPersonId.name),
          this
        )
      )
    }

    filter.status?.run {
      predicates.add(criteriaBuilder.equal(root.get<String>(Visit::status.name), this))
    }

    filter.createTimestamp?.run {
      predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get(Visit::createTimestamp.name), this))
    }

    filter.modifyTimestamp?.run {
      predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get(Visit::modifyTimestamp.name), this))
    }

    return criteriaBuilder.and(*predicates.toTypedArray())
  }
}
