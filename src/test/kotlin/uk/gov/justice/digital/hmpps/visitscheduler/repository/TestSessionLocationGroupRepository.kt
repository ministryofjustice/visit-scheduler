package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.PermittedSessionLocation
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup

@Repository
interface TestSessionLocationGroupRepository : JpaRepository<SessionLocationGroup, Long> {

  @Query(
    "SELECT CASE WHEN (COUNT(t) > 0) THEN TRUE ELSE FALSE END FROM SessionLocationGroup t WHERE t.id = :id ",
  )
  fun hasById(@Param("id") id: Long): Boolean

  @Query("select exists(select * from session_to_location_group WHERE session_template_id=:templateID and group_id=:groupId)", nativeQuery = true)
  fun hasJoinTable(@Param("templateID") templateId: Long, @Param("groupId") groupId: Long): Boolean

  fun findByReference(reference: String): SessionLocationGroup?

  @Query(
    "select p from PermittedSessionLocation p " +
      "where p.sessionLocationGroup = :sessionLocationGroup ",
  )
  fun findPermittedSessionLocationsByGroup(sessionLocationGroup: SessionLocationGroup): List<PermittedSessionLocation>?
}
