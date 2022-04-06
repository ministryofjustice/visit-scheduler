package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.LegacyData

@Repository
interface LegacyDataRepository : JpaRepository<LegacyData, Long> {

  fun findByVisitId(visitId: Long): LegacyData?
}
