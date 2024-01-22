package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import java.time.LocalDate
import java.time.LocalTime

@Repository
interface SessionSlotRepository : JpaRepository<SessionSlot, Long>, JpaSpecificationExecutor<SessionSlot> {

  @Query(
    "SELECT s FROM SessionSlot s " +
      "WHERE s.sessionTemplateReference = :sessionTemplateReference" +
      " AND s.slotDate = :slotDate AND s.slotTime = :slotTime AND s.slotEndTime = :slotEndTime",
  )
  fun findSessionSlot(
    sessionTemplateReference: String,
    slotDate: LocalDate,
    slotTime: LocalTime,
    slotEndTime: LocalTime,
  ): SessionSlot?
}
