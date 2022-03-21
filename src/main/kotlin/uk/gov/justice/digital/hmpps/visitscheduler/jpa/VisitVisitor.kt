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
data class VisitVisitorPk(
  @Column(name = "NOMIS_PERSON_ID", nullable = false)
  var nomisPersonId: Long,
  @Column(name = "VISIT_ID", nullable = false)
  var visitId: String,
) : Serializable

@Entity
@Table(name = "VISIT_VISITOR")
data class VisitVisitor(

  @EmbeddedId
  val id: VisitVisitorPk,

  @Column(name = "LEAD_VISITOR")
  val leadVisitor: Boolean = true,

  @ManyToOne
  @JoinColumn(name = "VISIT_ID", updatable = false, insertable = false)
  val visit: Visit,

) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as VisitVisitor

    if (id != other.id) return false
    if (leadVisitor != other.leadVisitor) return false

    return true
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + leadVisitor.hashCode()
    return result
  }

  override fun toString(): String {
    return this::class.simpleName + "(id=$id, leadVisitor=$leadVisitor)"
  }
}
