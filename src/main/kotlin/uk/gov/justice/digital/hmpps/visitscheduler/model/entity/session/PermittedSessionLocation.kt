package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session

import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.jpa.repository.Temporal
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractIdEntity
import java.time.LocalDateTime
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table
import javax.persistence.TemporalType

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
  val modifyTimestamp: LocalDateTime? = null
) : AbstractIdEntity()
