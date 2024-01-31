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
      sessionTemplateReference: String ? = "sessionTemplateReference",
      prisonId: Long,
      slotDate: LocalDate = LocalDate.now().plusDays(2),
      slotTime: LocalTime = LocalTime.now().plusHours(4),
      slotEndTime: LocalTime = slotTime.plusHours(2),
    ): SessionSlot {
      return SessionSlot(
        sessionTemplateReference = sessionTemplateReference,
        prisonId = prisonId,
        slotDate = slotDate,
        slotTime = slotTime,
        slotEndTime = slotEndTime,
      )
    }
  }

  fun create(
    sessionTemplateReference: String = "sessionTemplateReference",
    prisonId: Long,
    slotDate: LocalDate = LocalDate.now().plusDays(2),
    slotTime: LocalTime = LocalTime.now().plusHours(4),
    slotEndTime: LocalTime = slotTime.plusHours(2),
  ): SessionSlot {
    return sessionSlotRepository.findSessionSlot(sessionTemplateReference, slotDate, slotTime, slotEndTime) ?: run {
      save(
        SessionSlot(
          sessionTemplateReference,
          prisonId,
          slotDate,
          slotTime,
          slotEndTime,
        ),
      )
    }
  }

  fun save(slot: SessionSlot): SessionSlot {
    return sessionSlotRepository.saveAndFlush(slot)
  }
}
