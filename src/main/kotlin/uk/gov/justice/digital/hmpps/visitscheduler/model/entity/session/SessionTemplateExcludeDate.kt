package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.TemporalType
import org.springframework.data.jpa.repository.Temporal
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.IExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractIdEntity
import java.time.LocalDate

@Entity
@Table(name = "SESSION_TEMPLATE_EXCLUDE_DATE")
class SessionTemplateExcludeDate(

  @Column(name = "SESSION_TEMPLATE_ID", nullable = false)
  var sessionTemplateId: Long,

  @ManyToOne
  @JoinColumn(name = "SESSION_TEMPLATE_ID", updatable = false, insertable = false, nullable = false)
  val sessionTemplate: SessionTemplate,

  @Temporal(TemporalType.DATE)
  @Column
  override val excludeDate: LocalDate,

  @Column(name = "actioned_by", nullable = false)
  override val actionedBy: String,
) : AbstractIdEntity(),
  IExcludeDate
