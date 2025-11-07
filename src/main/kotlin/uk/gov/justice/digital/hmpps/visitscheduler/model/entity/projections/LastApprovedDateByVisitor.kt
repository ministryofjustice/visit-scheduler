package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections

import java.time.LocalDate

interface LastApprovedDateByVisitor {
  val nomisPersonId: Long
  val lastApprovedDate: LocalDate?
}
