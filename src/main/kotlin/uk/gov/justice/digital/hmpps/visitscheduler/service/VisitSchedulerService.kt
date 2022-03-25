package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.data.CreateSessionTemplateRequest
import uk.gov.justice.digital.hmpps.visitscheduler.data.CreateVisitRequest
import uk.gov.justice.digital.hmpps.visitscheduler.data.OffenderNonAssociationDetail
import uk.gov.justice.digital.hmpps.visitscheduler.data.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.data.SupportTypeDto
import uk.gov.justice.digital.hmpps.visitscheduler.data.UpdateVisitRequest
import uk.gov.justice.digital.hmpps.visitscheduler.data.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.data.VisitSession
import uk.gov.justice.digital.hmpps.visitscheduler.data.filter.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitContact
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitSupport
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.SupportTypeRepository
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.specification.VisitSpecification
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.function.Supplier

@Service
@Transactional
class VisitSchedulerService(
  private val prisonApiClient: PrisonApiClient,
  private val visitRepository: VisitRepository,
  private val sessionTemplateRepository: SessionTemplateRepository,
  private val supportTypeRepository: SupportTypeRepository,
  private val clock: Clock,
  @Value("\${policy.minimum-booking-notice-period-days:2}") private val defaultNoticeDaysMin: Long,
  @Value("\${policy.maximum-booking-notice-period-days:28}") private val defaultNoticeDaysMax: Long,
  @Value("\${policy.non-association-whole-day:true}") private val defaultNonAssociationWholeDay: Boolean,
) {

  @Transactional(readOnly = true)
  fun getVisitByReference(reference: String): VisitDto {
    return VisitDto(visitRepository.findByReference(reference) ?: throw VisitNotFoundException("Visit id  $reference not found"))
  }

  @Transactional(readOnly = true)
  fun getVisitSessions(
    prisonId: String,
    prisonerId: String? = null,
    noticeDaysMin: Long? = null,
    noticeDaysMax: Long? = null
  ): List<VisitSession> {
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

    return available.sortedWith(compareBy { it.startTimestamp })
  }

  private fun filterNonAssociationByPrisonerId(visitSessions: List<VisitSession>?, prisonerId: String): List<VisitSession> {
    val nonAssociations = getNonAssociation(prisonerId)
    return visitSessions?.filterNot {
      sessionHasNonAssociation(it, nonAssociations, defaultNonAssociationWholeDay)
    } ?: emptyList()
  }

  private fun getNonAssociation(prisonerId: String): List<OffenderNonAssociationDetail> {
    try {
      return prisonApiClient.getOffenderNonAssociation(prisonerId)!!.nonAssociations
    } catch (e: WebClientResponseException) {
      if (e.statusCode != HttpStatus.NOT_FOUND)
        throw e
    }
    return emptyList()
  }

  private fun sessionHasNonAssociation(session: VisitSession, nonAssociations: List<OffenderNonAssociationDetail>, wholeDay: Boolean? = true): Boolean {
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
          ).any {
            it.status == VisitStatus.BOOKED || it.status == VisitStatus.RESERVED
          }
        }
    }
  }

  private fun isDateWithinRange(
    sessionDate: LocalDate,
    naStartDate: LocalDate,
    naEndDate: LocalDate? = null
  ): Boolean = sessionDate >= naStartDate && (naEndDate == null || sessionDate <= naEndDate)

  // from the start date calculate the slots for based on chosen frequency  (expiry is inclusive)
  private fun buildVisitSessionsUsingTemplate(
    sessionTemplate: SessionTemplate,
    bookablePeriodStartDate: LocalDate,
    bookablePeriodEndDate: LocalDate
  ): List<VisitSession> {

    val lastBookableSessionDay =
      if (sessionTemplate.expiryDate == null || bookablePeriodEndDate.isBefore(sessionTemplate.expiryDate))
        bookablePeriodEndDate
      else
        sessionTemplate.expiryDate

    val firstBookableSessionDay: LocalDate = if (bookablePeriodStartDate.isAfter(sessionTemplate.startDate))
      bookablePeriodStartDate
    else
      sessionTemplate.startDate

    return sessionTemplate.startDate.datesUntil(
      lastBookableSessionDay.plusDays(1), sessionTemplate.frequency.frequencyPeriod
    )
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
      // remove sessions are before the bookable period
      .filter { session -> session.startTimestamp > LocalDateTime.of(firstBookableSessionDay, LocalTime.MIDNIGHT) }
      .toList()
  }

  fun createSessionTemplate(createSessionTemplateRequest: CreateSessionTemplateRequest): SessionTemplateDto {
    log.info("Creating session template for prison")
    val sessionTemplateEntity = sessionTemplateRepository.saveAndFlush(
      SessionTemplate(
        prisonId = createSessionTemplateRequest.prisonId,
        startTime = createSessionTemplateRequest.startTime,
        endTime = createSessionTemplateRequest.endTime,
        visitType = createSessionTemplateRequest.visitType,
        startDate = createSessionTemplateRequest.startDate,
        expiryDate = createSessionTemplateRequest.expiryDate,
        frequency = createSessionTemplateRequest.frequency,
        visitRoom = createSessionTemplateRequest.visitRoom,
        closedCapacity = createSessionTemplateRequest.closedCapacity,
        openCapacity = createSessionTemplateRequest.openCapacity,
        restrictions = createSessionTemplateRequest.restrictions
      )
    )
    return SessionTemplateDto(
      sessionTemplateEntity
    )
  }

  fun deleteSessionTemplate(sessionTemplateId: Long) {
    val sessionTemplate = sessionTemplateRepository.findByIdOrNull(sessionTemplateId)
    sessionTemplate?.let { sessionTemplateRepository.delete(it) }.also { log.info("Session template with id  $sessionTemplateId deleted") }
      ?: run {
        log.info("Session template with id  $sessionTemplateId not found")
      }
  }

  fun getSessionTemplates(): List<SessionTemplateDto> {
    return sessionTemplateRepository.findAll().sortedBy { it.startDate }.map { SessionTemplateDto(it) }
  }

  fun getSessionTemplates(sessionTemplateId: Long): SessionTemplateDto {
    return sessionTemplateRepository.findById(sessionTemplateId).map { SessionTemplateDto(it) }
      .orElseThrow(TemplateNotFoundException("Template id $sessionTemplateId not found"))
  }

  @Transactional(readOnly = true)
  fun findVisitsByFilter(visitFilter: VisitFilter): List<VisitDto> {
    return visitRepository.findAll(VisitSpecification(visitFilter)).sortedBy { it.visitStart }.map { VisitDto(it) }
  }

  fun createVisit(createVisitRequest: CreateVisitRequest): VisitDto {
    log.info("Creating visit for ${createVisitRequest.prisonerId}")
    val visitEntity = visitRepository.saveAndFlush(
      Visit(
        prisonId = createVisitRequest.prisonId,
        prisonerId = createVisitRequest.prisonerId,
        visitType = createVisitRequest.visitType,
        status = createVisitRequest.visitStatus,
        visitRestriction = createVisitRequest.visitRestriction,
        visitRoom = createVisitRequest.visitRoom,
        visitStart = createVisitRequest.startTimestamp,
        visitEnd = createVisitRequest.endTimestamp,
        visitorConcerns = createVisitRequest.visitorConcerns,
        sessionTemplateId = createVisitRequest.sessionId,
      )
    )

    createVisitRequest.mainContact?.let {
      visitEntity.mainContact = createVisitContact(visitEntity, it.contactName, it.contactPhone)
    }

    createVisitRequest.contactList?.let { contactList ->
      contactList.distinctBy { it.nomisPersonId }.forEach {
        visitEntity.visitors.add(createVisitVisitor(visitEntity, it.nomisPersonId, it.leadVisitor))
      }
    }

    createVisitRequest.supportList?.let { supportList ->
      supportList.distinctBy { it.supportName }.forEach {
        supportTypeRepository.findByName(it.supportName) ?: throw SupportNotFoundException("Invalid support ${it.supportName} not found")
        visitEntity.support.add(createVisitSupport(visitEntity, it.supportName, it.supportDetails))
      }
    }

    return VisitDto(visitEntity)
  }

  fun updateVisit(reference: String, updateVisitRequest: UpdateVisitRequest): VisitDto {
    log.info("Updating visit for $reference")

    val visitEntity = visitRepository.findByReference(reference)
    visitEntity ?: throw VisitNotFoundException("Visit id  $reference not found")

    updateVisitRequest.prisonerId?.let { prisonerId -> visitEntity.prisonerId = prisonerId }
    updateVisitRequest.prisonId?.let { prisonId -> visitEntity.prisonId = prisonId }
    updateVisitRequest.startTimestamp?.let { visitStart -> visitEntity.visitStart = visitStart }
    updateVisitRequest.endTimestamp?.let { visitEnd -> visitEntity.visitEnd = visitEnd }
    updateVisitRequest.visitType?.let { visitType -> visitEntity.visitType = visitType }
    updateVisitRequest.visitStatus?.let { status -> visitEntity.status = status }
    updateVisitRequest.visitRestriction?.let { visitRestriction -> visitEntity.visitRestriction = visitRestriction }
    updateVisitRequest.visitRoom?.let { visitRoom -> visitEntity.visitRoom = visitRoom }
    updateVisitRequest.visitorConcerns?.let { visitorConcerns -> visitEntity.visitorConcerns = visitorConcerns }
    updateVisitRequest.sessionId?.let { sessionId -> visitEntity.sessionTemplateId = sessionId }

    updateVisitRequest.mainContact?.let { updateContact ->
      visitEntity.mainContact?.let { mainContact ->
        mainContact.contactName = updateContact.contactName
        mainContact.contactPhone = updateContact.contactPhone
      } ?: run {
        visitEntity.mainContact = createVisitContact(visitEntity, updateContact.contactName, updateContact.contactPhone)
      }
    }

    updateVisitRequest.contactList?.let { contactList ->
      visitEntity.visitors.clear()
      visitRepository.saveAndFlush(visitEntity)
      contactList.distinctBy { it.nomisPersonId }.forEach {
        visitEntity.visitors.add(createVisitVisitor(visitEntity, it.nomisPersonId, it.leadVisitor))
      }
    }

    updateVisitRequest.supportList?.let { supportList ->
      visitEntity.support.clear()
      visitRepository.saveAndFlush(visitEntity)
      supportList.distinctBy { it.supportName }.forEach {
        supportTypeRepository.findByName(it.supportName) ?: throw SupportNotFoundException("Invalid support ${it.supportName} not found")
        visitEntity.support.add(createVisitSupport(visitEntity, it.supportName, it.supportDetails))
      }
    }

    visitRepository.saveAndFlush(visitEntity)

    return VisitDto(visitEntity)
  }

  private fun createVisitContact(visit: Visit, contactName: String, contactPhone: String): VisitContact {
    return VisitContact(
      visitId = visit.id,
      contactName = contactName,
      contactPhone = contactPhone,
      visit = visit
    )
  }

  private fun createVisitVisitor(visit: Visit, personId: Long, leadVisitor: Boolean): VisitVisitor {
    return VisitVisitor(
      nomisPersonId = personId,
      visitId = visit.id,
      leadVisitor = leadVisitor,
      visit = visit
    )
  }

  private fun createVisitSupport(visit: Visit, supportName: String, supportDetails: String?): VisitSupport {
    return VisitSupport(
      supportName = supportName,
      visitId = visit.id,
      supportDetails = supportDetails,
      visit = visit
    )
  }

  fun deleteVisit(reference: String) {
    val visit = visitRepository.findByReference(reference)
    visit?.let { visitRepository.delete(it) }.also { log.info("Visit with reference $reference deleted") }
      ?: run {
        log.info("Visit reference $reference not found")
      }
  }

  fun getSupportTypes(): List<SupportTypeDto> {
    return supportTypeRepository.findAll().sortedBy { it.code }.map { SupportTypeDto(it) }
  }

  fun deleteAllVisits(expired: List<VisitDto>) {
    visitRepository.deleteAllByReferenceIn(expired.map { it.reference }.toList())
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

class VisitNotFoundException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<VisitNotFoundException> {
  override fun get(): VisitNotFoundException {
    return VisitNotFoundException(message, cause)
  }
}

class TemplateNotFoundException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<TemplateNotFoundException> {
  override fun get(): TemplateNotFoundException {
    return TemplateNotFoundException(message, cause)
  }
}

class SupportNotFoundException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<SupportNotFoundException> {
  override fun get(): SupportNotFoundException {
    return SupportNotFoundException(message, cause)
  }
}
