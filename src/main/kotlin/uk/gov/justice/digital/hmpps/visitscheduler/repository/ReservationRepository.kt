package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Reservation
import javax.persistence.LockModeType

@Repository
interface ReservationRepository : JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

  // deprecate
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  fun deleteAllByReferenceIn(reference: List<String>)

  fun findByReference(reference: String): Reservation?
}
