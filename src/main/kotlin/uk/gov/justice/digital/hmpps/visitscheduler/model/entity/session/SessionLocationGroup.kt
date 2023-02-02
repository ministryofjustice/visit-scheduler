package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session

import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractReferenceEntity
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.JoinColumn
import javax.persistence.ManyToMany
import javax.persistence.ManyToOne
import javax.persistence.OneToMany
import javax.persistence.PreRemove
import javax.persistence.Table

@Entity
@Table(name = "SESSION_LOCATION_GROUP")
data class SessionLocationGroup(

  @Column(name = "PRISON_ID", nullable = false)
  val prisonId: Long,

  @Column(nullable = false)
  var name: String,

  @ManyToOne(fetch = FetchType.LAZY, cascade = [CascadeType.DETACH])
  @JoinColumn(name = "PRISON_ID", updatable = false, insertable = false)
  val prison: Prison
) : AbstractReferenceEntity(delimiter = "~", chunkSize = 3) {

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @JoinColumn(name = "GROUP_ID", updatable = false, insertable = true)
  val sessionLocations: MutableList<PermittedSessionLocation> = mutableListOf()

  @ManyToMany(mappedBy = "permittedSessionGroups", fetch = FetchType.LAZY, cascade = [CascadeType.DETACH])
  val sessionTemplates: MutableList<SessionTemplate> = mutableListOf()

  @PreRemove
  private fun removeGroupsFromUsers() {
    for (s in sessionTemplates) {
      s.permittedSessionGroups.remove(this)
    }
  }
}
