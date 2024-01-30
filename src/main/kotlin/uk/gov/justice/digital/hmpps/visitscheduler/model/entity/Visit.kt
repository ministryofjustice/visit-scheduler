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
import org.hibernate.annotations.UpdateTimestamp
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractIdEntity
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import uk.gov.justice.digital.hmpps.visitscheduler.utils.QuotableEncoder
import java.time.LocalDateTime

@Entity
@Table(name = "VISIT")
class Visit(

  @Column(name = "PRISON_ID", nullable = false)
  val prisonId: Long,

  @ManyToOne(cascade = [CascadeType.DETACH])
  @JoinColumn(name = "PRISON_ID", updatable = false, insertable = false)
  val prison: Prison,

  @Column(nullable = false)
  val prisonerId: String,

  @Column(name = "SESSION_SLOT_ID", nullable = true)
  var sessionSlotId: Long,

  @ManyToOne
  @JoinColumn(name = "SESSION_SLOT_ID", updatable = false, insertable = false)
  var sessionSlot: SessionSlot,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  var visitType: VisitType,

  @Column(nullable = false)
  var visitRoom: String,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  var visitStatus: VisitStatus,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  var visitRestriction: VisitRestriction,
) : AbstractIdEntity() {

  @Column(nullable = true)
  @Enumerated(EnumType.STRING)
  var outcomeStatus: OutcomeStatus? = null

  @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], mappedBy = "visit", orphanRemoval = true)
  lateinit var visitContact: VisitContact

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], mappedBy = "visit", orphanRemoval = true)
  val visitors: MutableList<VisitVisitor> = mutableListOf()

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], mappedBy = "visit", orphanRemoval = true)
  val support: MutableList<VisitSupport> = mutableListOf()

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], mappedBy = "visit", orphanRemoval = true)
  val visitNotes: MutableList<VisitNote> = mutableListOf()

  @OneToMany(mappedBy = "visit", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  val applications: MutableList<Application> = mutableListOf()

  @CreationTimestamp
  @Column
  val createTimestamp: LocalDateTime? = null

  @UpdateTimestamp
  @Column
  val modifyTimestamp: LocalDateTime? = null

  @Column
  var reference: String = ""
    private set

  @PostPersist
  fun createReference() {
    if (reference.isNullOrBlank()) {
      reference = QuotableEncoder(minLength = 8).encode(id)
    }
  }

  override fun toString(): String {
    return "Visit(id=$id,reference='$reference')"
  }
}
