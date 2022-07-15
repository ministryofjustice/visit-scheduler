package uk.gov.justice.digital.hmpps.visitscheduler.model.specification

import org.springframework.data.jpa.domain.Specification
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Booking
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Reservation
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visitor
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.JoinType
import javax.persistence.criteria.Predicate
import javax.persistence.criteria.Root

class VisitSpecification(private val filter: VisitFilter) : Specification<Reservation> {
  override fun toPredicate(
    root: Root<Reservation>,
    query: CriteriaQuery<*>,
    criteriaBuilder: CriteriaBuilder
  ): Predicate? {
    val predicates = mutableListOf<Predicate>()

    filter.prisonerId?.run {
      val leftJoinFromReservationToBooking =
        root.join<Reservation, Booking>(Reservation::booking.name, JoinType.LEFT).get<Booking>(
          Booking::prisonerId.name
        )
      predicates.add(criteriaBuilder.equal(leftJoinFromReservationToBooking, this))
    }

    filter.prisonId?.run {
      val leftJoinFromReservationToBooking =
        root.join<Reservation, Booking>(Reservation::booking.name, JoinType.LEFT).get<Booking>(
          Booking::prisonId.name
        )
      predicates.add(criteriaBuilder.equal(leftJoinFromReservationToBooking, this))
    }

    filter.startDateTime?.run {
      predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get(Reservation::visitStart.name), this))
    }

    filter.endDateTime?.run {
      predicates.add(criteriaBuilder.lessThan(root.get(Reservation::visitStart.name), this))
    }

    filter.visitRestriction?.run {
      predicates.add(criteriaBuilder.equal(root.get<String>(Reservation::visitRestriction.name), this))
    }

    filter.visitRoom?.run {
      predicates.add(criteriaBuilder.equal(root.get<String>(Reservation::visitRoom.name), this))
    }

    filter.nomisPersonId?.run {
      val leftJoinFromReservationToBookingToVisitor = root.join<Reservation, Booking>(Reservation::booking.name, JoinType.LEFT)
        .join<Booking, MutableList<Visitor>>(Booking::visitors.name).get<Visitor>(
          Visitor::nomisPersonId.name
        )
      predicates.add(criteriaBuilder.equal(leftJoinFromReservationToBookingToVisitor, this))
    }

    filter.visitStatus?.run {
      val leftJoinFromReservationToBooking =
        root.join<Reservation, Booking>(Reservation::booking.name, JoinType.LEFT).get<Booking>(
          Booking::visitStatus.name
        )
      predicates.add(criteriaBuilder.equal(leftJoinFromReservationToBooking, this))
    }

    filter.createTimestamp?.run {
      predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get(Reservation::createTimestamp.name), this))
    }

    filter.modifyTimestamp?.run {
      predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get(Reservation::modifyTimestamp.name), this))
    }

    return criteriaBuilder.and(*predicates.toTypedArray())
  }
}
