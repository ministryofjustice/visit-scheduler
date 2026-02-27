package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerCategoryType

data class CreateCategoryGroupDto(
  @param:Schema(description = "Group name", example = "Main group", required = true)
  @field:NotBlank
  val name: String,

  @param:JsonProperty("prisonId")
  @param:Schema(description = "prisonId", example = "MDI", required = true)
  @field:NotBlank
  val prisonCode: String,

  @param:Schema(description = "list of categories for group", required = false)
  val categories: List<PrisonerCategoryType> = listOf(),
)
