package uk.gov.justice.digital.hmpps.visitscheduler.model

import java.time.LocalDate

data class VisitFilter(
  val prisonerId: String? = null,
  val prisonCode: String? = null,
  val visitStartDate: LocalDate? = null,
  val visitEndDate: LocalDate? = null,
  val visitStatusList: List<VisitStatus>,
)
