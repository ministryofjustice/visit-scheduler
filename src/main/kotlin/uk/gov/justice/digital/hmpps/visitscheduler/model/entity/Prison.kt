package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.jpa.repository.Temporal
import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.TemporalType

@Entity
@Table(name = "PRISON")
data class Prison(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ID")
  val id: Long = 0,

  @Column(name = "CODE", unique = true)
  var code: String,

  @Column(name = "active")
  var active: Boolean,

  @CreationTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  @Column
  val createTimestamp: LocalDateTime? = null,

  @UpdateTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  @Column
  val modifyTimestamp: LocalDateTime? = null
)
