package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonVisitRequestRules

@Repository
interface PrisonVisitRequestRulesRepository : JpaRepository<PrisonVisitRequestRules, Int> {
  @Query(
    "SELECT r FROM PrisonVisitRequestRules r " +
      "WHERE r.prison.code = :prisonCode " +
      " AND r.active = true ",
  )
  fun findActiveVisitRequestRulesByPrison(prisonCode: String): List<PrisonVisitRequestRules>
}
