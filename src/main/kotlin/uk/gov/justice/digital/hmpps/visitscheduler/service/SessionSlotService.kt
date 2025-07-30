package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionSlotRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class SessionSlotService {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Autowired
  private lateinit var sessionSlotRepository: SessionSlotRepository

  fun getSessionSlot(
    slotDate: LocalDate,
    sessionTemplate: SessionTemplateDto,
    prison: Prison,
  ): SessionSlot {
    val slotTime = sessionTemplate.sessionTimeSlot.startTime
    val slotEndTime = sessionTemplate.sessionTimeSlot.endTime

    return getSessionSlot(sessionTemplate.reference, slotDate, slotTime, slotEndTime, prison)
  }

  fun getSessionSlot(
    startTimeDate: LocalDateTime,
    endTimeAndDate: LocalDateTime,
    sessionTemplateReference: String,
    prison: Prison,
  ): SessionSlot {
    val slotDate = startTimeDate.toLocalDate()
    val slotTime = startTimeDate.toLocalTime()
    val slotEndTime = endTimeAndDate.toLocalTime()

    return getSessionSlot(sessionTemplateReference, slotDate, slotTime, slotEndTime, prison)
  }

  fun getSessionSlot(
    startTimeDate: LocalDateTime,
    endTimeAndDate: LocalDateTime,
    prison: Prison,
  ): SessionSlot {
    val slotDate = startTimeDate.toLocalDate()
    val slotTime = startTimeDate.toLocalTime()
    val slotEndTime = endTimeAndDate.toLocalTime()
    return getSessionSlot(slotDate = slotDate, slotTime = slotTime, slotEndTime = slotEndTime, prison = prison)
  }

  private fun getSessionSlot(
    sessionTemplateReference: String? = null,
    slotDate: LocalDate,
    slotTime: LocalTime,
    slotEndTime: LocalTime,
    prison: Prison,
  ): SessionSlot {
    val slotStart = slotDate.atTime(slotTime)
    val slotEnd = slotDate.atTime(slotEndTime)

    sessionTemplateReference?.let {
      return sessionSlotRepository.findSessionSlot(sessionTemplateReference, slotDate) ?: run {
        try {
          sessionSlotRepository.saveAndFlush(
            SessionSlot(
              sessionTemplateReference,
              prison.id,
              slotDate,
              slotStart = slotStart,
              slotEnd = slotEnd,
            ),
          )
        } catch (e: DataIntegrityViolationException) {
          LOG.warn("Constraint issue with session slot session template reference: $sessionTemplateReference slot date: $slotDate.")
          sessionSlotRepository.findSessionSlot(sessionTemplateReference, slotDate)!!
        }
      }
    } ?: run {
      return sessionSlotRepository.findSessionSlotWithOutSessionReference(prison.id, slotStart, slotEnd) ?: run {
        try {
          sessionSlotRepository.saveAndFlush(
            SessionSlot(
              prisonId = prison.id,
              slotDate = slotDate,
              slotStart = slotStart,
              slotEnd = slotEnd,
            ),
          )
        } catch (e: DataIntegrityViolationException) {
          LOG.warn("Constraint issue with session slot prisonId: ${prison.id} slot date : $slotDate.")
          sessionSlotRepository.findSessionSlotWithOutSessionReference(prison.id, slotStart, slotEnd)!!
        }
      }
    }
  }

  fun getSessionSlot(sessionTemplateReference: String, slotDate: LocalDate): SessionSlot? = sessionSlotRepository.findSessionSlot(sessionTemplateReference, slotDate)
}
