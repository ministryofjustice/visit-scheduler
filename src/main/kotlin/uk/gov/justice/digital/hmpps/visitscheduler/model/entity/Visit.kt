package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.NaturalId
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.jpa.repository.Temporal
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.utils.QuotableEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import javax.persistence.CascadeType.ALL
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType.STRING
import javax.persistence.Enumerated
import javax.persistence.FetchType.LAZY
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.OneToMany
import javax.persistence.OneToOne
import javax.persistence.PostPersist
import javax.persistence.Table
import javax.persistence.TemporalType.TIMESTAMP

@Entity
@Table(name = "VISIT")
class Visit(

  @Column(nullable = false)
  var prisonerId: String,

  @Column(name = "PRISON_ID", nullable = false)
  val prisonId: Long,

  @ManyToOne
  @JoinColumn(name = "PRISON_ID", updatable = false, insertable = false)
  val prison: Prison,

  @Column(nullable = false)
  @Enumerated(STRING)
  var visitStatus: VisitStatus,

  @Column(nullable = true)
  @Enumerated(STRING)
  var outcomeStatus: OutcomeStatus? = null,

  @Column(nullable = false)
  @Enumerated(STRING)
  var visitRestriction: VisitRestriction,

  @OneToOne(fetch = LAZY, cascade = [ALL], mappedBy = "visit", orphanRemoval = true)
  var visitContact: VisitContact? = null,

  @OneToMany(fetch = LAZY, cascade = [ALL], mappedBy = "visit", orphanRemoval = true)
  var visitors: MutableList<VisitVisitor> = mutableListOf(),

  @OneToMany(fetch = LAZY, cascade = [ALL], mappedBy = "visit", orphanRemoval = true)
  var support: MutableList<VisitSupport> = mutableListOf(),

  @OneToMany(fetch = LAZY, cascade = [ALL], mappedBy = "visit", orphanRemoval = true)
  var visitNotes: MutableList<VisitNote> = mutableListOf(),

  @CreationTimestamp
  @Temporal(TIMESTAMP)
  @Column
  val createTimestamp: LocalDateTime? = null,

  @UpdateTimestamp
  @Temporal(TIMESTAMP)
  @Column
  val modifyTimestamp: LocalDateTime? = null,

  @Transient
  private val _reference: String = "",

  @Column
  val visitDate: LocalDate,
  @ManyToOne
  @JoinColumn(name = "VISIT_TIME_SLOT_ID", updatable = false, insertable = false)
  val timeSlot: VisitTimeSlot,

  @Column
  val visitTimeSlotId: Long
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ID")
  val id: Long = 0

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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Visit) return false

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }

  override fun toString(): String {
    return "Visit(id=$id,reference='$reference')"
  }
}
