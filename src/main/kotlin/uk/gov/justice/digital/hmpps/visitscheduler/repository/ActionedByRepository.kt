package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.ActionedBy

@Repository
interface ActionedByRepository : JpaRepository<ActionedBy, Long> {

  @Query(
    "SELECT a FROM ActionedBy a " +
      "WHERE a.bookerReference =  :bookerReference and a.userType = 'PUBLIC'",
  )
  fun findActionedByForPublic(bookerReference: String): ActionedBy?

  @Query(
    "SELECT a FROM ActionedBy a " +
      "WHERE a.userName =  :userName and a.userType = 'STAFF'",
  )
  fun findActionedByForStaff(userName: String): ActionedBy?

  @Query(
    "SELECT a FROM ActionedBy a " +
      "WHERE a.userName is null and a.bookerReference is null and a.userType = 'SYSTEM'",
  )
  fun findActionedByForSystem(): ActionedBy?
}
