package uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.LegacyData

@Repository
interface LegacyDataRepository : JpaRepository<LegacyData, Long>{

  fun findByVisitId(visitId: Long): LegacyData?
}
