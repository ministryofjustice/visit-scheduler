package uk.gov.justice.digital.hmpps.visitscheduler.utils.rules

import org.springframework.context.annotation.Description
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.temporal.TemporalAdjusters

@Description("This rule will ensure visits for same prisoner within n days are being flagged")
class MaxVisitsPerMonthRule(
  private val maxVisitsAllowedPerMonth: Int,
  private val visitRepository: VisitRepository,
) : Rule<Application> {
  override fun ruleCheck(t: Application): Boolean {
    val prisonerId = t.prisonerId
    val prisonCode = t.prison.code
    val visitDate = t.sessionSlot.slotDate
    val fromDate = visitDate.withDayOfMonth(1)
    val toDate = visitDate.with(TemporalAdjusters.lastDayOfMonth())

    val visits = visitRepository.findBookedVisits(
      prisonCode = prisonCode,
      prisonerId = prisonerId,
      fromDate = fromDate,
      toDate = toDate,
    )
    return visits.size >= maxVisitsAllowedPerMonth
  }
}
