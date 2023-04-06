package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.PostPersist
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.NaturalId
import org.hibernate.annotations.UpdateTimestamp
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractIdEntity
import uk.gov.justice.digital.hmpps.visitscheduler.utils.QuotableEncoder
import java.time.LocalDateTime

@Entity
@Table(name = "VISIT")
class Visit(

  @Column(nullable = false)
  var prisonerId: String,

  @Column(name = "PRISON_ID", nullable = false)
  val prisonId: Long,

  @ManyToOne(cascade = [CascadeType.DETACH])
  @JoinColumn(name = "PRISON_ID", updatable = false, insertable = false)
  val prison: Prison,

  @Column(nullable = false)
  var capacityGroup: String,

  @Column(nullable = false)
  var visitStart: LocalDateTime,

  @Column(nullable = false)
  var visitEnd: LocalDateTime,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  var visitType: VisitType,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  var visitStatus: VisitStatus,

  @Column(nullable = true)
  @Enumerated(EnumType.STRING)
  var outcomeStatus: OutcomeStatus? = null,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  var visitRestriction: VisitRestriction,

  @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], mappedBy = "visit", orphanRemoval = true)
  var visitContact: VisitContact? = null,

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], mappedBy = "visit", orphanRemoval = true)
  var visitors: MutableList<VisitVisitor> = mutableListOf(),

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], mappedBy = "visit", orphanRemoval = true)
  var support: MutableList<VisitSupport> = mutableListOf(),

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], mappedBy = "visit", orphanRemoval = true)
  var visitNotes: MutableList<VisitNote> = mutableListOf(),

  @Column
  var createdBy: String,

  @Column
  var updatedBy: String? = null,

  @Column
  var cancelledBy: String? = null,

  @Transient
  private val _reference: String = "",
) : AbstractIdEntity() {

  @CreationTimestamp
  @Column
  val createTimestamp: LocalDateTime? = null

  @UpdateTimestamp
  @Column
  val modifyTimestamp: LocalDateTime? = null

  @Column
  var reference = _reference

  @Column
  @NaturalId(mutable = true)
  var applicationReference: String = ""

  @PostPersist
  fun createReference() {
    if (_reference.isBlank()) {
      reference = QuotableEncoder(minLength = 8).encode(id)
    }
    if (applicationReference.isBlank()) {
      applicationReference = QuotableEncoder(minLength = 8, chunkSize = 3).encode(id)
    }
  }

  override fun toString(): String {
    return "Visit(id=$id,reference='$reference')"
  }
}
