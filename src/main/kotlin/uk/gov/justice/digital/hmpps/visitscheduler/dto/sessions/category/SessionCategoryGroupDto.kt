package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionCategoryGroup

data class SessionCategoryGroupDto(
  @param:JsonProperty("name")
  @param:Schema(description = "Group name", example = "Category A Group", required = true)
  @field:NotBlank
  val name: String,

  @param:Schema(description = "Reference", example = "v9-d7-ed-7u", required = true)
  val reference: String,

  @param:Schema(description = "list of allowed prisoner categories for group", required = false)
  val categories: List<PrisonerCategoryType> = listOf(),
) {
  constructor(sessionCategoryGroup: SessionCategoryGroup) : this(
    name = sessionCategoryGroup.name,
    reference = sessionCategoryGroup.reference,
    categories = sessionCategoryGroup.sessionCategories.map { it.prisonerCategoryType },
  )
}
