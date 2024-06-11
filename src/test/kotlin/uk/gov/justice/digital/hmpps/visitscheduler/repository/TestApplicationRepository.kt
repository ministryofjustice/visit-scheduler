package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import java.time.LocalDateTime

@Repository
interface TestApplicationRepository : JpaRepository<Application, Long>, JpaSpecificationExecutor<Application> {

  @Query(
    "SELECT a.visit  FROM Application a" +
      "  WHERE a.reference = :applicationReference",
  )
  fun findVisitByApplicationReference(applicationReference: String): Visit?

  @Query(
    "SELECT a  FROM Application a" +
      "  WHERE a.reference = :applicationReference",
  )
  fun findByApplicationReference(applicationReference: String): Application?

  @Query(
    "select count(*)>0 from application_visitor av where av.application_id=:applicationId",
    nativeQuery = true,
  )
  fun hasVisitorsByApplicationId(applicationId: Long): Boolean

  @Query(
    "select count(*)>0 from application_contact  where application_id=:applicationId",
    nativeQuery = true,
  )
  fun hasContactByApplicationId(applicationId: Long): Boolean

  @Query(
    "select count(*)>0 from application_support  where application_id=:applicationId",
    nativeQuery = true,
  )
  fun hasSupportByApplicationId(applicationId: Long): Boolean

  fun findByReference(reference: String): Application?

  @Transactional
  @Modifying
  fun deleteByReference(reference: String): Long

  @Query(
    "SELECT COUNT(a) > 0 FROM Application a WHERE a.id = :id ",
  )
  fun hasApplication(@Param("id") id: Long): Boolean

  @Transactional
  @Modifying
  @Query(
    "Update application SET  modify_timestamp = :modifyTimestamp WHERE id=:id",
    nativeQuery = true,
  )
  fun updateModifyTimestamp(modifyTimestamp: LocalDateTime, id: Long): Int

  @Query(
    "SELECT CASE WHEN (COUNT(av) > 0) THEN TRUE ELSE FALSE END FROM ApplicationVisitor av WHERE av.applicationId = :id ",
  )
  fun hasVisitors(@Param("id") id: Long): Boolean

  @Query(
    "SELECT CASE WHEN (COUNT(asu) > 0) THEN TRUE ELSE FALSE END FROM ApplicationSupport asu WHERE asu.applicationId = :id ",
  )
  fun hasSupport(@Param("id") id: Long): Boolean

  @Query(
    "SELECT CASE WHEN (COUNT(ac) > 0) THEN TRUE ELSE FALSE END FROM ApplicationContact ac WHERE ac.applicationId = :id ",
  )
  fun hasContact(@Param("id") id: Long): Boolean

  @Transactional
  @Modifying
  @Query(
    "Update application SET  modify_timestamp = :dateAndTime, create_timestamp = :dateAndTime  WHERE reference=:reference",
    nativeQuery = true,
  )
  fun updateTimestamp(dateAndTime: LocalDateTime, reference: String): Int
}
