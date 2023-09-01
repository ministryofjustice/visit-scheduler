package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import java.time.LocalDate

data class NonAssociationChangedNotificationDto(
  val prisonerNumber: String,
  val nonAssociationPrisonerNumber: String,
  val validFromDate: LocalDate,
  val validToDate: LocalDate? = null,
)
