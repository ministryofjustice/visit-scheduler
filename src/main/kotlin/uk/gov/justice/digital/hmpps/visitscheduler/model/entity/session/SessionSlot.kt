package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.PostPersist
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractIdEntity
import uk.gov.justice.digital.hmpps.visitscheduler.utils.QuotableEncoder
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "session_slot")
class SessionSlot(

  @Column(nullable = true)
  val sessionTemplateReference: String? = null,

  @Column(nullable = false)
  val prisonId: Long,

  @Column(nullable = false)
  val slotDate: LocalDate,

  @Column(nullable = false)
  val slotStart: LocalDateTime,

  @Column(nullable = false)
  val slotEnd: LocalDateTime,

) : AbstractIdEntity() {

  @Column
  var reference: String = ""
    private set

  @PostPersist
  fun createReference() {
    if (reference.isNullOrBlank()) {
      reference = QuotableEncoder(minLength = 8).encode(id)
    }
  }

  override fun toString(): String {
    return "SessionSlot(id=$id,reference='$reference')"
  }
}
