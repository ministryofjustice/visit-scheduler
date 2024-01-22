package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionSlotRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Service
class SessionSlotService {

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

  fun getSessionTimeAndDate(date: LocalDate, time: LocalTime): LocalDateTime {
    return date.atTime(time)
  }

  fun getSessionTimeAndDateString(date: LocalDate, time: LocalTime): String {
    return getSessionTimeAndDate(date, time).format(DateTimeFormatter.ISO_DATE_TIME)
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
    sessionTemplateReference?.let {
      return sessionSlotRepository.findSessionSlot(sessionTemplateReference, slotDate, slotTime, slotEndTime) ?: run {
        sessionSlotRepository.saveAndFlush(
          SessionSlot(
            sessionTemplateReference,
            prison.id,
            slotDate,
            slotTime,
            slotEndTime,
          ),
        )
      }
    } ?: run {
      return sessionSlotRepository.findSessionSlotWithOutSessionReference(prison.id, slotDate, slotTime, slotEndTime) ?: run {
        sessionSlotRepository.saveAndFlush(
          SessionSlot(
            prisonId = prison.id,
            slotDate = slotDate,
            slotTime = slotTime,
            slotEndTime = slotEndTime,
          ),
        )
      }
    }
  }
}
