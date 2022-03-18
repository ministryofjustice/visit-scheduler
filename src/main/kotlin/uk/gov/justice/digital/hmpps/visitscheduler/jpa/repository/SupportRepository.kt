package uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.Support

@Repository
interface SupportRepository : JpaRepository<Support, Int> {
  fun findByName(name: String): Support?
}
