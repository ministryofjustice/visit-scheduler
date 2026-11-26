package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSearchSubject
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSearchType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitAttendanceDetails
import java.time.LocalTime

@Schema(description = "Additional visit attendance details")
data class VisitAttendanceDetailsDto(
  @param:Schema(description = "Search carried out in connection with the visit")
  val search: VisitSearchDto? = null,
  @param:Schema(description = "Actual local start time", type = "string", example = "13:45:00")
  val actualStartTime: LocalTime? = null,
  @param:Schema(description = "Actual local end time", type = "string", example = "14:30:00")
  val actualEndTime: LocalTime? = null,
) {
  constructor(entity: VisitAttendanceDetails) : this(
    search = entity.searchSubject?.let { VisitSearchDto(it, entity.searchType) },
    actualStartTime = entity.actualStartTime,
    actualEndTime = entity.actualEndTime,
  )
}

@Schema(description = "Search details for a visit")
data class VisitSearchDto(
  @param:Schema(description = "Who was searched", example = "PRISONER")
  val subject: VisitSearchSubject,
  @param:Schema(description = "Type of search, if recorded", example = "FULL_SEARCH")
  val type: VisitSearchType? = null,
)
