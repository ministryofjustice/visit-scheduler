package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OffenderNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.specification.VisitSpecification
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

@Service
@Transactional
class SessionService(
  private val sessionTemplateRepository: SessionTemplateRepository,
  private val visitRepository: VisitRepository,
  private val prisonApiClient: PrisonApiClient,
  private val clock: Clock,
  @Value("\${policy.session.booking-notice-period.minimum-days:2}")
  private val policyNoticeDaysMin: Long,
  @Value("\${policy.session.booking-notice-period.maximum-days:28}")
  private val policyNoticeDaysMax: Long,
  @Value("\${policy.session.non-association.whole-day:true}")
  private val policyNonAssociationWholeDay: Boolean,
) {

  @Transactional(readOnly = true)
  fun getVisitSessions(
    prisonId: String,
    prisonerId: String? = null,
    noticeDaysMin: Long? = null,
    noticeDaysMax: Long? = null
  ): List<VisitSessionDto> {

    val today = LocalDate.now(clock)
    val requestedBookableStartDate = today.plusDays(noticeDaysMin ?: policyNoticeDaysMin)
    val requestedBookableEndDate = today.plusDays(noticeDaysMax ?: policyNoticeDaysMax)

    val sessionTemplates = sessionTemplateRepository.findValidSessionTemplatesByPrisonId(
      prisonId,
      requestedBookableStartDate,
      requestedBookableEndDate
    )

    var sessions = sessionTemplates.map {
      buildVisitSessionsUsingTemplate(it, requestedBookableStartDate, requestedBookableEndDate)
    }.flatten()

    if (!prisonerId.isNullOrBlank()) {
      sessions = filterPrisonerConflict(sessions, prisonerId)
    }

    populateBookedCount(sessions)

    return sessions.sortedWith(compareBy { it.startTimestamp })
  }

  private fun buildVisitSessionsUsingTemplate(
    sessionTemplate: SessionTemplate,
    requestedBookableStartDate: LocalDate,
    requestedBookableEndDate: LocalDate
  ): List<VisitSessionDto> {

    val firstBookableSessionDay = getFirstBookableSessionDay(requestedBookableStartDate, sessionTemplate)
    val lastBookableSessionDay = getLastBookableSession(sessionTemplate, requestedBookableEndDate)

    if (firstBookableSessionDay.isBefore(lastBookableSessionDay)) {

      return firstBookableSessionDay.datesUntil(
        lastBookableSessionDay.plusDays(1), sessionTemplate.frequency.frequencyPeriod
      )
        .map { date ->
          VisitSessionDto(
            sessionTemplateId = sessionTemplate.id,
            prisonId = sessionTemplate.prisonId,
            startTimestamp = LocalDateTime.of(date, sessionTemplate.startTime),
            openVisitCapacity = sessionTemplate.openCapacity,
            closedVisitCapacity = sessionTemplate.closedCapacity,
            endTimestamp = LocalDateTime.of(date, sessionTemplate.endTime),
            visitRoomName = sessionTemplate.visitRoom,
            visitType = sessionTemplate.visitType,
            dayOfWeek = sessionTemplate.dayOfWeek
          )
        }
        .toList()
    }

    return emptyList()
  }

  private fun getFirstBookableSessionDay(
    bookablePeriodStartDate: LocalDate,
    sessionTemplate: SessionTemplate
  ): LocalDate {

    var startDate = sessionTemplate.startDate
    if (bookablePeriodStartDate.isAfter(sessionTemplate.startDate)) {
      startDate = bookablePeriodStartDate
    }
    sessionTemplate.dayOfWeek?.let {
      if (startDate.dayOfWeek != sessionTemplate.dayOfWeek) {
        startDate = startDate.with(TemporalAdjusters.next(sessionTemplate.dayOfWeek))
      }
    }
    return startDate
  }

  private fun getLastBookableSession(
    sessionTemplate: SessionTemplate,
    bookablePeriodEndDate: LocalDate
  ): LocalDate {
    if (sessionTemplate.expiryDate == null || bookablePeriodEndDate.isBefore(sessionTemplate.expiryDate)) {
      return bookablePeriodEndDate
    }

    return sessionTemplate.expiryDate
  }

  private fun filterPrisonerConflict(sessions: List<VisitSessionDto>, prisonerId: String): List<VisitSessionDto> {
    return sessions.filterNot {
      sessionHasNonAssociation(it, prisonerId)
    }
  }

  private fun populateBookedCount(sessions: List<VisitSessionDto>) {
    sessions.forEach {
      it.openVisitBookedCount = sessionBookedCount(it, VisitRestriction.OPEN)
      it.closedVisitBookedCount = sessionBookedCount(it, VisitRestriction.CLOSED)
    }
  }

  private fun sessionHasNonAssociation(session: VisitSessionDto, prisonerId: String): Boolean {
    // Any Non-association withing the session period && Non-association has a RESERVED or BOOKED booking.
    // We could also include ATTENDED booking but as prisons have a minimum notice period they can be ignored.
    return getNonAssociation(prisonerId).any { it ->
      isDateWithinRange(session.startTimestamp.toLocalDate(), it.effectiveDate, it.expiryDate) &&
        it.offenderNonAssociation.let { ona ->
          visitRepository.findAll(
            VisitSpecification(
              VisitFilter(
                prisonerId = ona.offenderNo,
                prisonId = session.prisonId,
                startDateTime =
                if (policyNonAssociationWholeDay) session.startTimestamp.toLocalDate().atStartOfDay() else session.startTimestamp,
                endDateTime =
                if (policyNonAssociationWholeDay) session.endTimestamp.toLocalDate().atTime(LocalTime.MAX) else session.endTimestamp
              )
            )
          ).any { isActiveStatus(it.visitStatus) }
        }
    }
  }

  private fun getNonAssociation(prisonerId: String): List<OffenderNonAssociationDetailDto> {
    try {
      return prisonApiClient.getOffenderNonAssociation(prisonerId)!!.nonAssociations
    } catch (e: WebClientResponseException) {
      if (e.statusCode != HttpStatus.NOT_FOUND)
        throw e
    }
    return emptyList()
  }

  private fun sessionBookedCount(session: VisitSessionDto, restriction: VisitRestriction): Int {
    return visitRepository.findAll(
      VisitSpecification(
        VisitFilter(
          prisonId = session.prisonId,
          visitRoom = session.visitRoomName,
          startDateTime = session.startTimestamp,
          endDateTime = session.endTimestamp,
          visitRestriction = restriction
        )
      )
    ).count { isActiveStatus(it.visitStatus) }
  }

  private fun isDateWithinRange(sessionDate: LocalDate, startDate: LocalDate, endDate: LocalDate? = null) =
    sessionDate >= startDate && (endDate == null || sessionDate <= endDate)

  private fun isActiveStatus(status: VisitStatus) =
    status == VisitStatus.BOOKED || status == VisitStatus.RESERVED
}
