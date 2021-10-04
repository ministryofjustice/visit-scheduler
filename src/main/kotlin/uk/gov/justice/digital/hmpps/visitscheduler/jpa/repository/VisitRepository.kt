package uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.Visit

@Repository
interface VisitRepository : JpaRepository<Visit, Long> {
    fun findByPrisonerId(prisonerId: String): List<Visit>
}
