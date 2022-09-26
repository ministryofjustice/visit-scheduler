package uk.gov.justice.digital.hmpps.visitscheduler.integration.container

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import java.time.LocalDateTime

@Repository
interface TestVisitRepository : JpaRepository<Visit, Long>, JpaSpecificationExecutor<Visit> {

  @Transactional
  @Modifying
  @Query(
    "Update visit SET  modify_timestamp = :modifyTimestamp WHERE id=:id",
    nativeQuery = true
  )
  fun updateModifyTimestamp(modifyTimestamp: LocalDateTime, id: Long): Int
}
