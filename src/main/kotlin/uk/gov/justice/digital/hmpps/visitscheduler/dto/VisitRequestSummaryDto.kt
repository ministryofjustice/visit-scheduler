package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

class VisitRequestSummaryDto(
  @field:Schema(description = "Visit date", required = true)
  val visitDate: LocalDate,

  @field:Schema(description = "Date the visit request was made", required = true)
  val requestedOnDate: LocalDate,

  @field:Schema(description = "Name of the prisoner who is being visited", required = true)
  val prisonerName: String,

  @field:Schema(description = "ID of the prisoner who is being visited", required = true)
  val prisonNumber: String,

  @field:Schema(description = "Name of the main contact for the visit request", required = false)
  val mainContact: String? = null,
)
