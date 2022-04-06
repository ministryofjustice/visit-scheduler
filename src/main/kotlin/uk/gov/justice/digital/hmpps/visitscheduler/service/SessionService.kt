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

@Service
@Transactional
class SessionService(
  private val sessionTemplateRepository: SessionTemplateRepository,
  private val visitRepository: VisitRepository,
  private val prisonApiClient: PrisonApiClient,
  private val clock: Clock,
  @Value("\${policy.minimum-booking-notice-period-days:2}") private val defaultNoticeDaysMin: Long,
  @Value("\${policy.maximum-booking-notice-period-days:28}") private val defaultNoticeDaysMax: Long,
  @Value("\${policy.non-association-whole-day:true}") private val defaultNonAssociationWholeDay: Boolean,
) {

  @Transactional(readOnly = true)
  fun getVisitSessions(
    prisonId: String,
    prisonerId: String? = null,
    noticeDaysMin: Long? = null,
    noticeDaysMax: Long? = null
  ): List<VisitSessionDto> {
    val bookablePeriodStartDate = LocalDate.now(clock).plusDays(noticeDaysMin ?: defaultNoticeDaysMin)
    val bookablePeriodEndDate = LocalDate.now(clock).plusDays(noticeDaysMax ?: defaultNoticeDaysMax)

    val sessionTemplates = sessionTemplateRepository.findValidSessionTemplatesByPrisonId(
      prisonId,
      bookablePeriodStartDate,
      bookablePeriodEndDate
    )

    var available = sessionTemplates.map {
      buildVisitSessionsUsingTemplate(it, bookablePeriodStartDate, bookablePeriodEndDate)
    }.flatten()

    if (!prisonerId.isNullOrBlank()) {
      available = filterNonAssociationByPrisonerId(available, prisonerId)
    }

    populateBookedCounts(available)

    return available.sortedWith(compareBy { it.startTimestamp })
  }

  fun visitBookedCount(session: VisitSessionDto, restriction: VisitRestriction): Int {
    return visitRepository.findAll(
      VisitSpecification(
        VisitFilter(
          prisonId = session.prisonId,
          startDateTime = session.startTimestamp,
          endDateTime = session.endTimestamp,
          visitRestriction = restriction
        )
      )
    ).count { isActiveStatus(it.visitStatus) }
  }

  // from the start date calculate the slots for based on chosen frequency  (expiry is inclusive)
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

    // Create a VisitSession for every date from the template start date in increments of
    // frequency until the lastBookableSessionDay + 1
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
          visitType = sessionTemplate.visitType,
          restrictions = sessionTemplate.restrictions
        )
      }
      // remove created VisitSessions which are before the bookable period
      .filter { session -> session.startTimestamp > LocalDateTime.of(firstBookableSessionDay, LocalTime.MIDNIGHT) }
      .toList()
  }

  private fun filterNonAssociationByPrisonerId(visitSessions: List<VisitSessionDto>?, prisonerId: String): List<VisitSessionDto> {
    val nonAssociations = getNonAssociation(prisonerId)
    return visitSessions?.filterNot {
      sessionHasNonAssociation(it, nonAssociations, defaultNonAssociationWholeDay)
    } ?: emptyList()
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

  private fun sessionHasNonAssociation(session: VisitSessionDto, nonAssociations: List<OffenderNonAssociationDetailDto>, wholeDay: Boolean? = true): Boolean {
    // Any Non-association withing the session period && Non-association has a RESERVED or BOOKED booking.
    // We could also include ATTENDED booking but as prisons have a minimum notice period they can be ignored.
    return nonAssociations.any { it ->
      isDateWithinRange(session.startTimestamp.toLocalDate(), it.effectiveDate, it.expiryDate) &&
        it.offenderNonAssociation.let { ona ->
          visitRepository.findAll(
            VisitSpecification(
              VisitFilter(
                prisonerId = ona.offenderNo,
                prisonId = session.prisonId,
                startDateTime =
                if (wholeDay == true) session.startTimestamp.toLocalDate().atStartOfDay() else session.startTimestamp,
                endDateTime =
                if (wholeDay == true) session.endTimestamp.toLocalDate().atTime(LocalTime.MAX) else session.endTimestamp
              )
            )
          ).any { isActiveStatus(it.visitStatus) }
        }
    }
  }

  private fun isDateWithinRange(
    sessionDate: LocalDate,
    naStartDate: LocalDate,
    naEndDate: LocalDate? = null
  ): Boolean = sessionDate >= naStartDate && (naEndDate == null || sessionDate <= naEndDate)

  private fun populateBookedCounts(available: List<VisitSessionDto>) {
    available.forEach {
      it.openVisitBookedCount = visitBookedCount(it, VisitRestriction.OPEN)
      it.closedVisitBookedCount = visitBookedCount(it, VisitRestriction.CLOSED)
    }
  }

  private fun isActiveStatus(status: VisitStatus) =
    status == VisitStatus.BOOKED || status == VisitStatus.RESERVED
}
