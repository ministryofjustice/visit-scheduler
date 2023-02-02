package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session

import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractReferenceEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.JoinColumn
import javax.persistence.JoinTable
import javax.persistence.ManyToMany
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@Table(name = "SESSION_TEMPLATE")
class SessionTemplate(

  @Column(name = "PRISON_ID", nullable = false)
  val prisonId: Long,

  @ManyToOne(cascade = [CascadeType.DETACH])
  @JoinColumn(name = "PRISON_ID", updatable = false, insertable = false)
  val prison: Prison,

  @Column(nullable = false)
  var name: String,

  @Column(nullable = false)
  val visitRoom: String,

  @Column(nullable = false)
  val enhanced: Boolean = false,

  @ManyToMany(fetch = FetchType.LAZY, cascade = [CascadeType.REFRESH])
  @JoinTable(
    name = "SESSION_TO_LOCATION_GROUP",
    joinColumns = [JoinColumn(name = "session_template_id",)],
    inverseJoinColumns = [JoinColumn(name = "group_id")],
  )
  val permittedSessionGroups: MutableList<SessionLocationGroup> = mutableListOf(),

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

  @Column(name = "bi_weekly", nullable = false)
  val biWeekly: Boolean = false,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  val dayOfWeek: DayOfWeek
) : AbstractReferenceEntity(delimiter = ".", chunkSize = 3)
