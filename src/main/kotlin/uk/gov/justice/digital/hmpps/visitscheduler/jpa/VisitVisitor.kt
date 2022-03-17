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
data class VisitVisitorPk(
  @Column(name = "NOMIS_PERSON_ID", nullable = false)
  var nomisPersonId: Long,
  @Column(name = "VISIT_ID", nullable = false)
  var visitId: String,
) : Serializable

@Entity
@Table(name = "VISIT_VISITOR")
data class VisitVisitor(

  @EmbeddedId
  val id: VisitVisitorPk,

  @Column(name = "LEAD_VISITOR")
  val leadVisitor: Boolean = true,

  @ManyToOne
  @JoinColumn(name = "VISIT_ID", updatable = false, insertable = false)
  val visit: Visit,

)
