package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PostPersist
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractIdEntity
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.utils.QuotableEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Entity
@Table(name = "session_time_slot")
class SessionTimeSlot(

  @ManyToOne
  @JoinColumn(name = "session_template_id", updatable = false, insertable = false, nullable = true)
  var sessionTemplate: SessionTemplate?=null,

  @Column(nullable = true)
  private val sessionTemplateReference: String?=null,

  @Column(nullable = false)
  private val timeSlot: LocalTime,

  @Column(nullable = false)
  private val timeSlotEnd: LocalTime,

  @Column(nullable = false)
  private val slotDuration: Int,

  @Column(nullable = false)
  private val date: LocalDate,

) : AbstractIdEntity() {

  @CreationTimestamp
  @Column
  val createTimestamp: LocalDateTime? = null

  @Column
  lateinit var reference: String

  @PostPersist
  fun createReference() {
    reference = QuotableEncoder(minLength = 8).encode(id)
  }

  override fun toString(): String {
    return "SessionTimeSlot(id=$id,reference='$reference')"
  }
}
