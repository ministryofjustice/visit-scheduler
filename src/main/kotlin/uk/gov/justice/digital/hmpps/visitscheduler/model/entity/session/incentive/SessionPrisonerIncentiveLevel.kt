package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractIdEntity
import java.time.LocalDateTime

@Entity
@Table(name = "SESSION_PRISONER_INCENTIVE")
class SessionPrisonerIncentiveLevel(

  @Column(name = "SESSION_INCENTIVE_GROUP_ID", nullable = false)
  val sessionIncentiveGroupId: Long,

  @ManyToOne(fetch = FetchType.LAZY, cascade = [CascadeType.DETACH])
  @JoinColumn(name = "SESSION_INCENTIVE_GROUP_ID", updatable = false, insertable = false)
  val sessionIncentiveLevelGroup: SessionIncentiveLevelGroup,

  @Column(name = "code", nullable = false)
  @Enumerated(EnumType.STRING)
  var prisonerIncentiveLevel: IncentiveLevel,

  @CreationTimestamp
  @Column
  val createTimestamp: LocalDateTime? = null,

  @UpdateTimestamp
  @Column
  val modifyTimestamp: LocalDateTime? = null,
) : AbstractIdEntity()
