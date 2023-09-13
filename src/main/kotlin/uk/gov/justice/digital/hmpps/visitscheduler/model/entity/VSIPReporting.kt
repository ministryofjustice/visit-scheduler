package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.visitscheduler.model.VSIPReport
import java.time.LocalDate

@Entity
@Table(name = "VSIP_REPORTING")
class VSIPReporting(
  @Id
  @Column(name = "REPORT_NAME", nullable = false)
  @Enumerated(EnumType.STRING)
  val reportName: VSIPReport,

  @Column(name = "LAST_REPORT_DATE", nullable = true)
  val lastReportDate: LocalDate?,
)
