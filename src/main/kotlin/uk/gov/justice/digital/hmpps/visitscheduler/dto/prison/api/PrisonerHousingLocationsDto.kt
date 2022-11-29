package uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api

import net.minidev.json.annotate.JsonIgnore

class PrisonerHousingLocationsDto(
  val levels: List<PrisonerHousingLevelDto> = listOf()
) {
  @JsonIgnore
  private val levelsMap: MutableMap<PrisonerHousingLevels, String?> = mutableMapOf()
  init {
    levelsMap[PrisonerHousingLevels.LEVEL_ONE] = getHousingLevelByLevelNumber(PrisonerHousingLevels.LEVEL_ONE.level)?.code
    levelsMap[PrisonerHousingLevels.LEVEL_TWO] = getHousingLevelByLevelNumber(PrisonerHousingLevels.LEVEL_TWO.level)?.code
    levelsMap[PrisonerHousingLevels.LEVEL_THREE] = getHousingLevelByLevelNumber(PrisonerHousingLevels.LEVEL_THREE.level)?.code
    levelsMap[PrisonerHousingLevels.LEVEL_FOUR] = getHousingLevelByLevelNumber(PrisonerHousingLevels.LEVEL_FOUR.level)?.code
  }

  fun getHousingLevelByLevelNumber(housingLevel: Int): PrisonerHousingLevelDto? {
    return levels.stream().filter { level -> level.level == housingLevel }.findFirst().orElse(null)
  }

  fun getLevels(): Map<PrisonerHousingLevels, String?> {
    return levelsMap.toMap()
  }
}
