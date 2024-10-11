package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.TemporalType
import org.springframework.data.jpa.repository.Temporal
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractIdEntity
import java.time.LocalDate

@Entity
@Table(name = "PRISON_EXCLUDE_DATE")
class PrisonExcludeDate(

  @Column(name = "PRISON_ID", nullable = false)
  var prisonId: Long,

  @ManyToOne
  @JoinColumn(name = "PRISON_ID", updatable = false, insertable = false, nullable = false)
  val prison: Prison,

  @Temporal(TemporalType.DATE)
  @Column
  override val excludeDate: LocalDate,

  @Column(name = "actioned_by", nullable = false)
  override val actionedBy: String,
) : AbstractIdEntity(), IExcludeDate
