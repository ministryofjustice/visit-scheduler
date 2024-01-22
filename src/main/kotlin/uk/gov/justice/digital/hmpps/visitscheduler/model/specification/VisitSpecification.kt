package uk.gov.justice.digital.hmpps.visitscheduler.model.specification

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.jpa.domain.Specification
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.OldVisit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor

class VisitSpecification(private val filter: VisitFilter) : Specification<OldVisit> {
  override fun toPredicate(
    root: Root<OldVisit>,
    query: CriteriaQuery<*>,
    criteriaBuilder: CriteriaBuilder,
  ): Predicate? {
    val predicates = mutableListOf<Predicate>()

    with(filter) {
      prisonerId?.run {
        predicates.add(criteriaBuilder.equal(root.get<String>(OldVisit::prisonerId.name), prisonerId))
      }

      prisonCode?.run {
        val innerJoinFromVisitToVisitors =
          root.join<OldVisit, MutableList<Prison>>(OldVisit::prison.name).get<VisitVisitor>(
            Prison::code.name,
          )
        predicates.add(criteriaBuilder.equal(innerJoinFromVisitToVisitors, prisonCode))
      }

      startDateTime?.run {
        predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get(OldVisit::visitStart.name), startDateTime))
      }

      endDateTime?.run {
        predicates.add(criteriaBuilder.lessThan(root.get(OldVisit::visitStart.name), endDateTime))
      }

      visitorId?.run {
        val innerJoinFromVisitToVisitors =
          root.join<OldVisit, MutableList<VisitVisitor>>(OldVisit::visitors.name).get<VisitVisitor>(
            VisitVisitor::nomisPersonId.name,
          )
        predicates.add(criteriaBuilder.equal(innerJoinFromVisitToVisitors, visitorId))
      }

      if (visitStatusList.isNotEmpty()) {
        val visitStatusPath = root.get<String>(OldVisit::visitStatus.name)
        predicates.add(visitStatusPath.`in`(visitStatusList))
      }

      val outcomeStatusPath = root.get<String>(OldVisit::outcomeStatus.name)
      predicates.add(
        criteriaBuilder.and(
          criteriaBuilder.or(
            criteriaBuilder.isNull(outcomeStatusPath),
            criteriaBuilder.notEqual(outcomeStatusPath, OutcomeStatus.SUPERSEDED_CANCELLATION),
          ),
        ),
      )
    }
    return criteriaBuilder.and(*predicates.toTypedArray())
  }
}
