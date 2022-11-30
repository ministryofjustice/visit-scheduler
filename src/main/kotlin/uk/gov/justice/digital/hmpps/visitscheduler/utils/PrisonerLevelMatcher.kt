package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.PermittedSessionLocation
import java.util.function.BiPredicate

@Component
class PrisonerLevelMatcher : BiPredicate<MutableList<PermittedSessionLocation>?, Map<PrisonerHousingLevels, String?>> {
  private val levelMatcher = object : BiPredicate<String?, String?> {
    override fun test(permittedSessionLevel: String?, prisonerLevel: String?): Boolean {
      permittedSessionLevel?.let {
        return it == prisonerLevel
      }

      return true
    }
  }

  override fun test(
    permittedSessionLocationsList: MutableList<PermittedSessionLocation>?,
    levelsMap: Map<PrisonerHousingLevels, String?>
  ): Boolean {
    permittedSessionLocationsList?.let { permittedSessionLocations ->
      for (permittedSessionLocation in permittedSessionLocations) {
        val result = levelMatcher.test(permittedSessionLocation.levelOneCode, levelsMap.get(PrisonerHousingLevels.LEVEL_ONE))
          .and(levelMatcher.test(permittedSessionLocation.levelTwoCode, levelsMap.get(PrisonerHousingLevels.LEVEL_TWO)))
          .and(levelMatcher.test(permittedSessionLocation.levelThreeCode, levelsMap.get(PrisonerHousingLevels.LEVEL_THREE)))
          .and(levelMatcher.test(permittedSessionLocation.levelFourCode, levelsMap.get(PrisonerHousingLevels.LEVEL_FOUR)))

        if (result)
          return true
      }
    }

    return false
  }
}
