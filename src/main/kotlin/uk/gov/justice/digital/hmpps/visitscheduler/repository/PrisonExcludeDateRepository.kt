package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonExcludeDate
import java.time.LocalDate

@Repository
interface PrisonExcludeDateRepository : JpaRepository<PrisonExcludeDate, Long> {
  @Modifying
  @Query(
    "delete  FROM PrisonExcludeDate ped " +
      "WHERE ped.prisonId = :prisonId" +
      " AND ped.excludeDate = :excludeDate",
  )
  fun deleteByPrisonIdAndExcludeDate(prisonId: Long, excludeDate: LocalDate)

  @Query(
    "select ped FROM PrisonExcludeDate ped " +
      "WHERE ped.prison.code = :prisonCode " +
      " ORDER BY ped.excludeDate desc ",
  )
  fun getExcludeDatesByPrisonCode(prisonCode: String): List<PrisonExcludeDate>

  @Query(
    "select COUNT(ped) > 0 FROM PrisonExcludeDate ped " +
      "WHERE ped.prison.code = :prisonCode " +
      " AND ped.excludeDate = :date ",
  )
  fun isDateExcludedByPrison(prisonCode: String, date: LocalDate): Boolean
}
