package uk.gov.justice.digital.hmpps.visitscheduler.utils.rules

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Description
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonVisitRequestRuleConfigType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonVisitRequestRules
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.temporal.TemporalAdjusters

@Service
@Description("This rule will ensure visits for same prisoner within n days are being flagged")
class MaxVisitsPerMonthRule(
  private val visitRepository: VisitRepository,
) : Rule<Application> {
  companion object {
    private const val DEFAULT_MAX_VISITS_PER_MONTH = 7
    val logger: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override fun ruleCheck(t: Application, prisonVisitRequestRules: PrisonVisitRequestRules): Boolean {
    var maxVisitsAllowedPerMonth = getMaxVisitsPerMonth(prisonVisitRequestRules)
    if (maxVisitsAllowedPerMonth == null) {
      logger.error("Max visits not set or set incorrectly for max visits a month rule for prison ${prisonVisitRequestRules.prison.code}, using default interval of $DEFAULT_MAX_VISITS_PER_MONTH")
      maxVisitsAllowedPerMonth = DEFAULT_MAX_VISITS_PER_MONTH
    }

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

  private fun getMaxVisitsPerMonth(prisonVisitRequestRules: PrisonVisitRequestRules): Int? = try {
    prisonVisitRequestRules.prisonVisitRequestRulesConfig.firstOrNull { it.attributeName == PrisonVisitRequestRuleConfigType.MAX_VISITS_PER_MONTH }?.attributeValue?.toIntOrNull()
  } catch (e: NumberFormatException) {
    logger.error("NumberFormatException thrown whilemax visits per month for max visits a month rule : ${prisonVisitRequestRules.ruleName} for prison ${prisonVisitRequestRules.prison.code}", e)
    null
  }
}
