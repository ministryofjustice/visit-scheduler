package uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.Visit
import javax.persistence.LockModeType

@Repository
interface VisitRepository : JpaRepository<Visit, String>, JpaSpecificationExecutor<Visit> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  fun deleteAllByReferenceIn(reference: List<String>)

  fun findByReference(reference: String): Visit?
}
