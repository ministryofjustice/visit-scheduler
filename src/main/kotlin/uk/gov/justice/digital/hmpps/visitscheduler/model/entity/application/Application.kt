package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application

import jakarta.persistence.CascadeType.ALL
import jakarta.persistence.CascadeType.DETACH
import jakarta.persistence.CascadeType.MERGE
import jakarta.persistence.CascadeType.PERSIST
import jakarta.persistence.CascadeType.REFRESH
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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractIdEntity
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import uk.gov.justice.digital.hmpps.visitscheduler.utils.QuotableEncoder
import java.time.LocalDateTime

@Entity
@Table(name = "APPLICATION")
class Application(

  @Column(name = "PRISON_ID", nullable = false)
  val prisonId: Long,

  @ManyToOne(cascade = [DETACH])
  @JoinColumn(name = "PRISON_ID", updatable = false, insertable = false)
  val prison: Prison,

  @Column(nullable = false)
  var prisonerId: String,

  @Column(name = "SESSION_SLOT_ID", nullable = true)
  var sessionSlotId: Long,

  @ManyToOne
  @JoinColumn(name = "SESSION_SLOT_ID", updatable = false, insertable = false)
  var sessionSlot: SessionSlot,

  @Column(nullable = false)
  var reservedSlot: Boolean = true,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  var visitType: VisitType,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  var restriction: VisitRestriction,

  @Enumerated(EnumType.STRING)
  @Column(name = "user_type", nullable = false)
  val userType: UserType,

  @Column(nullable = false)
  val createdBy: String,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  var applicationStatus: ApplicationStatus,

) : AbstractIdEntity() {

  @Column(name = "VISIT_ID", nullable = true)
  var visitId: Long? = null

  @ManyToOne(fetch = FetchType.LAZY, cascade = [REFRESH, PERSIST, MERGE])
  @JoinColumn(name = "VISIT_ID", updatable = false, insertable = false)
  var visit: Visit? = null

  @OneToOne(fetch = FetchType.LAZY, cascade = [ALL], mappedBy = "application", orphanRemoval = true)
  var visitContact: ApplicationContact? = null

  @OneToMany(fetch = FetchType.LAZY, cascade = [ALL], mappedBy = "application", orphanRemoval = true)
  var visitors: MutableList<ApplicationVisitor> = mutableListOf()

  @OneToOne(fetch = FetchType.LAZY, cascade = [ALL], mappedBy = "application", orphanRemoval = true)
  var support: ApplicationSupport? = null

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
      reference = QuotableEncoder(minLength = 8, chunkSize = 3).encode(id)
    }
  }

  override fun toString(): String = "Application(id=$id,reference='$reference')"
}
