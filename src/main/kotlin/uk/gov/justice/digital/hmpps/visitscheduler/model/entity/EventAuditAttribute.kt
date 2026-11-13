package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditAttributeTypes

@Entity
@Table(name = "event_audit_attribute")
class EventAuditAttribute(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  open val id: Long = 0,

  @Column(name = "event_audit_id", unique = false, nullable = false)
  val eventAuditId: Long,

  @Column(name = "attribute_name", nullable = false)
  @Enumerated(EnumType.STRING)
  val attributeName: EventAuditAttributeTypes,

  @Column(name = "attribute_value", nullable = false)
  val attributeValue: String,

  @ManyToOne
  @JoinColumn(name = "event_audit_id", updatable = false, insertable = false)
  val eventAudit: EventAudit,
)
