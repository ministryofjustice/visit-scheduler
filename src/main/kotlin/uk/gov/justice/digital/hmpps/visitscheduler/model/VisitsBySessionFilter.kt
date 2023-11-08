package uk.gov.justice.digital.hmpps.visitscheduler.model

import java.time.LocalDate

data class VisitsBySessionFilter(
  val sessionTemplateReference: String,
  val fromDate: LocalDate?,
  val toDate: LocalDate?,
  val visitStatusList: List<VisitStatus>,
  )
