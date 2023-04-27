package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.Hibernate

@Entity
@Table(name = "LEGACY_DATA")
class LegacyData(

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ID")
  val id: Long = 0,

  @Column(name = "VISIT_ID", unique = true)
  val visitId: Long,

  @Column(name = "LEAD_PERSON_ID", nullable = true)
  val leadPersonId: Long?,

) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as LegacyData

    return id == other.id
  }

  override fun hashCode(): Int = id.hashCode()

  override fun toString(): String {
    return this::class.simpleName + "(id=$id, leadPersonId=$leadPersonId)"
  }
}
