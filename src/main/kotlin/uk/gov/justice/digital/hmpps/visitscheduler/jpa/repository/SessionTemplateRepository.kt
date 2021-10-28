package uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.SessionTemplate
import java.time.LocalDate

@Repository
interface SessionTemplateRepository : JpaRepository<SessionTemplate, Long> {

  @Query(
    "select u from SessionTemplate u " +
      "where u.prisonId = :prisonId " +
      "and (u.expiryDate is null or u.expiryDate >= :currentDate) " +
      "and (u.startDate <= :lastDay)"
  )
  fun findValidSessionsByPrisonId(
    @Param("prisonId") prisonId: String,
    @Param("currentDate") currentDate: LocalDate,
    @Param("lastDay") lastDay: LocalDate
  ): List<SessionTemplate>
}
