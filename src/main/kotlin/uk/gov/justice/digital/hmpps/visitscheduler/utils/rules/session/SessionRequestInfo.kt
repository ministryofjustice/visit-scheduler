package uk.gov.justice.digital.hmpps.visitscheduler.utils.rules.session

import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto

data class SessionRequestInfo(
  val prisonCode: String,
  val prisonerId: String,
  val visitSession: VisitSessionDto,
  val visitorIds: List<Long>?,
  val userType: UserType,
)
