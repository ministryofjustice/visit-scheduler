package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import java.time.LocalDate

data class NonAssociationConflictSessionDto(
  val prisonerId: String,
  val conflictType: SessionConflictType,
  val reference: String?,
  val sessionDate: LocalDate,
)
