package uk.gov.justice.digital.hmpps.visitscheduler.service

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.LegacyData
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.repository.LegacyDataRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository

@Service
@Transactional
class MigrateVisitService(
  private val legacyDataRepository: LegacyDataRepository,
  private val visitRepository: VisitRepository,
  private val telemetryClient: TelemetryClient,
) {

  fun migrateVisit(migrateVisitRequest: MigrateVisitRequestDto): String {

    val visitEntity = visitRepository.saveAndFlush(
      Visit(
        prisonerId = migrateVisitRequest.prisonerId,
        prisonId = migrateVisitRequest.prisonId,
        visitRoom = migrateVisitRequest.visitRoom,
        visitType = migrateVisitRequest.visitType,
        visitStatus = migrateVisitRequest.visitStatus,
        outcomeStatus = migrateVisitRequest.outcomeStatus,
        visitRestriction = migrateVisitRequest.visitRestriction,
        visitStart = migrateVisitRequest.startTimestamp,
        visitEnd = migrateVisitRequest.endTimestamp
      )
    )

    migrateVisitRequest.visitContact?.let {
      visitEntity.visitContact = createVisitContact(visitEntity, it.name, it.telephone)
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

    migrateVisitRequest.legacyData?.let { legacyData ->
      saveLegacyData(visitEntity, legacyData.leadVisitorId)
    }

    telemetryClient.trackEvent(
      "visit-scheduler-prison-visit-migrated",
      mapOf(
        "reference" to visitEntity.reference,
        "prisonerId" to visitEntity.prisonerId,
        "prisonId" to visitEntity.prisonId,
        "visitType" to visitEntity.visitType.name,
        "visitRoom" to visitEntity.visitRoom,
        "visitRestriction" to visitEntity.visitRestriction.name,
        "visitStart" to visitEntity.visitStart.toString(),
        "visitStatus" to visitEntity.visitStatus.name,
        "outcomeStatus" to visitEntity.outcomeStatus?.name
      ),
      null
    )

    return visitEntity.reference
  }

  private fun createVisitNote(visit: Visit, type: VisitNoteType, text: String): VisitNote {
    return VisitNote(
      visitId = visit.id,
      type = type,
      text = text,
      visit = visit
    )
  }

  private fun saveLegacyData(visit: Visit, leadPersonId: Long) {
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
      visit = visit
    )
  }
}
