package uk.gov.justice.digital.hmpps.visitscheduler.utils.rules

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Description
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonVisitRequestRuleConfigType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonVisitRequestRules
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository

@Service
@Description("This rule will ensure visits for same prisoner within n days are being flagged")
class VisitIntervalRule(
  private val visitRepository: VisitRepository,
) : Rule<Application> {
  companion object {
    private const val DEFAULT_INTERVAL = 2
    val logger: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override fun ruleCheck(t: Application, prisonVisitRequestRules: PrisonVisitRequestRules): Boolean {
    var interval = getInterval(prisonVisitRequestRules)
    if (interval == null) {
      logger.error("Interval not set or set incorrectly for visit interval rulefor prison ${prisonVisitRequestRules.prison.code}, using default interval of $DEFAULT_INTERVAL")
      interval = DEFAULT_INTERVAL
    }

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

  private fun getInterval(prisonVisitRequestRules: PrisonVisitRequestRules): Int? = try {
    prisonVisitRequestRules.prisonVisitRequestRulesConfig.firstOrNull { it.attributeName == PrisonVisitRequestRuleConfigType.NUMBER_OF_DAYS }?.attributeValue?.toIntOrNull()
  } catch (e: NumberFormatException) {
    logger.error("NumberFormatException thrown while getting number of days for visit interval rule : ${prisonVisitRequestRules.ruleName} for prison ${prisonVisitRequestRules.prison.code}", e)
    null
  }
}
