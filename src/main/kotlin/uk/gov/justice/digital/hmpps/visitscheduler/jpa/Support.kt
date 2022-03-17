package uk.gov.justice.digital.hmpps.visitscheduler.jpa

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "SUPPORT")
data class Support(
  @Id
  @Column(name = "CODE")
  val code: Int,

  @Column(name = "NAME", nullable = false)
  val name: String,

  @Column(name = "DESCRIPTION", nullable = false)
  val description: String,

)
