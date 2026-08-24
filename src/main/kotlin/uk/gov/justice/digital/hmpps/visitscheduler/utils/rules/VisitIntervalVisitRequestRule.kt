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
@Description("This rule will ensure visits for same prisoner within n days (before or after) are being flagged")
class VisitIntervalVisitRequestRule(
  private val visitRepository: VisitRepository,
) : VisitRequestRule<Application> {
  companion object {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override fun ruleCheck(application: Application, prisonVisitRequestRules: PrisonVisitRequestRules): Boolean {
    val prisonCode = application.prison.code
    val rulesConfig = getConfigValues(prisonVisitRequestRules)
    val interval = getInterval(rulesConfig, prisonCode)
    val allowedVisits = getVisitsAllowed(rulesConfig, prisonCode)

    if (interval == null || allowedVisits == null) {
      logger.error("Interval or allowed visits not set or set incorrectly for visit interval rule for prison {}", prisonCode)
      return false
    }

    val prisonerId = application.prisonerId
    val visitDate = application.sessionSlot.slotDate
    val fromDate = visitDate.minusDays(interval.toLong())
    val toDate = visitDate.plusDays(interval.toLong())

    val totalBookedVisitsForPrisonerPrior = visitRepository.getBookedVisitsCountForPrisoner(
      prisonCode = prisonCode,
      prisonerId = prisonerId,
      fromDate = fromDate,
      toDate = visitDate,
    )

    val totalBookedVisitsForPrisonerAfter = visitRepository.getBookedVisitsCountForPrisoner(
      prisonCode = prisonCode,
      prisonerId = prisonerId,
      fromDate = visitDate,
      toDate = toDate,
    )

    return (
      (totalBookedVisitsForPrisonerPrior >= allowedVisits) ||
        (totalBookedVisitsForPrisonerAfter >= allowedVisits)
      )
  }

  private fun getInterval(configValues: Map<PrisonVisitRequestRuleConfigType, String?>, prisonCode: String): Int? {
    try {
      return configValues[PrisonVisitRequestRuleConfigType.INTERVAL_DAYS_BEFORE_AND_AFTER]?.toInt()
    } catch (_: NumberFormatException) {
      logger.error("NumberFormatException thrown while getting number of days for visit interval rule for prison {}", prisonCode)
      return null
    }
  }

  private fun getVisitsAllowed(configValues: Map<PrisonVisitRequestRuleConfigType, String?>, prisonCode: String): Int? {
    try {
      return configValues[PrisonVisitRequestRuleConfigType.VISITS_ALLOWED]?.toInt()
    } catch (_: NumberFormatException) {
      logger.error("NumberFormatException thrown while getting number of visits allowed for visit interval rule for prison {}", prisonCode)
      return null
    }
  }
}
