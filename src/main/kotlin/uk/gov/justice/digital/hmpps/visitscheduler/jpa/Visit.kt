package uk.gov.justice.digital.hmpps.visitscheduler.jpa

import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.NaturalId
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.jpa.repository.Temporal
import uk.gov.justice.digital.hmpps.visitscheduler.utils.QuotableEncoder
import java.time.LocalDateTime
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.OneToMany
import javax.persistence.OneToOne
import javax.persistence.PostPersist
import javax.persistence.Table
import javax.persistence.TemporalType

@Entity
@Table(name = "VISIT")
data class Visit(

  @Column(nullable = false)
  var prisonerId: String,

  @Column(nullable = false)
  var prisonId: String,

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
  var status: VisitStatus,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  var visitRestriction: VisitRestriction,

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], mappedBy = "visit", orphanRemoval = true)
  var visitNotes: MutableList<VisitNote> = mutableListOf(),

  @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], mappedBy = "visit", orphanRemoval = true)
  var mainContact: VisitContact? = null,

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], mappedBy = "visit", orphanRemoval = true)
  var visitors: MutableList<VisitVisitor> = mutableListOf(),

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], mappedBy = "visit", orphanRemoval = true)
  var support: MutableList<VisitSupport> = mutableListOf(),

  @Column
  var sessionTemplateId: Long? = null,

  @CreationTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  @Column
  val createTimestamp: LocalDateTime? = null,

  @UpdateTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  @Column
  var modifyTimestamp: LocalDateTime? = null,
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ID")
  val id: Long = 0

  @NaturalId(mutable = true)
  @Column(name = "REFERENCE", unique = true)
  var reference: String = ""
    private set

  @PostPersist
  fun createReference() {
    reference = QuotableEncoder(minLength = 8).encode(id)
  }
}
