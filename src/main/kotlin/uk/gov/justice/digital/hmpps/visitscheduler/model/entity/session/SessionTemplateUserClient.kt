package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session

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
import jakarta.persistence.TemporalType
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.jpa.repository.Temporal
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonUserClient
import java.time.LocalDateTime

@Entity
@Table(name = "SESSION_TEMPLATE_USER_CLIENT")
class SessionTemplateUserClient(

  @Column(name = "SESSION_TEMPLATE_ID", nullable = false)
  var sessionTemplateId: Long,

  @ManyToOne
  @JoinColumn(name = "SESSION_TEMPLATE_ID", updatable = false, insertable = false, nullable = false)
  val sessionTemplate: SessionTemplate,

  @Enumerated(EnumType.STRING)
  @Column(name = "user_type")
  val userType: UserType,

  @Column(name = "active")
  var active: Boolean,

  @CreationTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  @Column
  val createTimestamp: LocalDateTime? = null,

  @UpdateTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  @Column
  val modifyTimestamp: LocalDateTime? = null,
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ID")
  val id: Long = 0

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PrisonUserClient) return false

    if (id != other.id) return false
    return true
  }

  override fun hashCode(): Int = id.hashCode()

  override fun toString(): String = this::class.simpleName + "(id=$id)"
}
