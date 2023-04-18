package uk.gov.justice.digital.hmpps.visitscheduler.service

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateLegacyContactOnVisitRequestDto.Companion.UNKNOWN_TOKEN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VisitNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.LegacyData
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.LegacyDataRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitService.Companion
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

@Service
@Transactional
class MigrateVisitService(
  private val legacyDataRepository: LegacyDataRepository,
  private val visitRepository: VisitRepository,
  private val prisonConfigService: PrisonConfigService,
  private val snsService: SnsService,
  private val prisonerService: PrisonerService,
  private val sessionTemplateRepository: SessionTemplateRepository,
  private val authenticationHelperService: AuthenticationHelperService,
  private val telemetryClient: TelemetryClient,
) {

  fun migrateVisit(migrateVisitRequest: MigrateVisitRequestDto): String {
    val actionedBy = migrateVisitRequest.actionedBy ?: authenticationHelperService.currentUserName
    // Deserialization kotlin data class issue when OutcomeStatus = json type of null defaults do not get set hence below code
    val outcomeStatus = migrateVisitRequest.outcomeStatus ?: OutcomeStatus.NOT_RECORDED

    val prison = prisonConfigService.findPrisonByCode(migrateVisitRequest.prisonCode)

    val capacityGroup = getSessionCategory(migrateVisitRequest)

    val visitEntity = visitRepository.saveAndFlush(
      Visit(
        prisonerId = migrateVisitRequest.prisonerId,
        prison = prison,
        prisonId = prison.id,
        capacityGroup = capacityGroup,
        visitType = migrateVisitRequest.visitType,
        visitStatus = migrateVisitRequest.visitStatus,
        outcomeStatus = outcomeStatus,
        visitRestriction = migrateVisitRequest.visitRestriction,
        visitStart = migrateVisitRequest.startTimestamp,
        visitEnd = migrateVisitRequest.endTimestamp,
        createdBy = actionedBy,
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
    }

    // Do this at end of this method, otherwise modify date would be overridden
    migrateVisitRequest.modifyDateTime?.let {
      visitRepository.updateModifyTimestamp(it, visitEntity.id)
    }

    return visitEntity.reference
  }

  @Transactional(readOnly = true)
  fun getSessionTemplatesInTimeProximityOrder(prisonCode: String, sessionDate: LocalDate, startTime: LocalTime, endTime: LocalTime): List<SessionTemplateDto> {
    val sessionTemplates = sessionTemplateRepository.findValidSessionTemplatesBy(prisonCode = prisonCode, dayOfWeek = sessionDate.dayOfWeek)
    if (sessionTemplates.isNotEmpty()) {
      val proximityComparator = Comparator<SessionTemplate> { template1, template2 ->
        val session1Proximity = getProximityMinutes(template1.startTime, startTime, template1.endTime, endTime)
        val session2Proximity = getProximityMinutes(template2.startTime, startTime, template2.endTime, endTime)
        if (session1Proximity == session2Proximity) {
          if (template1.validToDate != null && template2.validToDate != null) {
            template2.validToDate.compareTo(template1.validToDate)
          } else {
            if (template1.validToDate != null) {
              1
            } else if (template2.validToDate != null) {
              -1
            } else {
              template2.validFromDate.compareTo(template1.validFromDate)
            }
          }
        } else {
          session1Proximity.compareTo(session2Proximity)
        }
      }

      val sortedSessionTemplates = sessionTemplates.sortedWith(proximityComparator)
      return sortedSessionTemplates.map { SessionTemplateDto(it) }
    }
    return emptyList()
  }

  private fun getProximityMinutes(sessionStartTime: LocalTime, startTime: LocalTime, sessionEndTime: LocalTime, endTime: LocalTime): Int {
    return (
      (sessionStartTime.toSecondOfDay() - startTime.toSecondOfDay()).absoluteValue +
        (sessionEndTime.toSecondOfDay() - endTime.toSecondOfDay()).absoluteValue
      ) / 60
  }

  private fun getSessionCategory(migrateVisitRequest: MigrateVisitRequestDto): String? {
    val sessionDate = migrateVisitRequest.startTimestamp.toLocalDate()
    val startTime = migrateVisitRequest.startTimestamp.toLocalTime()
    val endTime = migrateVisitRequest.endTimestamp.toLocalTime()

    val sessionTemplates = getSessionTemplatesInTimeProximityOrder(
      migrateVisitRequest.prisonCode,
      sessionDate,
      startTime,
      endTime,
    )

    if (sessionTemplates.isNotEmpty()) {
      val isInTheFuture = !migrateVisitRequest.startTimestamp.isBefore(LocalDateTime.now())
      if (isInTheFuture) {
        val sessionMatchingPrisonerLocation = getSessionMatchingPrisonerLocation(migrateVisitRequest, sessionTemplates)
        sessionMatchingPrisonerLocation?.let {
          return it.capacityGroup
        }
      }
      return sessionTemplates.first().capacityGroup
    }
    return null
  }

  private fun getSessionMatchingPrisonerLocation(
    migrateVisitRequest: MigrateVisitRequestDto,
    sessionTemplates: List<SessionTemplateDto>,
  ): SessionTemplateDto? {
    val location = prisonerService.getPermittedSessionLocationForPrisoner(
      migrateVisitRequest.prisonerId,
      migrateVisitRequest.prisonCode,
    )
    location?.let {
      val result = sessionTemplates.firstOrNull { st ->
        if (st.permittedLocationGroups.isNotEmpty()) {
          st.permittedLocationGroups.firstOrNull { lg ->
            lg.locations.firstOrNull { (it == location) } != null
          } != null
        } else {
          false
        }
      }
      result?.let { return it }
    }
    return null
  }

  fun cancelVisit(reference: String, cancelVisitDto: CancelVisitDto): VisitDto {
    val cancelOutcome = cancelVisitDto.cancelOutcome

    if (visitRepository.isBookingCancelled(reference)) {
      // If already canceled then just return object and do nothing more!
      VisitService.LOG.debug("The visit $reference has already been canceled!")
      val canceledVisit = visitRepository.findByReference(reference)!!
      return VisitDto(canceledVisit)
    }

    val visitEntity = visitRepository.findBookedVisit(reference) ?: throw VisitNotFoundException("Canceled migrated visit $reference not found ")

    visitEntity.visitStatus = CANCELLED
    visitEntity.outcomeStatus = cancelOutcome.outcomeStatus
    visitEntity.cancelledBy = cancelVisitDto.actionedBy

    cancelOutcome.text?.let {
      visitEntity.visitNotes.add(createVisitNote(visitEntity, VisitNoteType.VISIT_OUTCOMES, cancelOutcome.text))
    }

    sendMigratedTrackEvent(visitEntity, TelemetryVisitEvents.CANCELLED_VISIT_MIGRATED_EVENT)

    val visit = VisitDto(visitRepository.saveAndFlush(visitEntity))
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
      "capacityGroup" to (visitEntity.capacityGroup ?: ""),
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
      visitRoom = migrateVisitRequestDto.visitRoom,
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
