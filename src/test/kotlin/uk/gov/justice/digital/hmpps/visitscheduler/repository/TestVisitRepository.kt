package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit

@Repository
interface TestVisitRepository :
  JpaRepository<Visit, Long>,
  JpaSpecificationExecutor<Visit> {

  fun findByReference(reference: String): Visit

  @Query(
    "SELECT v.*  FROM visit v" +
      "  JOIN application a on a.visit_id = v.id" +
      "  WHERE a.reference = :applicationReference",
    nativeQuery = true,
  )
  fun findByApplicationReference(applicationReference: String): Visit?

  @Modifying
  @Query(
    "DELETE FROM visit WHERE id IN (SELECT v.id  FROM visit v" +
      "  JOIN application a on a.visit_id = v.id" +
      "  WHERE a.reference = :applicationReference)",
    nativeQuery = true,
  )
  fun deleteByApplicationReferenceNative(applicationReference: String): Int

  @Modifying
  fun deleteByReference(reference: String): Long

  @Modifying
  @Query(
    "DELETE FROM visit v " +
      "  WHERE v.reference = :reference",
    nativeQuery = true,
  )
  fun deleteByReferenceNative(reference: String): Int

  @Query(
    "SELECT CASE WHEN (COUNT(v) = 1) THEN TRUE ELSE FALSE END FROM Visit v WHERE v.reference = :reference ",
  )
  fun hasOneVisit(@Param("reference") reference: String): Boolean

  @Query(
    "SELECT CASE WHEN (COUNT(v) > 0) THEN TRUE ELSE FALSE END FROM Visit v WHERE v.id = :visitId ",
  )
  fun hasVisit(@Param("visitId") visitId: Long): Boolean

  @Query(
    "SELECT CASE WHEN (COUNT(vv) > 0) THEN TRUE ELSE FALSE END FROM VisitVisitor vv WHERE vv.visitId = :visitId ",
  )
  fun hasVisitors(@Param("visitId") visitId: Long): Boolean

  @Query(
    "SELECT CASE WHEN (COUNT(vs) > 0) THEN TRUE ELSE FALSE END FROM VisitSupport vs WHERE vs.visitId = :visitId ",
  )
  fun hasSupport(@Param("visitId") visitId: Long): Boolean

  @Query(
    "SELECT CASE WHEN (COUNT(vn) > 0) THEN TRUE ELSE FALSE END FROM VisitNote vn WHERE vn.visitId = :visitId ",
  )
  fun hasNotes(@Param("visitId") visitId: Long): Boolean

  @Query(
    "SELECT CASE WHEN (COUNT(vc) > 0) THEN TRUE ELSE FALSE END FROM VisitContact vc WHERE vc.visitId = :visitId ",
  )
  fun hasContact(@Param("visitId") visitId: Long): Boolean
}
