package uk.gov.justice.digital.hmpps.visitscheduler.utils.rules.session

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Description
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonVisitRequestRuleConfigType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonVisitRequestRuleType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonVisitRequestRules
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository

@Service
@Description("This rule will ensure visits for same prisoner within n days (before or after) are being flagged")
class VisitIntervalVisitRequestRule(
  private val visitRepository: VisitRepository,
) : VisitRequestRule {
  companion object {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override fun ruleCheck(
    sessionRequest: SessionRequestInfo,
    visitSessions: List<VisitSessionDto>,
    prisonVisitRequestRules: PrisonVisitRequestRules,
  ) {
    if (visitSessions.isEmpty()) {
      return
    }

    val prisonCode = sessionRequest.prisonCode
    val prisonerId = sessionRequest.prisonerId

    val rulesConfig = getConfigValues(prisonVisitRequestRules)
    val interval = getInterval(rulesConfig, prisonCode)
    val allowedVisits = getVisitsAllowed(rulesConfig, prisonCode)

    if (interval == null || allowedVisits == null) {
      logger.error("Interval or allowed visits not set or set incorrectly for visit interval rule for prison {}", prisonCode)
      return
    }

    val fromDate = visitSessions.minOf { it.startTimestamp.toLocalDate() }.minusDays(interval.toLong())
    val toDate = visitSessions.maxOf { it.startTimestamp.toLocalDate() }.plusDays(interval.toLong())

    val totalBookedVisitsForPrisonerByDate = visitRepository.getBookedVisitsCountForPrisonerByDate(
      prisonCode = prisonCode,
      prisonerId = prisonerId,
      fromDate = fromDate,
      toDate = toDate,
    )

    visitSessions.forEach { visitSession ->
      val sessionDate = visitSession.startTimestamp.toLocalDate()
      val datePrior = sessionDate.minusDays(interval.toLong())
      val dateAfter = sessionDate.plusDays(interval.toLong())
      val datesPrior = datePrior.datesUntil(sessionDate.plusDays(1)).toList()
      val datesAfter = sessionDate.datesUntil(dateAfter.plusDays(1)).toList()

      val totalBookedVisitsForPrisonerPrior = totalBookedVisitsForPrisonerByDate.filter { visitCountForPrisonerByDate -> visitCountForPrisonerByDate.visitDate in datesPrior }.sumOf { it.bookedVisitsTotal }
      val totalBookedVisitsForPrisonerAfter = totalBookedVisitsForPrisonerByDate.filter { visitCountForPrisonerByDate -> visitCountForPrisonerByDate.visitDate in datesAfter }.sumOf { it.bookedVisitsTotal }

      if ((totalBookedVisitsForPrisonerPrior >= allowedVisits) || (totalBookedVisitsForPrisonerAfter >= allowedVisits)) {
        visitSession.sessionPrisonRuleFailures.add(PrisonVisitRequestRuleType.VISIT_INTERVAL)
      }
    }
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
