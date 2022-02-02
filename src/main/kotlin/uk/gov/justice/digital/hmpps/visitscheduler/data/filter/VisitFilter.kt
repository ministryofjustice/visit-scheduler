package uk.gov.justice.digital.hmpps.visitscheduler.data.filter

import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitStatus
import java.time.LocalDateTime

data class VisitFilter(
  val prisonerId: String? = null,
  val prisonId: String? = null,
  val nomisPersonId: Long? = null,
  val startDateTime: LocalDateTime? = null,
  val endDateTime: LocalDateTime? = null,
  val status: VisitStatus? = null,
  val createTimestamp: LocalDateTime? = null,
  val modifyTimestamp: LocalDateTime? = null,
)
