package uk.gov.justice.digital.hmpps.visitscheduler.jpa

import org.hibernate.Hibernate
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.OneToOne
import javax.persistence.Table

@Entity
@Table(name = "VISIT_CONTACT")
data class VisitContact(

  @Id
  @Column(name = "VISIT_ID")
  var id: String,

  @Column(name = "CONTACT_NAME", nullable = false)
  var contactName: String,

  @Column(name = "CONTACT_PHONE", nullable = false)
  var contactPhone: String,

  @OneToOne
  @JoinColumn(name = "VISIT_ID", updatable = false, insertable = false)
  val visit: Visit,

) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as VisitContact

    if (id != other.id) return false
    if (contactName != other.contactName) return false
    if (contactPhone != other.contactPhone) return false

    return true
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + contactName.hashCode()
    result = 31 * result + contactPhone.hashCode()
    return result
  }

  override fun toString(): String {
    return this::class.simpleName + "(id=$id, contactName=$contactName, contactPhone=$contactPhone)"
  }
}
