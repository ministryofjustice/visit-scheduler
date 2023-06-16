package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionCategoryGroup

@Repository
interface SessionCategoryGroupRepository : JpaRepository<SessionCategoryGroup, Long> {

  @Query(
    "select g from SessionCategoryGroup g " +
      "where g.prison.code = :prisonCode ",
  )
  fun findByPrisonCode(
    @Param("prisonCode") prisonCode: String,
  ): List<SessionCategoryGroup>

  fun findByReference(reference: String): SessionCategoryGroup?

  @Modifying
  fun deleteByReference(reference: String): Int
}
