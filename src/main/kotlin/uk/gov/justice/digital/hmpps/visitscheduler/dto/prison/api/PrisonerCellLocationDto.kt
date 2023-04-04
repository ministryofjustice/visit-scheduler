package uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Prisoner cell information")
data class PrisonerCellLocationDto(
  var prisonCode: String? = null,
  val levels: List<PrisonerHousingLevelDto> = listOf(),
) {

  // This is the workaround until we have the new api, JSON should then convert directly into this object
  companion object {

    private fun createHousingLevels(description: String): List<PrisonerHousingLevelDto> {
      val descriptionElements = description.split("-").toMutableList()
      if (descriptionElements.isNotEmpty()) {
        descriptionElements.removeAt(0)
      }
      return descriptionElements.mapIndexed { index: Int, value ->
        PrisonerHousingLevelDto(
          level = index + 1,
          code = value,
          description = "cell history",
        )
      }
    }

    private fun getPrisonCode(description: String): String? {
      return description.split("-")[0]
    }
  }

  @JsonCreator
  constructor(@JsonProperty("description") description: String? = null) : this(
    levels = description?.let { createHousingLevels(it) } ?: listOf(),
    prisonCode = description?.let { getPrisonCode(it) },
  )
}
