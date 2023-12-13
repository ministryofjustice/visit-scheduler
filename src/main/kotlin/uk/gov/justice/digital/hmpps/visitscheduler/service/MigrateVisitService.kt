package uk.gov.justice.digital.hmpps.visitscheduler.service

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateLegacyContactOnVisitRequestDto.Companion.UNKNOWN_TOKEN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigratedCancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.builder.VisitDtoBuilder
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VisitNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType.CANCELLED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType.MIGRATED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.LegacyData
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.repository.EventAuditRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.LegacyDataRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitService.Companion
import uk.gov.justice.digital.hmpps.visitscheduler.utils.MigrationSessionTemplateMatcher
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

const val NOT_KNOWN_NOMIS = "NOT_KNOWN_NOMIS"

@Service
@Transactional
class MigrateVisitService(
  private val legacyDataRepository: LegacyDataRepository,
  private val visitRepository: VisitRepository,
  private val eventAuditRepository: EventAuditRepository,
  private val prisonsService: PrisonsService,
  private val snsService: SnsService,
  private val migrationSessionTemplateMatcher: MigrationSessionTemplateMatcher,
  private val telemetryClient: TelemetryClient,
  @Value("\${migrate.sessiontemplate.mapping.offset.days:0}")
  private val migrateSessionTemplateMappingOffsetDays: Long,
) {

  @Autowired
  private lateinit var visitDtoBuilder: VisitDtoBuilder

  fun migrateVisit(migrateVisitRequest: MigrateVisitRequestDto): String {
    val actionedBy = migrateVisitRequest.actionedBy ?: NOT_KNOWN_NOMIS
    // Deserialization kotlin data class issue when OutcomeStatus = json type of null defaults do not get set hence below code
    val outcomeStatus = migrateVisitRequest.outcomeStatus ?: OutcomeStatus.NOT_RECORDED

    val prison: Prison
    var sessionTemplateReference: String ? = null
    val visitRoom: String

    if (shouldMigrateWithSessionMapping(migrateVisitRequest)) {
      val sessionTemplate = migrationSessionTemplateMatcher.getMatchingSessionTemplate(migrateVisitRequest)

      prison = sessionTemplate.prison
      sessionTemplateReference = sessionTemplate.reference
      visitRoom = sessionTemplate.visitRoom
    } else {
      prison = prisonsService.findPrisonByCode(migrateVisitRequest.prisonCode)
      visitRoom = migrateVisitRequest.visitRoom
    }

    val visitEntity = visitRepository.saveAndFlush(
      Visit(
        prisonerId = migrateVisitRequest.prisonerId,
        prison = prison,
        prisonId = prison.id,
        visitRoom = visitRoom,
        sessionTemplateReference = sessionTemplateReference,
        visitType = migrateVisitRequest.visitType,
        visitStatus = migrateVisitRequest.visitStatus,
        outcomeStatus = outcomeStatus,
        visitRestriction = migrateVisitRequest.visitRestriction,
        visitStart = migrateVisitRequest.startTimestamp,
        visitEnd = migrateVisitRequest.endTimestamp,
      ),
    )

    val eventAudit = eventAuditRepository.saveAndFlush(
      EventAudit(
        actionedBy = actionedBy,
        bookingReference = visitEntity.reference,
        applicationReference = visitEntity.applicationReference,
        sessionTemplateReference = visitEntity.sessionTemplateReference,
        type = MIGRATED_VISIT,
        applicationMethodType = NOT_KNOWN,
      ),
    )

    migrateVisitRequest.visitContact?.let { contact ->
      visitEntity.visitContact = createVisitContact(
        visitEntity,
        if (UNKNOWN_TOKEN == contact.name || contact.name.partition { it.isLowerCase() }.first.isNotEmpty()) {
          contact.name
        } else {
          capitalise(contact.name)
        },
        contact.telephone,
      )
    }

    migrateVisitRequest.visitors?.let { contactList ->
      contactList.forEach {
        visitEntity.visitors.add(createVisitVisitor(visitEntity, it.nomisPersonId))
      }
    }

    migrateVisitRequest.visitNotes?.let { visitNotes ->
      visitNotes.forEach {
        visitEntity.visitNotes.add(createVisitNote(visitEntity, it.type, it.text))
      }
    }

    saveLegacyData(visitEntity, migrateVisitRequest)

    sendMigratedTrackEvent(visitEntity, TelemetryVisitEvents.VISIT_MIGRATED_EVENT)

    visitRepository.saveAndFlush(visitEntity)

    migrateVisitRequest.createDateTime?.let {
      visitRepository.updateCreateTimestamp(it, visitEntity.id)
      if (migrateVisitRequest.visitStatus == BOOKED) {
        eventAuditRepository.updateCreateTimestamp(it, eventAudit.id)
      }
    }

    // Do this at end of this method, otherwise modify date would be overridden
    migrateVisitRequest.modifyDateTime?.let {
      visitRepository.updateModifyTimestamp(it, visitEntity.id)
      if (migrateVisitRequest.visitStatus == CANCELLED) {
        eventAuditRepository.updateCreateTimestamp(it, eventAudit.id)
      }
    }

    return visitEntity.reference
  }

  private fun shouldMigrateWithSessionMapping(migrateVisitRequest: MigrateVisitRequestDto): Boolean {
    val startDate = LocalDate.now().plusDays(migrateSessionTemplateMappingOffsetDays)
    return !migrateVisitRequest.startTimestamp.toLocalDate().isBefore(startDate)
  }

  fun cancelVisit(reference: String, cancelVisitDto: MigratedCancelVisitDto): VisitDto {
    val cancelOutcome = cancelVisitDto.cancelOutcome

    if (visitRepository.isBookingCancelled(reference)) {
      // If already canceled then just return object and do nothing more!
      VisitService.LOG.debug("The visit $reference has already been canceled!")
      val canceledVisit = visitRepository.findByReference(reference)!!
      return visitDtoBuilder.build(canceledVisit)
    }

    val visitEntity = visitRepository.findBookedVisit(reference) ?: throw VisitNotFoundException("Canceled migrated visit $reference not found ")

    visitEntity.visitStatus = CANCELLED
    visitEntity.outcomeStatus = cancelOutcome.outcomeStatus

    eventAuditRepository.saveAndFlush(
      EventAudit(
        actionedBy = cancelVisitDto.actionedBy,
        bookingReference = visitEntity.reference,
        applicationReference = visitEntity.applicationReference,
        sessionTemplateReference = visitEntity.sessionTemplateReference,
        type = CANCELLED_VISIT,
        applicationMethodType = NOT_KNOWN,
      ),
    )

    cancelOutcome.text?.let {
      visitEntity.visitNotes.add(createVisitNote(visitEntity, VisitNoteType.VISIT_OUTCOMES, cancelOutcome.text))
    }

    sendMigratedTrackEvent(visitEntity, TelemetryVisitEvents.CANCELLED_VISIT_MIGRATED_EVENT)

    val visit = visitDtoBuilder.build(visitEntity)
    snsService.sendVisitCancelledEvent(visit)
    return visit
  }

  private fun sendMigratedTrackEvent(visitEntity: Visit, type: TelemetryVisitEvents) {
    val eventsMap = createVisitTrackEventFromVisitEntity(visitEntity)
    visitEntity.outcomeStatus?.let {
      eventsMap.put("outcomeStatus", it.name)
    }
    trackEvent(
      type.eventName,
      eventsMap,
    )
  }

  private fun createVisitTrackEventFromVisitEntity(visitEntity: Visit): MutableMap<String, String> {
    return mutableMapOf(
      "reference" to visitEntity.reference,
      "prisonerId" to visitEntity.prisonerId,
      "prisonId" to visitEntity.prison.code,
      "visitType" to visitEntity.visitType.name,
      "visitRoom" to visitEntity.visitRoom,
      "sessionTemplateReference" to (visitEntity.sessionTemplateReference ?: ""),
      "visitRestriction" to visitEntity.visitRestriction.name,
      "visitStart" to visitEntity.visitStart.format(DateTimeFormatter.ISO_DATE_TIME),
      "visitStatus" to visitEntity.visitStatus.name,
      "applicationReference" to visitEntity.applicationReference,
    )
  }

  private fun trackEvent(eventName: String, properties: Map<String, String>) {
    try {
      telemetryClient.trackEvent(eventName, properties, null)
    } catch (e: RuntimeException) {
      Companion.LOG.error("Error occurred in call to telemetry client to log event - $e.toString()")
    }
  }

  private fun capitalise(sentence: String): String =
    sentence.lowercase(Locale.getDefault()).split(" ").joinToString(" ") { word ->
      var index = 0
      for (ch in word) {
        if (ch in 'a'..'z') {
          break
        }
        index++
      }
      if (index < word.length) {
        word.replaceRange(index, index + 1, word[index].titlecase(Locale.getDefault()))
      } else {
        word
      }
    }

  private fun createVisitNote(visit: Visit, type: VisitNoteType, text: String): VisitNote {
    return VisitNote(
      visitId = visit.id,
      type = type,
      text = text,
      visit = visit,
    )
  }

  private fun saveLegacyData(visit: Visit, migrateVisitRequestDto: MigrateVisitRequestDto) {
    val legacyData = LegacyData(
      visitId = visit.id,
      leadPersonId = migrateVisitRequestDto.legacyData?.leadVisitorId,
    )

    legacyDataRepository.saveAndFlush(legacyData)
  }

  private fun createVisitContact(visit: Visit, name: String, telephone: String?): VisitContact {
    return VisitContact(
      visitId = visit.id,
      name = name,
      telephone = telephone ?: "",
      visit = visit,
    )
  }

  private fun createVisitVisitor(visit: Visit, personId: Long): VisitVisitor {
    return VisitVisitor(
      nomisPersonId = personId,
      visitId = visit.id,
      visit = visit,
      visitContact = null,
    )
  }
}
