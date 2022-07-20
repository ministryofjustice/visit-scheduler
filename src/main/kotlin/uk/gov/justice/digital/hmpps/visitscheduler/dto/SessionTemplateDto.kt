package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.SessionTemplate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.validation.constraints.NotBlank

data class SessionTemplateDto(
  @Schema(description = "session id", example = "123", required = true)
  val sessionTemplateId: Long,
  @Schema(description = "prisonId", example = "MDI", required = true)
  @field:NotBlank
  val prisonId: String,
  @Schema(description = "The start time of the generated visit session(s)", example = "13:45", required = true)
  val startTime: LocalTime,
  @Schema(description = "The end time of the generated visit session(s)", example = "13:45", required = true)
  val endTime: LocalTime,
  @Schema(description = "The start date of the session template", example = "2019-12-02", required = true)
  val startDate: LocalDate,
  @Schema(description = "The expiry date of the session template", example = "2019-12-02", required = false)
  val expiryDate: LocalDate? = null,
  @Schema(description = "visit type", example = "SOCIAL", required = true)
  val visitType: VisitType,
  @Schema(description = "visit room", example = "A1", required = true)
  val visitRoom: String,
  @Schema(description = "closed capacity", example = "10", required = true)
  val closedCapacity: Int,
  @Schema(description = "open capacity", example = "50", required = true)
  val openCapacity: Int,
  @Schema(description = "day of week fpr visit", example = "MONDAY", required = false)
  val dayOfWeek: DayOfWeek?,
) {
  constructor(sessionTemplateEntity: SessionTemplate) : this(
    sessionTemplateId = sessionTemplateEntity.id,
    prisonId = sessionTemplateEntity.prisonId,
    startTime = sessionTemplateEntity.startTime,
    endTime = sessionTemplateEntity.endTime,
    visitType = sessionTemplateEntity.visitType,
    startDate = sessionTemplateEntity.startDate,
    expiryDate = sessionTemplateEntity.expiryDate,
    visitRoom = sessionTemplateEntity.visitRoom,
    closedCapacity = sessionTemplateEntity.closedCapacity,
    openCapacity = sessionTemplateEntity.openCapacity,
    dayOfWeek = sessionTemplateEntity.dayOfWeek
  )
}
