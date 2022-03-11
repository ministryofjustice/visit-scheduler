package uk.gov.justice.digital.hmpps.visitscheduler.jpa

import org.hibernate.Hibernate
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.GenericGenerator
import org.hibernate.annotations.Parameter
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.jpa.repository.Temporal
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
import javax.persistence.Table
import javax.persistence.TemporalType

@Entity
@Table(name = "VISIT")
data class Visit(
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "visit_seq_generator")
  @GenericGenerator(
    name = "visit_seq_generator",
    strategy = "uk.gov.justice.digital.hmpps.visitscheduler.jpa.generator.ReferenceIdGenerator",
    parameters = [
      Parameter(name = org.hibernate.id.enhanced.SequenceStyleGenerator.SEQUENCE_PARAM, value = "visit_seq"),
    ]
  )
  val id: String = "",

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

  @Column
  var reasonableAdjustments: String? = null,

  @Column
  var visitorConcerns: String? = null,

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], mappedBy = "visit", orphanRemoval = true)
  var visitors: MutableList<VisitVisitor> = mutableListOf(),

  @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], mappedBy = "visit", orphanRemoval = true)
  var mainContact: VisitContact? = null,

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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as Visit

    return id == other.id
  }

  override fun hashCode(): Int = 0

  @Override
  override fun toString(): String {
    return this::class.simpleName + "(id = $id )"
  }
}
