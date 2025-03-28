package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.Hibernate


@Entity
@Table(name = "VISIT_FROM_EXTERNAL_SYSTEM_CLIENT_REFERENCE")
class VisitFromExternalSystemClientReference(
    @Id
    var visitId: Long,

    @Column(name = "client_reference", nullable = false)
    var clientReference: String,

    @OneToOne
    @JoinColumn(name = "VISIT_ID", updatable = false, insertable = false)
    var visit: Visit
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as VisitFromExternalSystemClientReference

    return visitId == other.visitId
  }

  override fun hashCode(): Int = visitId.hashCode()

  override fun toString(): String = this::class.simpleName + "(visitId=$visitId, clientReference=$clientReference)"
}
