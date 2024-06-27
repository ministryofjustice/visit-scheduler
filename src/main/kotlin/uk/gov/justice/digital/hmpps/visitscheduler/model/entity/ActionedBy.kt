package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import jakarta.persistence.CascadeType.ALL
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import java.time.LocalDateTime

@Entity
@Table(
  name = "actioned_by",
)
class ActionedBy(

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ID")
  val id: Long = 0,

  @Column(nullable = true)
  val bookerReference: String?,

  @Column(nullable = true)
  val userName: String?,

  @Enumerated(EnumType.STRING)
  @Column(name = "user_type", nullable = false)
  val userType: UserType,

  @CreationTimestamp
  @Column
  val createTimestamp: LocalDateTime = LocalDateTime.now(),

  @OrderBy("id")
  @OneToMany(fetch = FetchType.LAZY, cascade = [ALL], mappedBy = "actionedBy", orphanRemoval = true)
  val eventAuditList: MutableList<EventAudit> = mutableListOf(),
)
