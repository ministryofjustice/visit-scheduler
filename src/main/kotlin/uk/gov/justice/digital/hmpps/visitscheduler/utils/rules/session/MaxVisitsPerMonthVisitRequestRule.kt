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
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters
import java.util.function.BiPredicate

@Service
@Description("This rule will ensure visits above the allowed limit for the month are being flagged")
class MaxVisitsPerMonthVisitRequestRule(
  private val visitRepository: VisitRepository,
) : VisitRequestRule {
  companion object {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)
    val dateFilter: BiPredicate<LocalDate, Pair<Month, Int>> = { date, monthAndYear -> date.month == monthAndYear.first && date.year == monthAndYear.second }
  }

  override fun ruleCheck(
    sessionRequest: SessionRequestInfo,
    visitSessions: List<VisitSessionDto>,
    prisonVisitRequestRules: PrisonVisitRequestRules,
  ) {
    if (visitSessions.isEmpty()) {
      return
    }

    val maxVisitsAllowedPerMonth = getMaxVisitsPerMonth(prisonVisitRequestRules)
    if (maxVisitsAllowedPerMonth == null) {
      logger.error("Max visits not set or set incorrectly for max visits a month rule for prison ${prisonVisitRequestRules.prison.code}")
      return
    }

    val prisonerId = sessionRequest.prisonerId
    val prisonCode = sessionRequest.prisonCode
    val sessionDates = visitSessions.map { it.startTimestamp.toLocalDate() }.sorted()
    val fromDate = sessionDates.first().with(TemporalAdjusters.firstDayOfMonth())
    val toDate = sessionDates.last().with(TemporalAdjusters.lastDayOfMonth())

    val totalBookedVisitsForPrisonerInAMonthAndYear = visitRepository.getBookedVisitsCountForPrisonerByMonthAndYear(
      prisonCode = prisonCode,
      prisonerId = prisonerId,
      fromDate = fromDate,
      toDate = toDate,
    )

    totalBookedVisitsForPrisonerInAMonthAndYear.associate { Pair(Month.of(it.month), it.year) to it.bookedVisitsTotal }.forEach { (monthAndYear, bookedVisitsTotal) ->
      if (bookedVisitsTotal >= maxVisitsAllowedPerMonth) {
        visitSessions.filter { dateFilter.test(it.startTimestamp.toLocalDate(), monthAndYear) }.forEach { it.sessionPrisonRuleFailures.add(PrisonVisitRequestRuleType.VISITS_PER_MONTH) }
      }
    }
  }

  private fun getMaxVisitsPerMonth(prisonVisitRequestRules: PrisonVisitRequestRules): Int? = try {
    prisonVisitRequestRules.prisonVisitRequestRulesConfig.firstOrNull { it.attributeName == PrisonVisitRequestRuleConfigType.MAX_VISITS_PER_MONTH }?.attributeValue?.toIntOrNull()
  } catch (e: NumberFormatException) {
    logger.error("NumberFormatException thrown for max visits a month rule : ${prisonVisitRequestRules.ruleName} for prison ${prisonVisitRequestRules.prison.code}", e)
    null
  }
}
