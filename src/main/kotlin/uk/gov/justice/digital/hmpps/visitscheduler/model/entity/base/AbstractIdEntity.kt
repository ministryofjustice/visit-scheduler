package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass

@MappedSuperclass
abstract class AbstractIdEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ID")
  open val id: Long = 0

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null) return false
    if (other::class != this::class) return false
    if (id != (other as AbstractIdEntity).id) return false
    return true
  }

  override fun hashCode(): Int = id.hashCode()

  override fun toString(): String = this::class.simpleName + "(id=$id)"
}
