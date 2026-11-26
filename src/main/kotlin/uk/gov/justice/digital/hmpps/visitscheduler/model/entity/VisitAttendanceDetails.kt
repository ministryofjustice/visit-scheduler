package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSearchSubject
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSearchType
import java.time.LocalTime

@Entity
@Table(name = "VISIT_ATTENDANCE_DETAILS")
class VisitAttendanceDetails(
  @Id
  @Column(name = "VISIT_ID")
  val visitId: Long,

  @OneToOne
  @JoinColumn(name = "VISIT_ID", updatable = false, insertable = false)
  val visit: Visit,

  @Column(name = "SEARCH_SUBJECT")
  @Enumerated(EnumType.STRING)
  var searchSubject: VisitSearchSubject? = null,

  @Column(name = "SEARCH_TYPE")
  @Enumerated(EnumType.STRING)
  var searchType: VisitSearchType? = null,

  @Column
  var actualStartTime: LocalTime? = null,

  @Column
  var actualEndTime: LocalTime? = null,
)
