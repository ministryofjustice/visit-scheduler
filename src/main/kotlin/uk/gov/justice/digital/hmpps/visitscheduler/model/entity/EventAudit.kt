package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import jakarta.persistence.CascadeType.REFRESH
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import java.time.LocalDateTime

@Entity
@Table(
  name = "event_audit",
)
class EventAudit private constructor(

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ID")
  val id: Long = 0,

  @Column
  val bookingReference: String? = null,

  @Column
  val applicationReference: String? = null,

  @Column
  val sessionTemplateReference: String? = null,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  val type: EventAuditType,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  val applicationMethodType: ApplicationMethodType,

  @Column(name = "ACTIONED_BY_ID", nullable = true)
  private val actionedById: Long? = null,

  @ManyToOne(fetch = FetchType.LAZY, cascade = [REFRESH])
  @JoinColumn(name = "ACTIONED_BY_ID", updatable = false, insertable = false)
  val actionedBy: ActionedBy,

  @Column(nullable = true)
  val text: String? = null,

  @CreationTimestamp
  @Column
  val createTimestamp: LocalDateTime = LocalDateTime.now(),
) {
  constructor(actionedBy: ActionedBy, bookingReference: String?, applicationReference: String?, sessionTemplateReference: String?, type: EventAuditType, applicationMethodType: ApplicationMethodType, text: String?) : this(
    actionedById = actionedBy.id,
    actionedBy = actionedBy,
    bookingReference = bookingReference,
    applicationReference = applicationReference,
    sessionTemplateReference = sessionTemplateReference,
    type = EventAuditType.valueOf(type.name),
    applicationMethodType = applicationMethodType,
    text = text,
  )
}
