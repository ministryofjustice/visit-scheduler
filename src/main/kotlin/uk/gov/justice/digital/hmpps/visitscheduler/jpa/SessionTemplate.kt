package uk.gov.justice.digital.hmpps.visitscheduler.jpa

import java.time.LocalDate
import java.time.LocalTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
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
  @Enumerated(EnumType.STRING)
  val frequency: SessionFrequency,

  @Column
  val restrictions: String?,

  @Column(nullable = false)
  val startTime: LocalTime,

  @Column(nullable = false)
  val endTime: LocalTime,

  @Column(nullable = false)
  val startDate: LocalDate,

  @Column
  val expiryDate: LocalDate?,

  @Column(nullable = false)
  val closedCapacity: Int,

  @Column(nullable = false)
  val openCapacity: Int,

)
