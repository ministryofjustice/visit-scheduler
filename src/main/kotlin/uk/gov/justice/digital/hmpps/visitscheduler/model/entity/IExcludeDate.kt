package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import java.time.LocalDate

interface IExcludeDate {
  val excludeDate: LocalDate

  val actionedBy: String
}
