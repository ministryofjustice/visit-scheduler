package uk.gov.justice.digital.hmpps.visitscheduler.service

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateLegacyContactOnVisitRequestDto.Companion.UNKNOWN_TOKEN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.LegacyData
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitTimeSlot
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.repository.LegacyDataRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitTimeSlotRepository
import java.util.Locale

@Service
@Transactional
class MigrateVisitService(
  private val legacyDataRepository: LegacyDataRepository,
  private val visitRepository: VisitRepository,
  private val visitTimeSlotRepository: VisitTimeSlotRepository,
  private val prisonConfigService: PrisonConfigService,
  private val telemetryClient: TelemetryClient,
) {

  fun migrateVisit(migrateVisitRequest: MigrateVisitRequestDto): String {

    // Deserialization kotlin data class issue when OutcomeStatus = json type of null defaults do not get set hence below code
    val outcomeStatus = migrateVisitRequest.outcomeStatus ?: OutcomeStatus.NOT_RECORDED

    val prison = prisonConfigService.findPrisonByCode(migrateVisitRequest.prisonCode)
    val visitDate = migrateVisitRequest.startTimestamp.toLocalDate()
    val startTime = migrateVisitRequest.startTimestamp.toLocalTime()
    val endTime = migrateVisitRequest.endTimestamp.toLocalTime()

    var visitTimeSlot = visitTimeSlotRepository.getTimeSlot(
                    prisonId = prison.id,
                    startTime = startTime,
                    endTime = endTime,
                    dayOfWeek = visitDate.dayOfWeek,
                    visitType = migrateVisitRequest.visitType)

    visitTimeSlot?.let {
      visitTimeSlot = visitTimeSlotRepository.saveAndFlush(
        VisitTimeSlot(
          prison = prison,
          prisonId = prison.id,
          visitType = migrateVisitRequest.visitType,
          visitRoom = migrateVisitRequest.visitRoom,
          startTime = startTime,
          endTime   = endTime,
          dayOfWeek = visitDate.dayOfWeek
        )
      )
    }

    val visitEntity = visitRepository.saveAndFlush(
      Visit(
        prisonerId = migrateVisitRequest.prisonerId,
        prisonId = prison.id,
        prison = prison,
        visitStatus = migrateVisitRequest.visitStatus,
        outcomeStatus = outcomeStatus,
        visitRestriction = migrateVisitRequest.visitRestriction,
        visitDate = visitDate,
        timeSlot = visitTimeSlot!!,
        visitTimeSlotId = visitTimeSlot!!.id
      )
    )

    migrateVisitRequest.visitContact?.let { contact ->
      visitEntity.visitContact = createVisitContact(
        visitEntity,
        if (UNKNOWN_TOKEN == contact.name || contact.name.partition { it.isLowerCase() }.first.isNotEmpty())
          contact.name
        else
          capitalise(contact.name),
        contact.telephone
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

    migrateVisitRequest.legacyData?.let {
      saveLegacyData(visitEntity, it.leadVisitorId)
    } ?: run {
      saveLegacyData(visitEntity, null)
    }

    sendTelemetryData(visitEntity,visitTimeSlot!!)

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

  private fun sendTelemetryData(visitEntity: Visit, visitTimeSlot: VisitTimeSlot) {
    telemetryClient.trackEvent(
      TelemetryVisitEvents.VISIT_MIGRATED_EVENT.eventName,
      mapOf(
        "reference" to visitEntity.reference,
        "prisonerId" to visitEntity.prisonerId,
        "prisonId" to visitEntity.prison.code,
        "visitType" to visitTimeSlot.visitType.name,
        "visitRoom" to visitTimeSlot.visitRoom,
        "visitRestriction" to visitEntity.visitRestriction.name,
        "visitStart" to visitEntity.visitDate.toString(),
        "visitStatus" to visitEntity.visitStatus.name,
        "outcomeStatus" to visitEntity.outcomeStatus?.name
      ),
      null
    )
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
      if (index < word.length)
        word.replaceRange(index, index + 1, word[index].titlecase(Locale.getDefault()))
      else
        word
    }

  private fun createVisitNote(visit: Visit, type: VisitNoteType, text: String): VisitNote {
    return VisitNote(
      visitId = visit.id,
      type = type,
      text = text,
      visit = visit
    )
  }

  private fun saveLegacyData(visit: Visit, leadPersonId: Long?) {
    val legacyData = LegacyData(
      visitId = visit.id,
      leadPersonId = leadPersonId
    )

    legacyDataRepository.saveAndFlush(legacyData)
  }

  private fun createVisitContact(visit: Visit, name: String, telephone: String?): VisitContact {
    return VisitContact(
      visitId = visit.id,
      name = name,
      telephone = telephone ?: "",
      visit = visit
    )
  }

  private fun createVisitVisitor(visit: Visit, personId: Long): VisitVisitor {
    return VisitVisitor(
      nomisPersonId = personId,
      visitId = visit.id,
      visit = visit,
      visitContact = null
    )
  }
}
