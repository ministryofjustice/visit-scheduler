package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionCategoryGroup

@Repository
interface SessionCategoryGroupRepository : JpaRepository<SessionCategoryGroup, Long> {
  fun findByReference(reference: String): SessionCategoryGroup?
}
