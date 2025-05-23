package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.Hibernate

@Entity
@Table(
  name = "APPLICATION_SUPPORT",
  uniqueConstraints = [
    UniqueConstraint(columnNames = ["APPLICATION_ID"]),
  ],
)
class ApplicationSupport(

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ID")
  val id: Long = 0,

  @Column(name = "APPLICATION_ID", nullable = false)
  val applicationId: Long,

  @Column(name = "DESCRIPTION", nullable = false)
  var description: String,

  @OneToOne
  @JoinColumn(name = "APPLICATION_ID", updatable = false, insertable = false)
  val application: Application,

) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as ApplicationSupport

    return id == other.id
  }

  override fun hashCode(): Int = id.hashCode()

  override fun toString(): String = this::class.simpleName + "(id=$id, text=$description)"
}
