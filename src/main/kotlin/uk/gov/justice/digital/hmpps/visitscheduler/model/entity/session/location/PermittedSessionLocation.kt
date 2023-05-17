package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.TemporalType
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.jpa.repository.Temporal
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractIdEntity
import java.time.LocalDateTime

@Entity
@Table(name = "PERMITTED_SESSION_LOCATION")
class PermittedSessionLocation(

  @Column(name = "GROUP_ID", nullable = false)
  val groupId: Long,

  @ManyToOne(fetch = FetchType.LAZY, cascade = [CascadeType.DETACH])
  @JoinColumn(name = "GROUP_ID", updatable = false, insertable = false)
  val sessionLocationGroup: SessionLocationGroup,

  @Column(name = "LEVEL_ONE_CODE", unique = false, nullable = false)
  var levelOneCode: String,
  @Column(name = "LEVEL_TWO_CODE", unique = false)
  var levelTwoCode: String? = null,
  @Column(name = "LEVEL_THREE_CODE", unique = false)
  var levelThreeCode: String? = null,
  @Column(name = "LEVEL_FOUR_CODE", unique = false)
  var levelFourCode: String? = null,

  @CreationTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  @Column
  val createTimestamp: LocalDateTime? = null,

  @UpdateTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  @Column
  val modifyTimestamp: LocalDateTime? = null,
) : AbstractIdEntity()
