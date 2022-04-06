package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import org.hibernate.annotations.NaturalId
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "SUPPORT_TYPE")
data class SupportType(

  @Id
  @Column(name = "CODE")
  val code: Int,

  @NaturalId
  @Column(name = "NAME", nullable = false, unique = true)
  val name: String,

  @Column(name = "DESCRIPTION", nullable = false)
  val description: String,

)
