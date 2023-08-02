package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.SessionIncentiveLevelGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.SessionPrisonerIncentiveLevel

@Repository
interface TestSessionIncentiveLevelGroupRepository : JpaRepository<SessionIncentiveLevelGroup, Long> {

  @Query(
    "SELECT CASE WHEN (COUNT(t) > 0) THEN TRUE ELSE FALSE END FROM SessionIncentiveLevelGroup t WHERE t.id = :id ",
  )
  fun hasById(@Param("id") id: Long): Boolean

  fun findByReference(reference: String): SessionIncentiveLevelGroup?

  @Query(
    "select p from SessionPrisonerIncentiveLevel p " +
      "where p.sessionIncentiveLevelGroup = :sessionIncentiveLevelGroup",
  )
  fun findSessionIncentiveLevelsByGroup(sessionIncentiveLevelGroup: SessionIncentiveLevelGroup): List<SessionPrisonerIncentiveLevel>?
}
