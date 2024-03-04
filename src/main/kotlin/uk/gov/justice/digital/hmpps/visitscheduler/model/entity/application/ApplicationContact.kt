package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.Hibernate

@Entity
@Table(name = "APPLICATION_CONTACT")
class ApplicationContact(

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ID")
  val id: Long = 0,

  @Column(name = "APPLICATION_ID", nullable = false)
  var applicationId: Long,

  @Column(name = "CONTACT_NAME", nullable = false)
  var name: String,

  @Column(name = "CONTACT_PHONE", nullable = true)
  var telephone: String?,

  @OneToOne
  @JoinColumn(name = "APPLICATION_ID", updatable = false, insertable = false)
  val application: Application,

) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as ApplicationContact

    return id == other.id
  }

  override fun hashCode(): Int = id.hashCode()

  override fun toString(): String {
    return this::class.simpleName + "(id=$id, name=$name, telephone=$telephone)"
  }
}
