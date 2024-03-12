package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import java.time.LocalDate
import java.time.LocalDateTime

@Repository
interface SessionSlotRepository : JpaRepository<SessionSlot, Long>, JpaSpecificationExecutor<SessionSlot> {

  @Query(
    "SELECT s FROM SessionSlot s " +
      "WHERE s.sessionTemplateReference in :sessionTemplateReferences" +
      " AND s.slotDate in :slotDates ",
  )
  fun findSessionSlot(slotDates: List<LocalDate>, sessionTemplateReferences: List<String>): List<SessionSlot>

  @Query(
    "SELECT * FROM session_slot s " +
      "WHERE s.session_template_reference = :sessionTemplateReference" +
      " AND s.slot_date = :slotDate limit 1",
    nativeQuery = true,
  )
  fun findSessionSlot(
    sessionTemplateReference: String,
    slotDate: LocalDate,
  ): SessionSlot?

  @Query(
    "SELECT s.id FROM session_slot s " +
      "WHERE s.session_template_reference = :sessionTemplateReference" +
      " AND s.slot_date = :slotDate limit 1",
    nativeQuery = true,
  )
  fun findSessionSlotId(
    sessionTemplateReference: String,
    slotDate: LocalDate,
  ): Long?

  @Query(
    "SELECT * FROM session_slot s " +
      "WHERE s.prison_id = :prisonId AND " +
      "s.slot_start = :slotStart AND " +
      "s.slot_end = :slotEnd AND " +
      "s.session_template_reference is null limit 1",
    nativeQuery = true,
  )
  fun findSessionSlotWithOutSessionReference(
    prisonId: Long,
    slotStart: LocalDateTime,
    slotEnd: LocalDateTime,
  ): SessionSlot?
}
