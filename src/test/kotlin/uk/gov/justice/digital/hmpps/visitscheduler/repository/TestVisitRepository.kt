package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit

@Repository
interface TestVisitRepository : JpaRepository<Visit, Long>, JpaSpecificationExecutor<Visit> {

  fun findByReference(reference: String): Visit

  @Query(
    "SELECT v.*  FROM visit v" +
      "  JOIN visits_to_applications vta ON vta.visit_id = v.id " +
      "  JOIN application a on a.id = vta.application_id " +
      "  WHERE a.reference = :applicationReference",
    nativeQuery = true,
  )
  fun findByApplicationReference(applicationReference: String): Visit?

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
