package uk.gov.justice.digital.hmpps.visitscheduler.helper

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionSlotRepository
import java.time.LocalDate
import java.time.LocalTime

@Component
@Transactional
class SessionSlotEntityHelper(
  private val sessionSlotRepository: TestSessionSlotRepository,
) {

  companion object {

    fun createSessionSlot(
      sessionTemplateReference: String? = "sessionTemplateReference",
      prisonId: Long,
      slotDate: LocalDate = LocalDate.now().plusDays(2),
      slotTime: LocalTime = LocalTime.now().plusHours(4),
      slotEndTime: LocalTime = slotTime.plusHours(2),
    ): SessionSlot = SessionSlot(
      sessionTemplateReference = sessionTemplateReference,
      prisonId = prisonId,
      slotDate = slotDate,
      slotStart = slotDate.atTime(slotTime),
      slotEnd = slotDate.atTime(slotEndTime),
    )
  }

  fun create(
    sessionTemplateReference: String = "sessionTemplateReference",
    prisonId: Long,
    slotDate: LocalDate = LocalDate.now().plusDays(2),
    slotTime: LocalTime = LocalTime.now().plusHours(4),
    slotEndTime: LocalTime = slotTime.plusHours(2),
  ): SessionSlot = sessionSlotRepository.findSessionSlot(sessionTemplateReference, slotDate) ?: run {
    save(
      SessionSlot(
        sessionTemplateReference,
        prisonId,
        slotDate,
        slotStart = slotDate.atTime(slotTime),
        slotEnd = slotDate.atTime(slotEndTime),
      ),
    )
  }

  fun create(
    prisonId: Long,
    slotDate: LocalDate = LocalDate.now().plusDays(2),
    slotTime: LocalTime = LocalTime.now().plusHours(4),
    slotEndTime: LocalTime = slotTime.plusHours(2),
  ): SessionSlot = sessionSlotRepository.findSessionSlotWithNullReference(slotDate) ?: run {
    save(
      SessionSlot(
        null,
        prisonId,
        slotDate,
        slotStart = slotDate.atTime(slotTime),
        slotEnd = slotDate.atTime(slotEndTime),
      ),
    )
  }

  fun save(slot: SessionSlot): SessionSlot = sessionSlotRepository.saveAndFlush(slot)
}
