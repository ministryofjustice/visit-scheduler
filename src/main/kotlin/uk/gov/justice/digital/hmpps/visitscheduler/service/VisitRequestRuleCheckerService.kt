package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonVisitRequestRules
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonVisitRequestRulesRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.visit.request.rules.VisitRequestRuleFactory
import uk.gov.justice.digital.hmpps.visitscheduler.utils.rules.session.SessionRequestInfo

@Service
class VisitRequestRuleCheckerService(
  private val prisonVisitRequestRulesRepository: PrisonVisitRequestRulesRepository,
  private val visitRequestRuleFactory: VisitRequestRuleFactory,
) {
  fun getRequestReviewReasons(prisonCode: String, prisonerId: String, visitorIds: List<Long>?, visitSessions: List<VisitSessionDto>) {
    val sessionRequest = SessionRequestInfo(
      prisonCode = prisonCode,
      prisonerId = prisonerId,
      visitorIds = visitorIds,
      userType = UserType.STAFF,
    )

    val rules = prisonVisitRequestRulesRepository.findActiveVisitRequestRulesByPrison(sessionRequest.prisonCode)
    rules.forEach { rule ->
      checkVisitRequestRule(sessionRequest, visitSessions, rule)
    }
  }

  private fun checkVisitRequestRule(sessionRequest: SessionRequestInfo, visitSessions: List<VisitSessionDto>, prisonVisitRequestRule: PrisonVisitRequestRules) {
    visitRequestRuleFactory.getRuleChecker(prisonVisitRequestRule).ruleCheck(sessionRequest, visitSessions, prisonVisitRequestRule)
  }
}
