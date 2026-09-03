package uk.gov.justice.digital.hmpps.visitscheduler.utils.rules

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Description
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonVisitRequestRuleConfigType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonVisitRequestRules
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDateTime
import kotlin.math.absoluteValue

@Service
@Description("This rule will reject a visit if a similar request for same prisoner was already rejected in the last n hours")
class AlreadyRejectedVisitRequestRejectionRule(
  private val visitRepository: VisitRepository,
) : VisitRejectionRequestRule<Application> {
  companion object {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override fun ruleCheck(application: Application, prisonVisitRequestRules: PrisonVisitRequestRules): Boolean {
    val prisonCode = application.prison.code
    val prisonerId = application.prisonerId
    val rulesConfig = getConfigValues(prisonVisitRequestRules)
    val rejectionTimeIntervalInHours = getRejectedVisitInterval(rulesConfig, prisonCode)
    val totalRejections = getTotalRejections(rulesConfig, prisonCode)

    if (rejectionTimeIntervalInHours == null || totalRejections == null) {
      logger.error("Reject interval / Total rejections not set or set incorrectly for already rejected visit request rule for prison {}", prisonCode)
      return false
    }

    val rejectedVisits = visitRepository.getRejectedVisitsForPrisoner(
      prisonCode = prisonCode,
      prisonerId = prisonerId,
      rejectionDateTime = LocalDateTime.now().minusHours(rejectionTimeIntervalInHours.toLong()),
    )

    return if (rejectedVisits.isNotEmpty() && rejectedVisits.size >= totalRejections) {
      // if visit for same time slot and same visitor list has been rejected more than allowed limit in the last n hours, fail the rejection rule
      rejectedVisits.any { rejectedVisit ->
        hasSameSessionSlot(rejectedVisit, application) &&
          hasSameVisitorList(rejectedVisit, application)
      }
    } else {
      false
    }
  }

  private fun getRejectedVisitInterval(configValues: Map<PrisonVisitRequestRuleConfigType, String?>, prisonCode: String): Int? {
    try {
      return configValues[PrisonVisitRequestRuleConfigType.REJECTION_INTERVAL_IN_HOURS]?.toInt()?.absoluteValue
    } catch (_: NumberFormatException) {
      logger.error("NumberFormatException thrown while getting rejection interval for rejection check rule for prison {}", prisonCode)
      return null
    }
  }

  private fun getTotalRejections(configValues: Map<PrisonVisitRequestRuleConfigType, String?>, prisonCode: String): Int? {
    try {
      return configValues[PrisonVisitRequestRuleConfigType.TOTAL_REJECTIONS]?.toInt()?.absoluteValue
    } catch (_: NumberFormatException) {
      logger.error("NumberFormatException thrown while getting total rejections for rejection check rule for prison {}", prisonCode)
      return null
    }
  }

  private fun hasSameVisitorList(rejectedVisit: Visit, application: Application) = (rejectedVisit.visitors.map { it.nomisPersonId }.distinct().size == application.visitors.map { it.nomisPersonId }.distinct().size) &&
    (rejectedVisit.visitors.map { it.nomisPersonId }.containsAll(application.visitors.map { it.nomisPersonId }))

  private fun hasSameSessionSlot(rejectedVisit: Visit, application: Application) = rejectedVisit.sessionSlot.id == application.sessionSlot.id
}
