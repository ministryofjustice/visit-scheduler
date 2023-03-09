package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

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
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.OneToMany
import javax.persistence.OneToOne
import javax.persistence.PostPersist
import javax.persistence.Table

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
  var visitRoom: String,

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
  var createdBy: String? = null,

  @Column
  var updatedBy: String? = null,

  @Transient
  private val _reference: String = ""
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
