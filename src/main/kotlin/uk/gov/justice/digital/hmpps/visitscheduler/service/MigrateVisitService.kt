package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.LegacyData
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitSupport
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.repository.LegacyDataRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SupportTypeRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository

@Service
@Transactional
class MigrateVisitService(
  private val legacyDataRepository: LegacyDataRepository,
  private val visitRepository: VisitRepository,
  private val supportTypeRepository: SupportTypeRepository,
) {

  fun migrateVisit(migrateVisitRequest: MigrateVisitRequestDto): VisitDto {
    log.info("Entered migrateVisit create migrated visit for prisoner ${migrateVisitRequest.prisonerId}")
    val visitEntity = visitRepository.saveAndFlush(
      Visit(
        prisonerId = migrateVisitRequest.prisonerId,
        prisonId = migrateVisitRequest.prisonId,
        visitRoom = migrateVisitRequest.visitRoom,
        visitType = migrateVisitRequest.visitType,
        visitStatus = migrateVisitRequest.visitStatus,
        visitRestriction = migrateVisitRequest.visitRestriction,
        visitStart = migrateVisitRequest.startTimestamp,
        visitEnd = migrateVisitRequest.endTimestamp
      )
    )

    migrateVisitRequest.visitContact?.let {
      visitEntity.visitContact = createVisitContact(visitEntity, it.name, it.telephone)
    }

    migrateVisitRequest.visitors?.let { contactList ->
      contactList.distinctBy { it.nomisPersonId }.forEach {
        visitEntity.visitors.add(createVisitVisitor(visitEntity, it.nomisPersonId))
      }
    }

    migrateVisitRequest.visitorSupport?.let { supportList ->
      supportList.distinctBy { it.type }.forEach {
        supportTypeRepository.findByName(it.type)
          ?: throw SupportNotFoundException("Invalid support ${it.type} not found")
        visitEntity.support.add(createVisitSupport(visitEntity, it.type, it.text))
      }
    }

    migrateVisitRequest.visitNotes?.let { visitNotes ->
      visitNotes.distinctBy { it.type }.forEach {
        visitEntity.visitNotes.add(createVisitNote(visitEntity, it.type, it.text))
      }
    }

    migrateVisitRequest.legacyData?.let {
      saveLegacyData(visitEntity, it.leadVisitorId)
    }

    return VisitDto(visitEntity)
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

  private fun createVisitContact(visit: Visit, name: String, telephone: String): VisitContact {
    return VisitContact(
      visitId = visit.id,
      name = name,
      telephone = telephone,
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

  private fun createVisitSupport(visit: Visit, type: String, text: String?): VisitSupport {
    return VisitSupport(
      type = type,
      visitId = visit.id,
      text = text,
      visit = visit
    )
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
