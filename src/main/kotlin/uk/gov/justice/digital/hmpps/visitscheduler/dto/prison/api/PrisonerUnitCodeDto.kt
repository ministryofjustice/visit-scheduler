package uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Prisoner details - only offenderNo and location needed")
open class PrisonerUnitCodeDto(
  @Schema(description = "The prisoner's nomsId", example = "A0000AA")
  val nomsId: String,

  @Schema(description = "Level 1 from the prisoner's internal location", example = "B")
  val unitCode1: String?,

  @Schema(description = "Level 2 from the prisoner's internal location", example = "3")
  val unitCode2: String?,

  @Schema(description = "Level 3 from the prisoner's internal location", example = "008")
  val unitCode3: String?,

  val unitCode4: String? = null
) : PrisonerDetailsDto {
  private val levelsMap: MutableMap<PrisonerHousingLevels, String?> = mutableMapOf()

  init {
    levelsMap[PrisonerHousingLevels.LEVEL_ONE] = unitCode1
    levelsMap[PrisonerHousingLevels.LEVEL_TWO] = unitCode2
    levelsMap[PrisonerHousingLevels.LEVEL_THREE] = unitCode3
    levelsMap[PrisonerHousingLevels.LEVEL_FOUR] = unitCode4
  }

  override fun getLevels(): Map<PrisonerHousingLevels, String?> {
    return levelsMap.toMap()
  }
}
