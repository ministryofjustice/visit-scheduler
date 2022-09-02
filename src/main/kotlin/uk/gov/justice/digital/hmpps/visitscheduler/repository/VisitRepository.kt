package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import java.time.LocalDateTime
import javax.persistence.LockModeType

@Repository
interface VisitRepository : JpaRepository<Visit, Long>, JpaSpecificationExecutor<Visit> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  fun deleteAllByReferenceIn(reference: List<String>)

  fun findByReference(reference: String): Visit?

  @Query(
    "SELECT count(v) > 0 FROM Visit v " +
      "WHERE v.prisonerId in (:prisonerIds) and " +
      "v.prisonId = :prisonId and " +
      "v.visitStart >= :startDateTime and " +
      "v.visitStart < :endDateTime and " +
      " (v.visitStatus = 'BOOKED' or v.visitStatus = 'RESERVED') "
  )
  fun hasActiveVisits(
    prisonerIds: List<String>,
    prisonId: String,
    startDateTime: LocalDateTime,
    endDateTime: LocalDateTime
  ): Boolean
}
