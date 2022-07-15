package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.session.OffenderNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.session.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.RestrictionType
import uk.gov.justice.digital.hmpps.visitscheduler.model.StatusType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.specification.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.specification.VisitSpecification
import uk.gov.justice.digital.hmpps.visitscheduler.repository.ReservationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Service
@Transactional
class SessionService(
  private val sessionTemplateRepository: SessionTemplateRepository,
  private val reservationRepository: ReservationRepository,
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
    val bookablePeriodStartDate = LocalDate.now(clock).plusDays(noticeDaysMin ?: policyNoticeDaysMin)
    val bookablePeriodEndDate = LocalDate.now(clock).plusDays(noticeDaysMax ?: policyNoticeDaysMax)

    val sessionTemplates = sessionTemplateRepository.findValidSessionTemplatesByPrisonId(
      prisonId,
      bookablePeriodStartDate,
      bookablePeriodEndDate
    )

    var sessions = sessionTemplates.map {
      buildVisitSessionsUsingTemplate(it, bookablePeriodStartDate, bookablePeriodEndDate)
    }.flatten()

    if (!prisonerId.isNullOrBlank()) {
      sessions = filterPrisonerConflict(sessions, prisonerId)
    }

    populateBookedCount(sessions)

    return sessions.sortedWith(compareBy { it.startTimestamp })
  }

  private fun buildVisitSessionsUsingTemplate(
    sessionTemplate: SessionTemplate,
    bookablePeriodStartDate: LocalDate,
    bookablePeriodEndDate: LocalDate
  ): List<VisitSessionDto> {
    val lastBookableSessionDay =
      if (sessionTemplate.expiryDate == null || bookablePeriodEndDate.isBefore(sessionTemplate.expiryDate))
        bookablePeriodEndDate
      else
        sessionTemplate.expiryDate

    val firstBookableSessionDay: LocalDate = if (bookablePeriodStartDate.isAfter(sessionTemplate.startDate))
      bookablePeriodStartDate
    else
      sessionTemplate.startDate

    // Create a VisitSession for every date from the template start date in increments of frequency until the
    // lastBookableSessionDay + 1
    return sessionTemplate.startDate.datesUntil(
      lastBookableSessionDay.plusDays(1), sessionTemplate.frequency.frequencyPeriod
    )
      .map {
        date ->
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
      // remove created VisitSessions which are before the bookable period
      .filter { session -> session.startTimestamp > LocalDateTime.of(firstBookableSessionDay, LocalTime.MIDNIGHT) }
      .toList()
  }

  private fun filterPrisonerConflict(sessions: List<VisitSessionDto>, prisonerId: String): List<VisitSessionDto> {
    return sessions.filterNot {
      sessionHasNonAssociation(it, prisonerId)
    }
  }

  private fun populateBookedCount(sessions: List<VisitSessionDto>) {
    sessions.forEach {
      it.openVisitBookedCount = sessionBookedCount(it, RestrictionType.OPEN)
      it.closedVisitBookedCount = sessionBookedCount(it, RestrictionType.CLOSED)
    }
  }

  private fun sessionHasNonAssociation(session: VisitSessionDto, prisonerId: String): Boolean {
    // Any Non-association withing the session period && Non-association has a RESERVED or BOOKED booking.
    // We could also include ATTENDED booking but as prisons have a minimum notice period they can be ignored.
    return getNonAssociation(prisonerId).any { it ->
      isDateWithinRange(session.startTimestamp.toLocalDate(), it.effectiveDate, it.expiryDate) &&
        it.offenderNonAssociation.let { ona ->
          reservationRepository.findAll(
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
          ).any { it.booking?.let { booking -> isActiveStatus(booking.visitStatus) } ?: false }
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

  private fun sessionBookedCount(session: VisitSessionDto, restriction: RestrictionType): Int {
    return reservationRepository.findAll(
      VisitSpecification(
        VisitFilter(
          prisonId = session.prisonId,
          visitRoom = session.visitRoomName,
          startDateTime = session.startTimestamp,
          endDateTime = session.endTimestamp,
          visitRestriction = restriction
        )
      )
    ).count { it.booking?.let { booking -> isActiveStatus(booking.visitStatus) } ?: true }
  }

  private fun isDateWithinRange(sessionDate: LocalDate, startDate: LocalDate, endDate: LocalDate? = null) =
    sessionDate >= startDate && (endDate == null || sessionDate <= endDate)

  private fun isActiveStatus(status: StatusType) =
    status == StatusType.BOOKED || status == StatusType.RESERVED
}
