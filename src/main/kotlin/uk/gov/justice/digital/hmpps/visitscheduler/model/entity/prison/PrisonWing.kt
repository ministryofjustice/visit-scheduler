package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.prison

import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.ManyToMany
import javax.persistence.Table

@Entity
@Table(name = "PRISON_WING")
data class PrisonWing(

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
