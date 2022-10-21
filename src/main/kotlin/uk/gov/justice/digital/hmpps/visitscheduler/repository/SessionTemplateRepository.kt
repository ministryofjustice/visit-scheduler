package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate

@Repository
interface SessionTemplateRepository : JpaRepository<SessionTemplate, Long> {

  @Query(
    "select u from SessionTemplate u " +
      "where u.prisonId = :prisonId " +
      "and (u.validToDate is null or u.validToDate >= :firstBookableDay) " +
      "and (u.validFromDate <= :lastBookableDay)"
  )
  fun findValidSessionTemplatesByPrisonId(
    @Param("prisonId") prisonId: String,
    @Param("firstBookableDay") firstBookableDay: LocalDate,
    @Param("lastBookableDay") lastBookableDay: LocalDate
  ): List<SessionTemplate>
}
