package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate

@Repository
interface TestSessionTemplateRepository : JpaRepository<SessionTemplate, Long>
