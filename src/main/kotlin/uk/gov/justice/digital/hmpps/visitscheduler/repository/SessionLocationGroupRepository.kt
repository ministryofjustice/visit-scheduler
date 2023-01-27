package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionLocationGroup

@Repository
interface SessionLocationGroupRepository : JpaRepository<SessionLocationGroup, Long> {

  @Query(
    "select g from SessionLocationGroup g " +
      "where g.prison.code = :prisonCode "
  )
  fun findByPrisonCode(
    @Param("prisonCode") prisonCode: String,
  ): List<SessionLocationGroup>

  fun findByReference(reference: String): SessionLocationGroup?
}
