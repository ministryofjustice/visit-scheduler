package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonVisitRequestRuleConfigType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractIdEntity

@Entity
@Table(name = "PRISON_VISIT_REQUEST_RULES_CONFIG")
class PrisonVisitRequestRulesConfig(
  @Column(name = "prison_visit_request_rule_id", nullable = false)
  var prisonVisitRequestRuleId: Long,

  @Column(name = "attribute_name", nullable = false)
  @Enumerated(EnumType.STRING)
  val attributeName: PrisonVisitRequestRuleConfigType,

  @Column(name = "attribute_value", nullable = false)
  val attributeValue: String,

  @ManyToOne
  @JoinColumn(name = "prison_visit_request_rule_id", updatable = false, insertable = false)
  val prisonVisitRequestRule: PrisonVisitRequestRules,
) : AbstractIdEntity()
