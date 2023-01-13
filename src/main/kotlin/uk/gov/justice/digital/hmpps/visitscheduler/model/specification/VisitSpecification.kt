package uk.gov.justice.digital.hmpps.visitscheduler.model.specification

import org.springframework.data.jpa.domain.Specification
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
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

    with(filter) {
      prisonerId?.run {
        predicates.add(criteriaBuilder.equal(root.get<String>(Visit::prisonerId.name), prisonerId))
      }

      prisonCode?.run {
        val innerJoinFromVisitToVisitors =
          root.join<Visit, MutableList<Prison>>(Visit::prison.name).get<VisitVisitor>(
            Prison::code.name
          )
        predicates.add(criteriaBuilder.equal(innerJoinFromVisitToVisitors, prisonCode))
      }

      startDateTime?.run {
        predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get(Visit::visitStart.name), startDateTime))
      }

      endDateTime?.run {
        predicates.add(criteriaBuilder.lessThan(root.get(Visit::visitStart.name), endDateTime))
      }

      visitorId?.run {
        val innerJoinFromVisitToVisitors =
          root.join<Visit, MutableList<VisitVisitor>>(Visit::visitors.name).get<VisitVisitor>(
            VisitVisitor::nomisPersonId.name
          )
        predicates.add(criteriaBuilder.equal(innerJoinFromVisitToVisitors, visitorId))
      }

      if (visitStatusList.isNotEmpty()) {
        val visitStatusPath = root.get<String>(Visit::visitStatus.name)
        predicates.add(visitStatusPath.`in`(visitStatusList))
      }

      val outcomeStatusPath = root.get<String>(Visit::outcomeStatus.name)
      predicates.add(
        criteriaBuilder.and(
          criteriaBuilder.or(
            criteriaBuilder.isNull(outcomeStatusPath),
            criteriaBuilder.notEqual(outcomeStatusPath, OutcomeStatus.SUPERSEDED_CANCELLATION)
          ),
        )
      )
    }
    return criteriaBuilder.and(*predicates.toTypedArray())
  }
}
