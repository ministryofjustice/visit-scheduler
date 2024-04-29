package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.exception.OverCapacityException
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionSlotRepository

@Service
@Transactional
class SlotCapacityService {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Autowired
  private lateinit var sessionSlotRepository: SessionSlotRepository

  @Autowired
  private lateinit var visitService: VisitService

  @Autowired
  private lateinit var applicationService: ApplicationService

  @Autowired
  private lateinit var sessionTemplateService: SessionTemplateService

  fun checkCapacityForBooking(sessionSlotReference: String, visitRestriction: VisitRestriction, incReservedApplications: Boolean) {
    val sessionSlot = sessionSlotRepository.findByReference(sessionSlotReference)

    if (hasExceededMaxCapacity(sessionSlot, visitRestriction, incReservedApplications)) {
      val messages = "Booking can not be made because capacity has been exceeded for the slot $sessionSlotReference"
      LOG.debug(messages)
      throw OverCapacityException(messages)
    }
  }

  fun checkCapacityForApplicationReservation(sessionSlotReference: String, visitRestriction: VisitRestriction, incReservedApplications: Boolean) {
    val sessionSlot = sessionSlotRepository.findByReference(sessionSlotReference)

    if (hasExceededMaxCapacity(sessionSlot, visitRestriction, true)) {
      val messages = "Application can not be reserved because capacity has been exceeded for the slot $sessionSlotReference"
      LOG.debug(messages)
      throw OverCapacityException(messages)
    }
  }

  private fun hasExceededMaxCapacity(
    sessionSlot: SessionSlot,
    visitRestriction: VisitRestriction,
    incReservedApplications: Boolean,
  ): Boolean {
    var slotTakenCount = visitService.getBookCountForSlot(sessionSlot.id, visitRestriction)

    if (incReservedApplications) {
      slotTakenCount += applicationService.getReservedApplicationsCountForSlot(sessionSlot.id, visitRestriction)
    }

    val maxBookings = sessionTemplateService.getSessionTemplatesCapacity(
      sessionSlot.sessionTemplateReference!!,
      visitRestriction,
    )

    return slotTakenCount >= maxBookings
  }
}
