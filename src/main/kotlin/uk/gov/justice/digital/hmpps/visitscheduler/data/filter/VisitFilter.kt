package uk.gov.justice.digital.hmpps.visitscheduler.data.filter

import java.time.LocalDateTime

data class VisitFilter(
  val prisonerId: String? = null,
  val prisonId: String? = null,
  val nomisPersonId: Long? = null,
  val startDateTime: LocalDateTime? = null,
  val endDateTime: LocalDateTime? = null
)
