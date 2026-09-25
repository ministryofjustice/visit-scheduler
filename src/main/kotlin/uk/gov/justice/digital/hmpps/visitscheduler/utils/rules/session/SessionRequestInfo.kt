package uk.gov.justice.digital.hmpps.visitscheduler.utils.rules.session

import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType

data class SessionRequestInfo(
  val prisonCode: String,
  val prisonerId: String,
  val visitorIds: List<Long>?,
  val userType: UserType,
)
