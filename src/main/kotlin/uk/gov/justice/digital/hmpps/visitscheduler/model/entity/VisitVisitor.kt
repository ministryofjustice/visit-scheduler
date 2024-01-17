package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.Hibernate

@Entity
@Table(
  name = "VISIT_VISITOR",
  uniqueConstraints = [
    UniqueConstraint(columnNames = ["VISIT_ID", "NOMIS_PERSON_ID"]),
  ],
)
class VisitVisitor(

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ID")
  val id: Long = 0,

  @Column(name = "VISIT_ID", nullable = false)
  var visitId: Long,

  @Column(name = "NOMIS_PERSON_ID", nullable = false)
  var nomisPersonId: Long,

  @Column(name = "VISIT_CONTACT", nullable = true)
  var visitContact: Boolean?,

  @ManyToOne
  @JoinColumn(name = "VISIT_ID", updatable = false, insertable = false)
  val visit: OldVisit,

  ) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as VisitVisitor

    return id == other.id
  }

  override fun hashCode(): Int = id.hashCode()

  override fun toString(): String {
    return this::class.simpleName + "(id=$id)"
  }
}
