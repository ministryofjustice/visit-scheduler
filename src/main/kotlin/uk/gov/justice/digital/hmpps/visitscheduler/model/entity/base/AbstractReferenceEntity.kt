package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base

import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import uk.gov.justice.digital.hmpps.visitscheduler.utils.QuotableEncoder
import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.MappedSuperclass
import javax.persistence.PostPersist

@MappedSuperclass
abstract class AbstractReferenceEntity : AbstractIdEntity() {

  @Column
  var reference = ""
    private set

  @CreationTimestamp
  @Column
  val createTimestamp: LocalDateTime? = null

  @UpdateTimestamp
  @Column
  val modifyTimestamp: LocalDateTime? = null

  @PostPersist
  fun createReference() {
    if (reference.isBlank()) {
      reference = QuotableEncoder(minLength = 8).encode(id)
    }
  }

  override fun toString(): String {
    return this::class.simpleName + "(id=$id, reference=$reference)"
  }
}
