package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.SupportType

@Repository
interface SupportTypeRepository : JpaRepository<SupportType, Int> {
  fun findByName(name: String): SupportType?
}
