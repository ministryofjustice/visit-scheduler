package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.CascadeType.ALL
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonVisitRequestRuleType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractIdEntity

@Entity
@Table(name = "PRISON_VISIT_REQUEST_RULES")
class PrisonVisitRequestRules(
  @Column(name = "prison_id", nullable = false)
  var prisonId: Long,

  @ManyToOne(cascade = [CascadeType.DETACH])
  @JoinColumn(name = "PRISON_ID", updatable = false, insertable = false)
  val prison: Prison,

  @Column(name = "rule_name", nullable = false)
  @Enumerated(EnumType.STRING)
  val ruleName: PrisonVisitRequestRuleType,

  @Column(name = "active", nullable = false)
  val active: Boolean,

  @OneToMany(fetch = FetchType.EAGER, cascade = [ALL], mappedBy = "prisonVisitRequestRule", orphanRemoval = true)
  val prisonVisitRequestRulesConfig: MutableList<PrisonVisitRequestRulesConfig> = mutableListOf(),
) : AbstractIdEntity()
