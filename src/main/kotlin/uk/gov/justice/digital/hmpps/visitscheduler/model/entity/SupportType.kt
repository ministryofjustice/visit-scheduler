package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import org.hibernate.annotations.NaturalId
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "SUPPORT_TYPE")
class SupportType(

  @Id
  @Column(name = "CODE")
  val code: Int,

  @NaturalId
  @Column(name = "NAME", nullable = false, unique = true)
  val name: String,

  @Column(name = "DESCRIPTION", nullable = false)
  val description: String,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SupportType) return false

    if (code != other.code) return false
    return true
  }

  override fun hashCode(): Int {
    return code.hashCode()
  }

  override fun toString(): String {
    return this::class.simpleName + "(code='$code', name='$name')"
  }
}
