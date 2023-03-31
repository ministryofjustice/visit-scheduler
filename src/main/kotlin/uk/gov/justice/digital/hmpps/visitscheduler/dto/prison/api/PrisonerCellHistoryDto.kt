package uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Prisoner details - only offenderNo and location needed")
data class PrisonerCellHistoryDto(

  @JsonProperty("content")
  val history: List<PrisonerCellDto> = listOf(),
)
