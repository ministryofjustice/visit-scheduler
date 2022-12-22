package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitTimeSlot
import java.time.DayOfWeek
import java.time.LocalTime

@Repository
interface VisitTimeSlotRepository : JpaRepository<VisitTimeSlot, Long> {

  fun getTimeSlotBySessionTemplateReference(sessionTemplateReference: String): VisitTimeSlot?

  fun getTimeSlot(
    prisonId: Long,
    startTime: LocalTime,
    endTime: LocalTime,
    dayOfWeek: DayOfWeek,
    visitType: VisitType,
  ): VisitTimeSlot?

}
