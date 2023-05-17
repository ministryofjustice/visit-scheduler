package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PostPersist
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import uk.gov.justice.digital.hmpps.visitscheduler.utils.QuotableEncoder
import java.time.LocalDateTime

@MappedSuperclass
abstract class AbstractReferenceEntity(
  @Transient
  private val delimiter: String = "-",
  @Transient
  private val minLength: Int = 8,
  @Transient
  private val chunkSize: Int = 2,
) : AbstractIdEntity() {

  @Column
  open var reference = ""

  @CreationTimestamp
  @Column
  open val createTimestamp: LocalDateTime? = null

  @UpdateTimestamp
  @Column
  open val modifyTimestamp: LocalDateTime? = null

  @PostPersist
  fun createReference() {
    if (reference.isBlank()) {
      reference = QuotableEncoder(minLength = minLength, delimiter = delimiter, chunkSize = chunkSize).encode(id)
    }
  }

  override fun toString(): String {
    return this::class.simpleName + "(id=$id, reference=$reference)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null) return false
    if (other::class != this::class) return false
    if (id != (other as AbstractIdEntity).id) return false
    if (reference != (other as AbstractReferenceEntity).reference) return false
    return true
  }
}
