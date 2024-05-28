package uk.gov.justice.digital.hmpps.visitscheduler.model

import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import java.time.LocalDate

data class VisitsBySessionTemplateFilter(
  val sessionTemplateReference: String?,
  val fromDate: LocalDate,
  val toDate: LocalDate,
  val visitStatusList: List<VisitStatus>,
  val visitRestrictions: List<VisitRestriction>?,
)
