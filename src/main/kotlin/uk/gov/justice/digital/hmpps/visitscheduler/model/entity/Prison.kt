package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.TemporalType
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.jpa.repository.Temporal
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonDto
import java.time.LocalDateTime

@Entity
@Table(name = "PRISON")
class Prison(
  @Column(name = "CODE", unique = true)
  var code: String,

  @Column(name = "active")
  var active: Boolean,

  @Column(name = "policy_notice_days_min")
  var policyNoticeDaysMin: Int,
  @Column(name = "policy_notice_days_max")
  var policyNoticeDaysMax: Int,

  @Column(name = "max_total_visitors")
  var maxTotalVisitors: Int,
  @Column(name = "max_adult_visitors")
  var maxAdultVisitors: Int,
  @Column(name = "max_child_visitors")
  var maxChildVisitors: Int,
  @Column(name = "adult_age_years")
  var adultAgeYears: Int,

  @CreationTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  @Column
  val createTimestamp: LocalDateTime? = null,

  @UpdateTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  @Column
  val modifyTimestamp: LocalDateTime? = null,

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], mappedBy = "prison", orphanRemoval = true)
  var excludeDates: MutableList<PrisonExcludeDate> = mutableListOf(),

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], mappedBy = "prison", orphanRemoval = true)
  var clients: MutableList<PrisonUserClient> = mutableListOf(),
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ID")
  val id: Long = 0

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Prison) return false

    if (id != other.id) return false
    return true
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }

  override fun toString(): String {
    return this::class.simpleName + "(id=$id, code= '$code')"
  }

  constructor(dto: PrisonDto) : this(
    code = dto.code,
    active = dto.active,
    policyNoticeDaysMin = dto.policyNoticeDaysMin,
    policyNoticeDaysMax = dto.policyNoticeDaysMax,
    maxTotalVisitors = dto.maxTotalVisitors,
    maxAdultVisitors = dto.maxAdultVisitors,
    maxChildVisitors = dto.maxChildVisitors,
    adultAgeYears = dto.adultAgeYears,
  )
}
