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
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import java.time.LocalDateTime

@Entity
@Table(name = "PRISON_USER_CLIENT")
class PrisonUserClient(

  @Column(name = "PRISON_ID", nullable = false)
  var prisonId: Long,

  @ManyToOne
  @JoinColumn(name = "PRISON_ID", updatable = false, insertable = false, nullable = false)
  val prison: Prison,

  @Enumerated(EnumType.STRING)
  @Column(name = "user_type")
  val userType: UserType,

  @Column(name = "active")
  var active: Boolean,

  @CreationTimestamp
  @Column
  val createTimestamp: LocalDateTime? = null,

  @UpdateTimestamp
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
