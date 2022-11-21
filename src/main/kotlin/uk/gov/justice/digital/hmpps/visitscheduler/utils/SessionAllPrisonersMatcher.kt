package uk.gov.justice.digital.hmpps.visitscheduler.utils

import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.util.function.Predicate

class SessionAllPrisonersMatcher : Predicate<SessionTemplate> {
  override fun test(sessionTemplate: SessionTemplate): Boolean {
    return sessionTemplate.permittedSessionLocations.isNullOrEmpty()
  }
}
