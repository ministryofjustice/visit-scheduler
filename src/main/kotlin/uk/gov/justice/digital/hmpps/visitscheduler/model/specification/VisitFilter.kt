package uk.gov.justice.digital.hmpps.visitscheduler.model.specification

import uk.gov.justice.digital.hmpps.visitscheduler.model.RestrictionType
import uk.gov.justice.digital.hmpps.visitscheduler.model.StatusType
import java.time.LocalDateTime

data class VisitFilter(
  val prisonerId: String? = null,
  val prisonId: String? = null,
  val visitRoom: String? = null,
  val nomisPersonId: Long? = null,
  val startDateTime: LocalDateTime? = null,
  val endDateTime: LocalDateTime? = null,
  val visitStatus: StatusType? = null,
  val visitRestriction: RestrictionType? = null,
  val createTimestamp: LocalDateTime? = null,
  val modifyTimestamp: LocalDateTime? = null,
)
