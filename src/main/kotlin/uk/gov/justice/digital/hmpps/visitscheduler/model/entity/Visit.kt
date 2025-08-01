package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.CascadeType.ALL
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.OrderBy
import jakarta.persistence.PostPersist
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus.ACCEPTED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractIdEntity
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
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
  var visitSubStatus: VisitSubStatus,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  var visitRestriction: VisitRestriction,

  @Enumerated(EnumType.STRING)
  @Column(name = "user_type", nullable = false)
  val userType: UserType,

) : AbstractIdEntity() {

  @Column(nullable = true)
  @Enumerated(EnumType.STRING)
  var outcomeStatus: OutcomeStatus? = null

  @OneToOne(fetch = FetchType.LAZY, cascade = [ALL], mappedBy = "visit", orphanRemoval = true)
  var visitContact: VisitContact? = null

  @OneToMany(fetch = FetchType.LAZY, cascade = [ALL], mappedBy = "visit", orphanRemoval = true)
  val visitors: MutableList<VisitVisitor> = mutableListOf()

  @OneToOne(fetch = FetchType.LAZY, cascade = [ALL], mappedBy = "visit", orphanRemoval = true)
  var support: VisitSupport? = null

  @OneToMany(fetch = FetchType.LAZY, cascade = [ALL], mappedBy = "visit", orphanRemoval = true)
  val visitNotes: MutableList<VisitNote> = mutableListOf()

  @OneToMany(fetch = FetchType.LAZY, cascade = [ALL], mappedBy = "visit", orphanRemoval = true)
  val visitNotificationEvents: MutableList<VisitNotificationEvent> = mutableListOf()

  @OrderBy("id")
  @OneToMany(fetch = FetchType.LAZY, cascade = [ALL], mappedBy = "visit", orphanRemoval = true)
  private val applications: MutableList<Application> = mutableListOf()

  @CreationTimestamp
  @Column
  val createTimestamp: LocalDateTime? = null

  @UpdateTimestamp
  @Column
  val modifyTimestamp: LocalDateTime? = null

  @Column
  var reference: String = ""
    private set

  @OneToOne(fetch = FetchType.LAZY, cascade = [ALL], mappedBy = "visit", orphanRemoval = true)
  var visitExternalSystemDetails: VisitExternalSystemDetails? = null

  @PostPersist
  fun createReference() {
    if (reference.isBlank()) {
      reference = QuotableEncoder(minLength = 8).encode(id)
    }
  }

  override fun toString(): String = "Visit(id=$id,reference='$reference')"

  fun getApplications(): List<Application> = this.applications

  fun getLastApplication(): Application? = this.applications.lastOrNull()

  fun getLastCompletedApplication(): Application? = this.applications.lastOrNull { it.applicationStatus == ACCEPTED }

  fun addApplication(application: Application) {
    applications.add(application)
    application.visitId = this.id
    application.visit = this
  }
}
