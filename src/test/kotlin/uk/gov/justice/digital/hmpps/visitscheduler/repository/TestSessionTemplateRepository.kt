package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate

@Repository
interface TestSessionTemplateRepository : JpaRepository<SessionTemplate, Long> {

  @Modifying
  fun deleteByReference(reference: String): Int

  @Query(
    "SELECT CASE WHEN (COUNT(st) > 0) THEN TRUE ELSE FALSE END FROM SessionTemplate st WHERE st.id = :id ",
  )
  fun hasSessionTemplate(@Param("id") id: Long): Boolean
}
