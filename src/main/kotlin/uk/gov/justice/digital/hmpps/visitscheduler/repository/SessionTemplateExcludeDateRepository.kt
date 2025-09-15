package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplateExcludeDate
import java.time.LocalDate

@Repository
interface SessionTemplateExcludeDateRepository : JpaRepository<SessionTemplateExcludeDate, Long> {
  @Modifying
  @Query(
    "delete  FROM SessionTemplateExcludeDate sted " +
      "WHERE sted.sessionTemplateId = :sessionTemplateId" +
      " AND sted.excludeDate = :excludeDate",
  )
  fun deleteBySessionTemplateIdAndExcludeDate(sessionTemplateId: Long, excludeDate: LocalDate)

  @Query(
    "select COUNT(sted) > 0 FROM SessionTemplateExcludeDate sted " +
      "WHERE sted.sessionTemplate.reference = :sessionTemplateReference " +
      " AND sted.excludeDate = :date ",
  )
  fun isSessionDateExcluded(sessionTemplateReference: String, date: LocalDate): Boolean
}
