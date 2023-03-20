package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionPrisonerCategory

@Repository
interface SessionPrisonerCategoryRepository : JpaRepository<SessionPrisonerCategory, Long> {

  fun findByCode(it: String): SessionPrisonerCategory?
}
