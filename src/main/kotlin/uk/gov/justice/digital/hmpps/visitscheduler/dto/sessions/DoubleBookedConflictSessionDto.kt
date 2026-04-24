package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import java.time.LocalDate

data class DoubleBookedConflictSessionDto(
  val reference: String?,
  val sessionDate: LocalDate,
  val conflictType: SessionConflictType,
  val visitSubStatus: VisitSubStatus?,
  val sessionTemplateReference: String,
)
