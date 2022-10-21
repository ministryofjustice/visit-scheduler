package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.ManyToMany
import javax.persistence.Table

@Entity
@Table(name = "SESSION_PRISON_WING")
data class SessionPrisonWing(

  @Column(nullable = false)
  val prisonId: String,

  @Column(nullable = false)
  val name: String,

  @ManyToMany(mappedBy = "prisonWings")
  var sessionTemplates: MutableList<SessionTemplate> = mutableListOf(),

) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
