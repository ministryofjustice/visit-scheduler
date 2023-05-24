package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison

@Repository
interface PrisonRepository : JpaRepository<Prison, Long> {

  fun findByCode(prisonCode: String): Prison?

  @Query(
    "select p.code from Prison p " +
      "where p.active = true order by p.code",
  )
  fun getSupportedPrisons(): List<String>

  fun findAllByOrderByCodeAsc(): List<Prison>
}
