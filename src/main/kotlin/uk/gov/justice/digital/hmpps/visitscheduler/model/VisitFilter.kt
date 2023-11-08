package uk.gov.justice.digital.hmpps.visitscheduler.model

import java.time.LocalDateTime

data class VisitFilter(
  val prisonerId: String? = null,
  val prisonCode: String? = null,
  val startDateTime: LocalDateTime? = null,
  val endDateTime: LocalDateTime? = null,
  val visitorId: Long? = null,
  val visitStatusList: List<VisitStatus>,
)
