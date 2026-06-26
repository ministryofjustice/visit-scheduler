package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerMergeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.repository.ActionedByRepository

@Service
class PrisonerMergeService(
  private val visitService: VisitService,
  private val applicationService: ApplicationService,
  private val actionedByRepository: ActionedByRepository,
  private val visitEventAuditService: VisitEventAuditService,
) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional
  fun handlePrisonerMerge(notificationDto: PrisonerMergeNotificationDto) {
    LOG.info("Prisoner merge notification received : {}", notificationDto)

    // get all affected visits - past, present, future, BOOKED or CANCELLED for the old prisonerId
    val affectedVisits = visitService.getAllVisitsForPrisoner(notificationDto.oldPrisonerId)

    // update the prisoner ID on all visits
    visitService.updateVisitsPrisonerIdPostMerge(oldPrisonerId = notificationDto.oldPrisonerId, newPrisonerId = notificationDto.newPrisonerId)

    // add an event audit entry against all visits
    visitEventAuditService.saveMergeEventAudits(visits = affectedVisits, oldPrisonerNumber = notificationDto.oldPrisonerId, newPrisonerNumber = notificationDto.newPrisonerId)

    // update the prisoner ID on all applications
    applicationService.updateApplicationsPrisonerIdPostMerge(oldPrisonerId = notificationDto.oldPrisonerId, newPrisonerId = notificationDto.newPrisonerId)

    // finally update the actionedBy Prisoner ID
    actionedByRepository.updateActionedByUsername(oldPrisonerId = notificationDto.oldPrisonerId, newPrisonerId = notificationDto.newPrisonerId)
  }
}
