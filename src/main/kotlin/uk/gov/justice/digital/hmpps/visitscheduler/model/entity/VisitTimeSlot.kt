package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.NaturalId
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.jpa.repository.Temporal
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.utils.QuotableEncoder
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType.STRING
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.PostPersist
import javax.persistence.Table
import javax.persistence.TemporalType.TIMESTAMP

@Entity
@Table(name = "VISIT_TIME_SLOT")
data class VisitTimeSlot(

  @Column
  var sessionTemplateReference: String = "",

  @Column(name = "PRISON_ID", nullable = false)
  val prisonId: Long,

  @ManyToOne
  @JoinColumn(name = "PRISON_ID", updatable = false, insertable = false)
  val prison: Prison,

  @Column(nullable = false)
  var visitRoom: String,

  @Column(nullable = false)
  var startTime: LocalTime,

  @Column(nullable = false)
  var endTime: LocalTime,

  @Column(nullable = false)
  @Enumerated(STRING)
  var visitType: VisitType,

  @Column(nullable = false)
  @Enumerated(STRING)
  val dayOfWeek: DayOfWeek

) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ID")
  val id: Long = 0

  @CreationTimestamp
  @Column
  val createTimestamp: LocalDateTime? = null

  @UpdateTimestamp
  @Column
  val modifyTimestamp: LocalDateTime? = null

  @PostPersist
  fun createReference() {
    if (sessionTemplateReference.isBlank()) {
      sessionTemplateReference = QuotableEncoder(minLength = 8).encode(id)
    }
  }
}
