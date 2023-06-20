package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionCategoryGroup

@Repository
interface TestSessionCategoryGroupRepository : JpaRepository<SessionCategoryGroup, Long> {

  @Query(
    "SELECT CASE WHEN (COUNT(t) > 0) THEN TRUE ELSE FALSE END FROM SessionCategoryGroup t WHERE t.id = :id ",
  )
  fun hasById(@Param("id") id: Long): Boolean
}
