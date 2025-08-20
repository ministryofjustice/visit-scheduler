package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.audit.EventAuditDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.builder.NotifyHistoryDtoBuilder
import uk.gov.justice.digital.hmpps.visitscheduler.dto.builder.VisitDtoBuilder
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.repository.EventAuditRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository

@Service
@Transactional
class PublicVisitService(
  private val visitRepository: VisitRepository,
  private val eventAuditRepository: EventAuditRepository,
  private val notifyHistoryDtoBuilder: NotifyHistoryDtoBuilder,
) {
  @Autowired
  private lateinit var visitDtoBuilder: VisitDtoBuilder

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getFuturePublicBookedVisitsByBookerReference(bookerReference: String): List<VisitDto> = visitRepository.getPublicFutureBookingsByBookerReference(bookerReference).map { visitDtoBuilder.build(it) }.sortedBy { it.startTimestamp }

  fun getPublicCanceledVisitsByBookerReference(bookerReference: String): List<VisitDto> = visitRepository.getPublicCanceledVisitsByBookerReference(bookerReference).map { visitDtoBuilder.build(it) }.sortedByDescending { it.modifiedTimestamp }

  fun getPublicPastVisitsByBookerReference(bookerReference: String): List<VisitDto> = visitRepository.getPublicPastBookingsByBookerReference(bookerReference).map { visitDtoBuilder.build(it) }.sortedByDescending { it.startTimestamp }

  fun getRelevantVisitEventsByBookerReference(bookerReference: String): List<EventAuditDto> {
    // need to ignore some visit event types e.g., RESERVED_VISIT as they are associated with a visit's application
    val ignoreEventTypes = listOf(
      EventAuditType.RESERVED_VISIT,
    )

    return eventAuditRepository.getVisitEventsByBookingReference(bookerReference, ignoreEventTypes)
      .map { EventAuditDto(it, notifyHistoryDtoBuilder) }
      .sortedByDescending { it.createTimestamp }
  }
}
