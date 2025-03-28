package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.TemporalType
import org.hibernate.Hibernate
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.jpa.repository.Temporal
import java.time.LocalDateTime

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

  @CreationTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  @Column
  val migrateDateTime: LocalDateTime? = null,

) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as LegacyData

    return id == other.id
  }

  override fun hashCode(): Int = id.hashCode()

  override fun toString(): String = this::class.simpleName + "(id=$id, leadPersonId=$leadPersonId)"
}
