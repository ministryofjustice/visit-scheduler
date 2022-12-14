package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base

import javax.persistence.Column
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.MappedSuperclass

@MappedSuperclass
abstract class AbstractIdEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ID")
  val id: Long = 0

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null) return false
    if (other::class != this::class) return false
    if (id != (other as AbstractIdEntity).id) return false
    return true
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }

  override fun toString(): String {
    return this::class.simpleName + "(id=$id)"
  }
}
