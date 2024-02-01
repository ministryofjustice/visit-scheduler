package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import java.time.LocalDate
import java.time.LocalDateTime

@Repository
interface SessionSlotRepository : JpaRepository<SessionSlot, Long>, JpaSpecificationExecutor<SessionSlot> {

  @Query(
    "SELECT s FROM SessionSlot s " +
      "WHERE s.sessionTemplateReference = :sessionTemplateReference" +
      " AND s.slotDate = :slotDate ",
  )
  fun findSessionSlot(
    sessionTemplateReference: String,
    slotDate: LocalDate,
  ): SessionSlot?

  @Query(
    "SELECT s FROM SessionSlot s " +
      "WHERE s.prisonId = :prisonId AND " +
      "s.slotStart = :slotStart AND " +
      "s.slotEnd = :slotEnd AND " +
      "s.sessionTemplateReference is null",
  )
  fun findSessionSlotWithOutSessionReference(
    prisonId: Long,
    slotStart: LocalDateTime,
    slotEnd: LocalDateTime,
  ): SessionSlot?

  @Modifying
  @Query(
    "Update SessionSlot set sessionTemplateReference = :newSessionTemplateReference " +
      "WHERE sessionTemplateReference = :existingSessionTemplateReference AND " +
      "slotDate >= :fromDate ",
  )
  fun updateSessionTemplateReference(
    existingSessionTemplateReference: String,
    newSessionTemplateReference: String,
    fromDate: LocalDate,
  ): Int

  @Modifying
  @Query(
    "Update SessionSlot set " +
      "sessionTemplateReference = :newSessionTemplateReference, " +
      "slotStart = :newStartTime, " +
      "slotEnd = :newEndTime " +
      "WHERE sessionTemplateReference = :existingSessionTemplateReference AND " +
      "slotDate >= :fromDate ",
  )
  fun updateSessionTemplateReference(
    existingSessionTemplateReference: String,
    newSessionTemplateReference: String,
    fromDate: LocalDate,
    newStartTime: LocalDateTime,
    newEndTime: LocalDateTime,
  ): Int
}
