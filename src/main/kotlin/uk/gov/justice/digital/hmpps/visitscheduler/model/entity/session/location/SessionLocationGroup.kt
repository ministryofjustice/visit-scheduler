package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.PreRemove
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractReferenceEntity
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate

@Entity
@Table(name = "SESSION_LOCATION_GROUP")
class SessionLocationGroup(

  @Column(name = "PRISON_ID", nullable = false)
  val prisonId: Long,

  @Column(nullable = false)
  var name: String,

  @ManyToOne(fetch = FetchType.LAZY, cascade = [CascadeType.DETACH])
  @JoinColumn(name = "PRISON_ID", updatable = false, insertable = false)
  val prison: Prison,
) : AbstractReferenceEntity(delimiter = "~", chunkSize = 3) {

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @JoinColumn(name = "GROUP_ID", updatable = false, insertable = true)
  val sessionLocations: MutableList<PermittedSessionLocation> = mutableListOf()

  @ManyToMany(mappedBy = "permittedSessionLocationGroups", fetch = FetchType.LAZY, cascade = [CascadeType.DETACH])
  val sessionTemplates: MutableList<SessionTemplate> = mutableListOf()

  @PreRemove
  private fun removeGroupsFromUsers() {
    for (s in sessionTemplates) {
      s.permittedSessionLocationGroups.remove(this)
    }
  }
}
