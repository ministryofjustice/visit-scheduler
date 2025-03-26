package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table


@Entity
@Table(name = "VISIT_PRIVATE_PRISON_CLIENT_REFERENCE",)
class VisitPrivatePrisonClientReference(
    @Id
    val visitId: Long,

    @Column(name = "client_reference", nullable = false)
    val clientReference: String,

    @OneToOne
    @JoinColumn(name = "VISIT_ID", updatable = false, insertable = false)
    val visit: Visit
)