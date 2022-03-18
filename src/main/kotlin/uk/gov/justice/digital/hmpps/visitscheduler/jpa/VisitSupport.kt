package uk.gov.justice.digital.hmpps.visitscheduler.jpa

import java.io.Serializable
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.EmbeddedId
import javax.persistence.Entity
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@Embeddable
data class VisitSupportPk(
  @Column(name = "SUPPORT_NAME", nullable = false)
  var supportName: String,
  @Column(name = "VISIT_ID", nullable = false)
  var visitId: String,
) : Serializable

@Entity
@Table(name = "VISIT_SUPPORT")
data class VisitSupport(

  @EmbeddedId
  var id: VisitSupportPk,

  @Column(name = "SUPPORT_DETAILS", nullable = true)
  var supportDetails: String? = null,

  @ManyToOne
  @JoinColumn(name = "VISIT_ID", updatable = false, insertable = false)
  val visit: Visit,

)
