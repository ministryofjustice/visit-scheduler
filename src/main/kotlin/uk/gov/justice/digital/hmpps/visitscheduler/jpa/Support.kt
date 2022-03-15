package uk.gov.justice.digital.hmpps.visitscheduler.jpa

import org.hibernate.Hibernate
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "SUPPORT")
data class Support(
  @Id
  @Column(name = "CODE")
  val code: Int,

  @Column(name = "NAME", nullable = false)
  val name: String,

  @Column(name = "DESCRIPTION", nullable = false)
  val description: String,

) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as Support

    return code == other.code
  }

  override fun hashCode(): Int = code.hashCode()

  @Override
  override fun toString(): String {
    return this::class.simpleName + code
  }
}
