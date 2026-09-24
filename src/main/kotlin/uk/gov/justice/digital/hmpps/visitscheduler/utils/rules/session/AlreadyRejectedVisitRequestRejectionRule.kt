package uk.gov.justice.digital.hmpps.visitscheduler.utils.rules.session

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Description
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonVisitRequestRuleConfigType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonVisitRequestRuleType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonVisitRequestRules
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.absoluteValue

@Service
@Description("This rule will reject a visit if a similar request for same prisoner was already rejected in the last n hours")
class AlreadyRejectedVisitRequestRejectionRule(private val visitRepository: VisitRepository) : VisitRequestRule {
  companion object {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override fun ruleCheck(
    sessionRequest: SessionRequestInfo,
    visitSessions: List<VisitSessionDto>,
    prisonVisitRequestRules: PrisonVisitRequestRules,
  ) {
    if (visitSessions.isEmpty() || sessionRequest.visitorIds.isNullOrEmpty()) {
      return
    }

    val prisonCode = sessionRequest.prisonCode
    val prisonerId = sessionRequest.prisonerId
    val fromDate = visitSessions.minOf { it.startTimestamp.toLocalDate() }
    val toDate = visitSessions.maxOf { it.startTimestamp.toLocalDate() }
    val rulesConfig = getConfigValues(prisonVisitRequestRules)
    val rejectionTimeIntervalInHours = getRejectedVisitInterval(rulesConfig, prisonCode)
    val totalRejections = getTotalRejections(rulesConfig, prisonCode)

    if (totalRejections == null || rejectionTimeIntervalInHours == null) {
      logger.error("Total rejections or rejection interval not set or set incorrectly for already rejected visit request rule for prison {}", prisonCode)
      return
    }

    val rejectedSince = LocalDateTime.now().minusHours(rejectionTimeIntervalInHours.toLong())

    val rejectedVisits = visitRepository.getRejectedVisitsForPrisoner(
      prisonCode = prisonCode,
      prisonerId = prisonerId,
      fromDate = fromDate,
      toDate = toDate,
      rejectedSince = rejectedSince,
    ).sortedBy { it.sessionSlot.slotStart }

    visitSessions.forEach {
      if (getTotalRejectionsForSlot(it, rejectedVisits, sessionRequest.visitorIds) >= totalRejections) {
        it.sessionPrisonRuleFailures.add(PrisonVisitRequestRuleType.ALREADY_REJECTED_VISIT)
      }
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

  private fun getTotalRejectionsForSlot(visitSession: VisitSessionDto, rejectedVisits: List<Visit>, visitorIds: List<Long>): Int {
    val slotDate = visitSession.startTimestamp.toLocalDate()
    val sessionTemplateReference = visitSession.sessionTemplateReference
    return rejectedVisits.filter { hasSameSessionSlot(it, sessionTemplateReference, slotDate) && hasSameVisitorList(it, visitorIds) }.size
  }

  private fun hasSameVisitorList(rejectedVisit: Visit, visitorIds: List<Long>?): Boolean {
    // only check visitor list if visitorIds is not null
    return if (visitorIds == null) {
      false
    } else {
      (rejectedVisit.visitors.map { it.nomisPersonId }.distinct().size == visitorIds.distinct().size) &&
        (rejectedVisit.visitors.map { it.nomisPersonId }.containsAll(visitorIds))
    }
  }

  private fun hasSameSessionSlot(rejectedVisit: Visit, sessionTemplateReference: String, sessionDate: LocalDate) = rejectedVisit.sessionSlot.sessionTemplateReference == sessionTemplateReference && rejectedVisit.sessionSlot.slotDate == sessionDate
}
