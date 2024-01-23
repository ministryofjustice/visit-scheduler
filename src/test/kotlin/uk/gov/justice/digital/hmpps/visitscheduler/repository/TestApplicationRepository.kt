package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import java.time.LocalDateTime

@Repository
interface TestApplicationRepository : JpaRepository<Application, Long>, JpaSpecificationExecutor<Application> {

  fun findByReference(reference: String): Application?

  @Modifying
  fun deleteByReference(reference: String): Long

  @Transactional
  @Modifying
  @Query(
    "Update application SET  modify_timestamp = :modifyTimestamp WHERE id=:id",
    nativeQuery = true,
  )
  fun updateModifyTimestamp(modifyTimestamp: LocalDateTime, id: Long): Int
}
