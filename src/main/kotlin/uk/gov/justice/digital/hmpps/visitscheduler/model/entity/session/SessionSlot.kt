package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.PostPersist
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractIdEntity
import uk.gov.justice.digital.hmpps.visitscheduler.utils.QuotableEncoder
import java.time.LocalDate
import java.time.LocalTime

@Entity
@Table(name = "session_slot")
class SessionSlot(

  @Column(nullable = true)
  val sessionTemplateReference: String,

  @Column(nullable = false)
  val prisonId: Long,

  @Column(nullable = false)
  val slotDate: LocalDate,

  @Column(nullable = false)
  val slotTime: LocalTime,

  @Column(nullable = false)
  val slotEndTime: LocalTime,

) : AbstractIdEntity() {

  @Column
  lateinit var reference: String

  @PostPersist
  fun createReference() {
    reference = QuotableEncoder(minLength = 8).encode(id)
  }

  override fun toString(): String {
    return "SessionSlot(id=$id,reference='$reference')"
  }
}
