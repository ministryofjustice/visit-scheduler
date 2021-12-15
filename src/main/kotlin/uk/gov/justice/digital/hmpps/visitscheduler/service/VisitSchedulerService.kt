package uk.gov.justice.digital.hmpps.visitscheduler.service

import VisitSpecification
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.data.CreateVisitRequest
import uk.gov.justice.digital.hmpps.visitscheduler.data.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.data.VisitSession
import uk.gov.justice.digital.hmpps.visitscheduler.data.filter.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.SessionFrequency
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitVisitorPk
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.VisitRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.function.Supplier

@Service
@Transactional
class VisitSchedulerService(
  private val visitRepository: VisitRepository,
  private val sessionTemplateRepository: SessionTemplateRepository,
  private val clock: Clock,
  @Value("\${policy.maximum-booking-notice-period-days}") private val maxBookingNoticeDays: Long,
  @Value("\${policy.minimum-booking-notice-period-days}") private val minBookingNoticeDays: Long,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional(readOnly = true)
  fun getVisitById(visitId: Long): VisitDto {
    return visitRepository.findById(visitId).map { VisitDto(it) }
      .orElseThrow(VisitNotFoundException("Visit id  $visitId not found"))
  }

  @Transactional(readOnly = true)
  fun getVisitSessions(prisonId: String): List<VisitSession> {
    val bookablePeriodEndDate = LocalDate.now(clock).plusDays(maxBookingNoticeDays)
    val bookablePeriodStartDate = LocalDate.now(clock).plusDays(minBookingNoticeDays)
    val sessionTemplates =
      sessionTemplateRepository.findValidSessionTemplatesByPrisonId(
        prisonId,
        bookablePeriodStartDate,
        bookablePeriodEndDate
      )
    return sessionTemplates.map {
      buildVisitSessionsUsingTemplate(it, bookablePeriodStartDate, bookablePeriodEndDate)
    }.flatten().sortedWith(compareBy { it.startTimestamp })
  }

  // from the start date calculate the slots for based on chosen frequency  (expiry is inclusive)
  private fun buildVisitSessionsUsingTemplate(
    sessionTemplate: SessionTemplate,
    bookablePeriodStartDate: LocalDate,
    bookablePeriodEndDate: LocalDate
  ): List<VisitSession> {
    val lastBookableSessionDay =
      if (sessionTemplate.expiryDate == null || bookablePeriodEndDate.isBefore(sessionTemplate.expiryDate)) bookablePeriodEndDate else sessionTemplate.expiryDate
    val firstBookableSessionDay: LocalDate = if (bookablePeriodStartDate.isAfter(sessionTemplate.startDate)) bookablePeriodStartDate else sessionTemplate.startDate
    return sessionTemplate.startDate.datesUntil(lastBookableSessionDay.plusDays(1), SessionFrequency.valueOf(sessionTemplate.frequency).frequencyPeriod)
      .map { date ->
        VisitSession(
          sessionTemplateId = sessionTemplate.id,
          prisonId = sessionTemplate.prisonId,
          startTimestamp = LocalDateTime.of(date, sessionTemplate.startTime),
          closedVisitBookedCount = 0,
          openVisitBookedCount = 0,
          openVisitCapacity = sessionTemplate.openCapacity,
          closedVisitCapacity = sessionTemplate.closedCapacity,
          endTimestamp = LocalDateTime.of(date, sessionTemplate.endTime),
          visitRoomName = sessionTemplate.visitRoom,
          visitType = sessionTemplate.visitType.name,
          visitTypeDescription = sessionTemplate.visitType.description,
          restrictions = sessionTemplate.restrictions
        )
      }
      .filter { session -> session.startTimestamp > LocalDateTime.of(firstBookableSessionDay, LocalTime.MIDNIGHT) } // remove sessions are before the bookable period
      .toList()
  }

  @Transactional(readOnly = true)
  fun findVisitsByFilter(visitFilter: VisitFilter): List<VisitDto> {
    return visitRepository.findAll(VisitSpecification(visitFilter)).sortedBy { it.visitStart }.map { VisitDto(it) }
  }

  fun deleteVisit(visitId: Long) {
    val visit = visitRepository.findByIdOrNull(visitId)
    visit?.let { visitRepository.delete(it) }.also { log.info("Visit with id  $visitId deleted") }
      ?: run {
        log.info("Visit id  $visitId not found")
      }
  }

  fun createVisit(createVisitRequest: CreateVisitRequest): VisitDto {
    log.info("Creating visit for ${createVisitRequest.prisonerId}")
    val visitEntity = visitRepository.saveAndFlush(
      Visit(
        prisonId = createVisitRequest.prisonId,
        prisonerId = createVisitRequest.prisonerId,
        visitType = createVisitRequest.visitType,
        status = createVisitRequest.visitStatus,
        visitRoom = createVisitRequest.visitRoom,
        visitStart = createVisitRequest.startTimestamp,
        visitEnd = createVisitRequest.endTimestamp,
        sessionTemplateId = createVisitRequest.sessionId,
        reasonableAdjustments = createVisitRequest.reasonableAdjustments
      )
    )

    createVisitRequest.contactList?.let { contactList ->
      contactList.forEach {
        visitEntity.visitors.add(
          VisitVisitor(
            id = VisitVisitorPk(
              contactId = it.contactId,
              visitId = visitEntity.id
            ),
            leadVisitor = it.leadVisitor, visit = visitEntity
          )
        )
      }
    }

    return VisitDto(
      visitEntity
    )
  }
}

class VisitNotFoundException(message: String?) :
  RuntimeException(message),
  Supplier<VisitNotFoundException> {
  override fun get(): VisitNotFoundException {
    return VisitNotFoundException(message)
  }
}
