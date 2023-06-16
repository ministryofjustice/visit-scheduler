package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType

data class UpdateCategoryGroupDto(
  @JsonProperty("name")
  @Schema(description = "Group name", example = "Main group", required = true)
  @field:NotBlank
  val name: String,

  @Schema(description = "list of category for group", required = false)
  val categories: List<PrisonerCategoryType> = listOf(),
)
