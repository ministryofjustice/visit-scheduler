package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session

import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.jpa.repository.Temporal
import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table
import javax.persistence.TemporalType

@Entity
@Table(name = "PERMITTED_SESSION_LOCATION")
data class PermittedSessionLocation(

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ID")
  val id: Long = 0,

  @Column(name = "SESSION_TEMPLATE_ID", nullable = false)
  val sessionTemplateID: Long,

  @ManyToOne
  @JoinColumn(name = "SESSION_TEMPLATE_ID", updatable = false, insertable = false)
  val sessionTemplate: SessionTemplate,

  @Column(name = "LEVEL_ONE_CODE", unique = false, nullable = false)
  var levelOneCode: String,
  @Column(name = "LEVEL_TWO_CODE", unique = false)
  var levelTwoCode: String,
  @Column(name = "LEVEL_THREE_CODE", unique = false)
  var levelThreeCode: String,
  @Column(name = "LEVEL_FOUR_CODE", unique = false)
  var levelFourCode: String,

  @CreationTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  @Column
  val createTimestamp: LocalDateTime? = null,

  @UpdateTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  @Column
  val modifyTimestamp: LocalDateTime? = null
)
