package uk.gov.justice.digital.hmpps.visitscheduler.jpa

import org.hibernate.Hibernate
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
import javax.persistence.Table

@Entity
@Table(name = "VISIT")
data class Visit(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0,

  @Column(nullable = false)
  val prisonerId: String,

  @Column(nullable = false)
  val prisonId: String,

  @Column(nullable = false)
  val visitRoom: String,

  @Column(nullable = false)
  val visitStart: LocalDateTime,

  @Column(nullable = false)
  val visitEnd: LocalDateTime,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  val visitType: VisitType,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  val status: VisitStatus,

  @Column
  val reasonableAdjustments: String?,

  @Column
  val sessionTemplateId: Long?,

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], mappedBy = "visit", orphanRemoval = true)
  val visitors: MutableList<VisitVisitor> = mutableListOf(),

  @Column
  val createTimestamp: LocalDateTime? = LocalDateTime.now(),

  @Column
  val modifyTimestamp: LocalDateTime? = LocalDateTime.now()

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
