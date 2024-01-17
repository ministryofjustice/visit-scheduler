package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.OldVisit

@Repository
interface TestVisitRepository : JpaRepository<OldVisit, Long>, JpaSpecificationExecutor<OldVisit> {

  fun findAllByReference(reference: String): List<OldVisit>

  fun findByApplicationReference(reference: String): OldVisit?

  @Modifying
  fun deleteByApplicationReference(applicationReference: String): Long

  @Query(
    "SELECT CASE WHEN (COUNT(v) > 0) THEN TRUE ELSE FALSE END FROM OldVisit v WHERE v.id = :visitId ",
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
