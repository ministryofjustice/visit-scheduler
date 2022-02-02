package uk.gov.justice.digital.hmpps.visitscheduler.jpa

import org.hibernate.Hibernate
import java.time.LocalDate
import java.time.LocalTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "SESSION_EXCEPTION")
data class SessionException(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0,

  @Column(nullable = false)
  val sessionTemplateId: String,

  @Column(nullable = false)
  val frequency: String,

  @Column(nullable = false)
  val startTime: LocalTime,

  @Column(nullable = false)
  val endTime: LocalTime,

  @Column(nullable = false)
  val startDate: LocalDate,

  @Column
  val endDate: LocalDate?,

  @Column(nullable = false)
  val closedCapacity: Int,

  @Column(nullable = false)
  val openCapacity: Int,

  @Column
  val reason: String?,

) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as SessionException

    return id == other.id
  }

  override fun hashCode(): Int = 0

  @Override
  override fun toString(): String {
    return this::class.simpleName + "(id = $id )"
  }
}
