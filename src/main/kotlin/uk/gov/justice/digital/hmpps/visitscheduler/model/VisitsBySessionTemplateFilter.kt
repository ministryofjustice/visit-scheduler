package uk.gov.justice.digital.hmpps.visitscheduler.model

import java.time.LocalDate

data class VisitsBySessionTemplateFilter(
  val sessionTemplateReference: String?,
  val fromDate: LocalDate,
  val toDate: LocalDate,
  val visitStatusList: List<VisitStatus>,
  val visitRestrictions: List<VisitRestriction>?,
)
