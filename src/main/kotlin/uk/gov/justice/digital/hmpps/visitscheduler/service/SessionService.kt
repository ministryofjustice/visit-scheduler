package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OffenderNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.SessionConflict
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitRestrictionStats
import uk.gov.justice.digital.hmpps.visitscheduler.model.specification.VisitSpecification
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.temporal.TemporalAdjusters
import java.util.stream.Stream
import javax.validation.constraints.NotNull

@Service
@Transactional
class SessionService(
  private val sessionTemplateRepository: SessionTemplateRepository,
  private val visitRepository: VisitRepository,
  private val prisonApiClient: PrisonApiClient,
  private val visitService: VisitService,
  private val clock: Clock,
  @Value("\${policy.session.booking-notice-period.minimum-days:2}")
  private val policyNoticeDaysMin: Long,
  @Value("\${policy.session.booking-notice-period.maximum-days:28}")
  private val policyNoticeDaysMax: Long,
  @Value("\${policy.session.double-booking.filter:false}")
  private val policyFilterDoubleBooking: Boolean,
  @Value("\${policy.session.non-association.filter:false}")
  private val policyFilterNonAssociation: Boolean,
  @Value("\${policy.session.non-association.whole-day:true}")
  private val policyNonAssociationWholeDay: Boolean,
) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

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
      val offenderNonAssociationList = getOffenderNonAssociationList(prisonerId)

      sessions = filterPrisonerConflict(sessions, prisonerId, offenderNonAssociationList)
      populateConflict(sessions, prisonerId, offenderNonAssociationList)
    }

    populateBookedCount(sessions)

    return sessions.sortedWith(compareBy { it.startTimestamp })
  }

  private fun buildVisitSessionsUsingTemplate(
    sessionTemplate: SessionTemplate,
    requestedBookableStartDate: LocalDate,
    requestedBookableEndDate: LocalDate
  ): List<VisitSessionDto> {
    val firstBookableSessionDay = getFirstBookableSessionDay(requestedBookableStartDate, sessionTemplate.validFromDate, sessionTemplate.dayOfWeek)
    val lastBookableSessionDay = getLastBookableSession(requestedBookableEndDate, sessionTemplate.validToDate)

    if (firstBookableSessionDay <= lastBookableSessionDay) {

      return this.calculateDates(firstBookableSessionDay, lastBookableSessionDay)
        .map { date ->
          VisitSessionDto(
            sessionTemplateId = sessionTemplate.id,
            prisonId = sessionTemplate.prisonId,
            startTimestamp = LocalDateTime.of(date, sessionTemplate.startTime),
            openVisitCapacity = sessionTemplate.openCapacity,
            closedVisitCapacity = sessionTemplate.closedCapacity,
            endTimestamp = LocalDateTime.of(date, sessionTemplate.endTime),
            visitRoomName = sessionTemplate.visitRoom,
            visitType = sessionTemplate.visitType
          )
        }
        .toList()
    }

    return emptyList()
  }

  private fun calculateDates(firstBookableSessionDay: LocalDate, lastBookableSessionDay: LocalDate): Stream<LocalDate> {
    return firstBookableSessionDay.datesUntil(
      lastBookableSessionDay.plusDays(1), Period.ofWeeks(1)
    )
  }

  private fun getFirstBookableSessionDay(
    bookablePeriodStartDate: LocalDate,
    sessionStartDate: LocalDate,
    sessionDayOfWeek: DayOfWeek?
  ): LocalDate {

    var firstBookableSessionDate = sessionStartDate
    if (bookablePeriodStartDate.isAfter(firstBookableSessionDate)) {
      firstBookableSessionDate = bookablePeriodStartDate
    }
    sessionDayOfWeek?.let {
      if (firstBookableSessionDate.dayOfWeek != sessionDayOfWeek) {
        firstBookableSessionDate = firstBookableSessionDate.with(TemporalAdjusters.next(sessionDayOfWeek))
      }
    }
    return firstBookableSessionDate
  }

  private fun getLastBookableSession(
    bookablePeriodEndDate: LocalDate,
    validToDate: LocalDate?
  ): LocalDate {

    if (validToDate == null || bookablePeriodEndDate.isBefore(validToDate)) {
      return bookablePeriodEndDate
    }

    return validToDate
  }

  private fun filterPrisonerConflict(sessions: List<VisitSessionDto>, prisonerId: String, offenderNonAssociationList: List<OffenderNonAssociationDetailDto>): List<VisitSessionDto> {
    return sessions.filterNot {
      (policyFilterNonAssociation && offenderNonAssociationList.isNotEmpty() && sessionHasNonAssociation(it, offenderNonAssociationList)) ||
        (policyFilterDoubleBooking && sessionHasBooking(it, prisonerId))
    }
  }

  private fun populateConflict(sessions: List<VisitSessionDto>, prisonerId: String, offenderNonAssociationList: List<OffenderNonAssociationDetailDto>) {
    sessions.forEach {
      if (!policyFilterNonAssociation && offenderNonAssociationList.isNotEmpty() && sessionHasNonAssociation(it, offenderNonAssociationList))
        it.sessionConflicts?.add(SessionConflict.NON_ASSOCIATION)
      if (!policyFilterDoubleBooking && sessionHasBooking(it, prisonerId))
        it.sessionConflicts?.add(SessionConflict.DOUBLE_BOOKED)
    }
  }

  private fun populateBookedCount(sessions: List<VisitSessionDto>) {
    sessions.forEach {
      val visitRestrictionStatsList: List<VisitRestrictionStats> = getVisitRestrictionStats(it)
      it.openVisitBookedCount = getCountsByVisitRestriction(VisitRestriction.OPEN, visitRestrictionStatsList)
      it.closedVisitBookedCount = getCountsByVisitRestriction(VisitRestriction.CLOSED, visitRestrictionStatsList)
    }
  }

  private fun getCountsByVisitRestriction(visitRestriction: VisitRestriction, visitRestrictionStatsList: List<VisitRestrictionStats>): Int {
    return visitRestrictionStatsList.stream().filter { visitRestriction == it.visitRestriction }.mapToInt(VisitRestrictionStats::count).sum()
  }

  private fun sessionHasNonAssociation(session: VisitSessionDto, offenderNonAssociationList: @NotNull List<OffenderNonAssociationDetailDto>): Boolean {
    if (offenderNonAssociationList.isNotEmpty()) {
      val nonAssociationPrisonerIds = getNonAssociationPrisonerIds(session.startTimestamp.toLocalDate(), offenderNonAssociationList)
      val startDateTimeFilter = if (policyNonAssociationWholeDay) session.startTimestamp.toLocalDate().atStartOfDay() else session.startTimestamp
      val endDateTimeFilter = if (policyNonAssociationWholeDay) session.endTimestamp.toLocalDate().atTime(LocalTime.MAX) else session.endTimestamp

      // Any Non-association withing the session period && Non-association has a RESERVED or BOOKED booking.
      // We could also include ATTENDED booking but as prisons have a minimum notice period they can be ignored.
      return visitRepository.hasActiveVisits(nonAssociationPrisonerIds, session.prisonId, startDateTimeFilter, endDateTimeFilter)
    }

    return false
  }

  private fun getOffenderNonAssociationList(prisonerId: String): List<OffenderNonAssociationDetailDto> {
    try {
      val offenderNonAssociationList = prisonApiClient.getOffenderNonAssociation(prisonerId)?.nonAssociations ?: emptyList()
      LOG.debug("sessionHasNonAssociation prisonerId : $prisonerId has ${offenderNonAssociationList.size} non associations!")
      return offenderNonAssociationList
    } catch (e: WebClientResponseException) {
      if (e.statusCode != HttpStatus.NOT_FOUND)
        throw e
    }

    return emptyList()
  }

  private fun getNonAssociationPrisonerIds(startTimestamp: LocalDate, @NotNull offenderNonAssociationList: List<OffenderNonAssociationDetailDto>): List<String> {
    return offenderNonAssociationList.filter { isDateWithinRange(startTimestamp, it.effectiveDate, it.expiryDate) }.map { it.offenderNonAssociation.offenderNo }
  }

  private fun sessionHasBooking(session: VisitSessionDto, prisonerId: String): Boolean {
    return visitRepository.findAll(
      VisitSpecification(
        VisitFilter(
          prisonId = session.prisonId,
          prisonerId = prisonerId,
          startDateTime = session.startTimestamp,
          endDateTime = session.endTimestamp
        )
      )
    ).any { isActiveStatus(it.visitStatus) }
  }

  private fun getVisitRestrictionStats(session: VisitSessionDto): List<VisitRestrictionStats> {

    val restrictionReservedStats = visitRepository.getCountOfReservedSessionVisitsForOpenOrClosedRestriction(
      prisonId = session.prisonId,
      visitRoom = session.visitRoomName,
      startDateTime = session.startTimestamp,
      endDateTime = session.endTimestamp,
      expiredDateAndTime = visitService.getReservedExpiredDateAndTime()
    )

    val restrictionBookedStats = visitRepository.getCountOfBookedSessionVisitsForOpenOrClosedRestriction(
      prisonId = session.prisonId,
      visitRoom = session.visitRoomName,
      startDateTime = session.startTimestamp,
      endDateTime = session.endTimestamp
    )

    return restrictionReservedStats + restrictionBookedStats
  }

  private fun isDateWithinRange(sessionDate: LocalDate, startDate: LocalDate, endDate: LocalDate? = null) =
    sessionDate >= startDate && (endDate == null || sessionDate <= endDate)

  private fun isActiveStatus(status: VisitStatus) =
    status == VisitStatus.BOOKED || status == VisitStatus.RESERVED
}
