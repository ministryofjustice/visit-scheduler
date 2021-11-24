package uk.gov.justice.digital.hmpps.visitscheduler.jpa

import org.hibernate.Hibernate
import java.io.Serializable
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.EmbeddedId
import javax.persistence.Entity
import javax.persistence.Table

@Embeddable
data class VisitVisitorPk(
  @Column(name = "CONTACT_ID", nullable = false)
  var contactId: Long,
  @Column(name = "VISIT_ID", nullable = false)
  var visitId: Long,
) : Serializable

@Entity
@Table(name = "VISIT_VISITOR")
data class VisitVisitor(

  @EmbeddedId
  val id: VisitVisitorPk,

  @Column(name = "LEAD_VISITOR")
  val leadVisitor: Boolean = true,

) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as VisitVisitor

    return id == other.id
  }

  override fun hashCode(): Int = id.hashCode()

  override fun toString(): String {
    return this::class.simpleName + id.toString()
  }
}
