package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import org.hibernate.Hibernate
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table
import javax.persistence.UniqueConstraint

@Entity
@Table(
  name = "VISIT_NOTES",
  uniqueConstraints = [
    UniqueConstraint(columnNames = ["VISIT_ID", "TYPE"])
  ]
)
data class VisitNote(

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ID")
  val id: Long = 0,

  @Column(name = "VISIT_ID", nullable = false)
  var visitId: Long,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  var type: VisitNoteType,

  @Column(nullable = false)
  var text: String,

  @ManyToOne
  @JoinColumn(name = "VISIT_ID", updatable = false, insertable = false, nullable = false)
  val visit: Visit,

) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as VisitNote

    return id == other.id
  }

  override fun hashCode(): Int = id.hashCode()

  override fun toString(): String {
    return this::class.simpleName + "(id=$id, type=$type)"
  }
}
