package uk.gov.justice.digital.hmpps.visitscheduler.jpa

import org.hibernate.Hibernate
import java.io.Serializable
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.EmbeddedId
import javax.persistence.Entity
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@Embeddable
data class VisitSupportPk(
  @Column(name = "SUPPORT_NAME", nullable = false)
  var supportName: String,
  @Column(name = "VISIT_ID", nullable = false)
  var visitId: String,
) : Serializable

@Entity
@Table(name = "VISIT_SUPPORT")
data class VisitSupport(

  @EmbeddedId
  var id: VisitSupportPk,

  @Column(name = "SUPPORT_DETAILS", nullable = true)
  var supportDetails: String? = null,

  @ManyToOne
  @JoinColumn(name = "VISIT_ID", updatable = false, insertable = false)
  val visit: Visit,

) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as VisitSupport

    if (id != other.id) return false
    if (supportDetails != other.supportDetails) return false

    return true
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + (supportDetails?.hashCode() ?: 0)
    return result
  }

  override fun toString(): String {
    return this::class.simpleName + "(id=$id, supportDetails=$supportDetails)"
  }
}
