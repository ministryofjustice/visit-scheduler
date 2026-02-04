package uk.gov.justice.digital.hmpps.visitscheduler.utils.rules

import org.springframework.context.annotation.Description
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository

@Description("This rule will ensure visits for same prisoner within n days are being flagged")
class VisitIntervalRule(
  private val interval: Int,
  private val visitRepository: VisitRepository,
) : Rule<Application> {

  // returns true
  override fun ruleCheck(t: Application): Boolean {
    val prisonerId = t.prisonerId
    val prisonCode = t.prison.code
    val visitDate = t.sessionSlot.slotDate
    val fromDate = visitDate.minusDays(interval.toLong())
    val toDate = visitDate.plusDays(interval.toLong())

    val visits = visitRepository.findBookedVisits(
      prisonCode = prisonCode,
      prisonerId = prisonerId,
      fromDate = fromDate,
      toDate = toDate,
    )
    return visits.isNotEmpty()
  }
}
