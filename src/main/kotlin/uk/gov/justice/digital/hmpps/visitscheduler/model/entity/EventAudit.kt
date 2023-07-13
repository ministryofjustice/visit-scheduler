package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType
import java.time.LocalDateTime

@Entity
@Table(
  name = "event_audit",
)
class EventAudit(

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ID")
  val id: Long = 0,

  @Column
  var bookingReference: String? = null,

  @Column
  var applicationReference: String? = null,

  @Column
  var sessionTemplateReference: String? = null,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  var type: EventAuditType,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  var applicationMethodType: ApplicationMethodType,

  @Column(nullable = false)
  var actionedBy: String,

  @CreationTimestamp
  @Column
  val createTimestamp: LocalDateTime = LocalDateTime.now(),
)
