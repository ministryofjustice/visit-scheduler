package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category

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
@Table(name = "SESSION_CATEGORY_GROUP")
class SessionCategoryGroup(

  @Column(name = "PRISON_ID", nullable = false)
  val prisonId: Long,

  @Column(nullable = false)
  var name: String,

  @ManyToOne(fetch = FetchType.LAZY, cascade = [CascadeType.DETACH])
  @JoinColumn(name = "PRISON_ID", updatable = false, insertable = false)
  val prison: Prison,
) : AbstractReferenceEntity(delimiter = "~", chunkSize = 3) {

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @JoinColumn(name = "SESSION_CATEGORY_GROUP_ID", updatable = false, insertable = true)
  val sessionCategories: MutableList<SessionPrisonerCategory> = mutableListOf()

  @ManyToMany(mappedBy = "permittedSessionCategoryGroups", fetch = FetchType.LAZY, cascade = [CascadeType.DETACH])
  val sessionTemplates: MutableList<SessionTemplate> = mutableListOf()

  @PreRemove
  private fun removeCategoryGroupsFromUsers() {
    for (s in sessionTemplates) {
      s.permittedSessionCategoryGroups.remove(this)
    }
  }
}
