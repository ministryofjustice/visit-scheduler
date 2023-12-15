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
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractReferenceEntity

@Entity
@Table(name = "VISIT")
class Visit(initialApplication: VisitApplication) : AbstractReferenceEntity(delimiter = ".", chunkSize = 3) {

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], mappedBy = "application", orphanRemoval = true)
  private var applications: MutableList<VisitApplication> = mutableListOf()

  @ManyToOne
  @JoinColumn(name = "session_time_id", updatable = false, insertable = false)
  var sessionTimeSlot: SessionTimeSlot? = null

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  var visitStatus: VisitStatus = BOOKED

  @Column(nullable = true)
  @Enumerated(EnumType.STRING)
  var outcomeStatus: OutcomeStatus? = null

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], mappedBy = "visit", orphanRemoval = true)
  var visitNotes: MutableList<VisitNote> = mutableListOf()

  // initializer block
  init {
    addApplication(initialApplication)
  }

  override fun toString(): String {
    return "Visit(id=$id,reference='$reference')"
  }

  fun addApplication(application: VisitApplication) {
    applications.add(application)
    sessionTimeSlot = application.sessionTimeSlot
  }

  fun getApplications(): List<VisitApplication> {
    return this.applications
  }

  fun getCurrentApplication(): VisitApplication {
    return this.applications.last()
  }
}
