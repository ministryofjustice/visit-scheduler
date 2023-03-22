package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ManyToMany
import jakarta.persistence.PreRemove
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractIdEntity
import java.time.LocalDateTime

@Entity
@Table(name = "SESSION_PRISONER_CATEGORY")
class SessionPrisonerCategory(

  @Column(nullable = false)
  var code: String,

) : AbstractIdEntity() {

  @ManyToMany(mappedBy = "excludedPrisonerCategories", fetch = FetchType.LAZY, cascade = [CascadeType.DETACH])
  val excludedSessionTemplate: MutableList<SessionTemplate> = mutableListOf()

  @ManyToMany(mappedBy = "includedPrisonerCategories", fetch = FetchType.LAZY, cascade = [CascadeType.DETACH])
  val includedSessionTemplate: MutableList<SessionTemplate> = mutableListOf()

  @CreationTimestamp
  @Column
  val createTimestamp: LocalDateTime? = null

  @PreRemove
  private fun removeCategories() {
    for (s in excludedSessionTemplate) {
      s.excludedPrisonerCategories.remove(this)
    }
    for (s in includedSessionTemplate) {
      s.includedPrisonerCategories.remove(this)
    }
  }
}
