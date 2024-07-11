package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.ActionedBy

@Repository
interface TestActionedByRepository : JpaRepository<ActionedBy, Long> {

  @Query(
    "SELECT a FROM ActionedBy a " +
      "WHERE (:value is null or a.bookerReference = :value or a.userName = :value) and a.userType = :userType",
  )
  fun findActionedBy(value: String? = null, userType: UserType): ActionedBy?
}
