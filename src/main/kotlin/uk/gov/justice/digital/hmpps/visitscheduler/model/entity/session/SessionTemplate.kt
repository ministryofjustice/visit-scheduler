package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session

import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.JoinTable
import javax.persistence.ManyToMany
import javax.persistence.Table

@Entity
@Table(name = "SESSION_TEMPLATE")
data class SessionTemplate(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0,

  @Column(nullable = false)
  val prisonId: String,

  @Column(nullable = false)
  val visitRoom: String,

  @Column
  @Enumerated(EnumType.STRING)
  val visitType: VisitType,

  @Column(nullable = false)
  val startTime: LocalTime,

  @Column(nullable = false)
  val endTime: LocalTime,

  @Column(nullable = false)
  val validFromDate: LocalDate,

  @Column
  val validToDate: LocalDate?,

  @Column(nullable = false)
  val closedCapacity: Int,

  @Column(nullable = false)
  val openCapacity: Int,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  val dayOfWeek: DayOfWeek,

  @ManyToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
  @JoinTable(
    name = "SESSION_TO_WING",
    joinColumns = [JoinColumn(name = "session_template_id")],
    inverseJoinColumns = [JoinColumn(name = "wing_id")]
  )
  var prisonWings: MutableList<SessionPrisonWing> = mutableListOf()
)
