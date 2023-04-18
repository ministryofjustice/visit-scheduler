package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractReferenceEntity
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionCategoryGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

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

  @Column(nullable = true)
  val capacityGroup: String? = null,

  @Column(nullable = false)
  val enhanced: Boolean = false,

  @ManyToMany(fetch = FetchType.LAZY, cascade = [CascadeType.REFRESH])
  @JoinTable(
    name = "SESSION_TO_LOCATION_GROUP",
    joinColumns = [JoinColumn(name = "session_template_id")],
    inverseJoinColumns = [JoinColumn(name = "group_id")],
  )
  val permittedSessionLocationGroups: MutableList<SessionLocationGroup> = mutableListOf(),

  @ManyToMany(fetch = FetchType.LAZY, cascade = [CascadeType.REFRESH])
  @JoinTable(
    name = "SESSION_TO_CATEGORY_GROUP",
    joinColumns = [JoinColumn(name = "session_template_id")],
    inverseJoinColumns = [JoinColumn(name = "session_category_group_id")],
  )
  val permittedSessionCategoryGroups: MutableList<SessionCategoryGroup> = mutableListOf(),

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
  val dayOfWeek: DayOfWeek,
) : AbstractReferenceEntity(delimiter = ".", chunkSize = 3)
