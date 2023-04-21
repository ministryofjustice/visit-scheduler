package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import java.util.function.BiPredicate

@Component
class PrisonerLevelMatcher : BiPredicate<SessionLocationGroup?, Map<PrisonerHousingLevels, String?>> {
  private val levelMatcher = object : BiPredicate<String?, String?> {
    override fun test(permittedSessionLevel: String?, prisonerLevel: String?): Boolean {
      permittedSessionLevel?.let {
        return it == prisonerLevel
      }
      // If no prison level then match
      return true
    }
  }

  override fun test(
    sessionLocationGroups: SessionLocationGroup?,
    levelsMap: Map<PrisonerHousingLevels, String?>,
  ): Boolean {
    sessionLocationGroups?.let { sessionLocationGroup ->
      for (permittedSessionLocation in sessionLocationGroup.sessionLocations) {
        val result = levelMatcher.test(permittedSessionLocation.levelOneCode, levelsMap.get(PrisonerHousingLevels.LEVEL_ONE))
          .and(levelMatcher.test(permittedSessionLocation.levelTwoCode, levelsMap.get(PrisonerHousingLevels.LEVEL_TWO)))
          .and(levelMatcher.test(permittedSessionLocation.levelThreeCode, levelsMap.get(PrisonerHousingLevels.LEVEL_THREE)))
          .and(levelMatcher.test(permittedSessionLocation.levelFourCode, levelsMap.get(PrisonerHousingLevels.LEVEL_FOUR)))

        if (result) {
          return true
        }
      }
    }

    return false
  }
}
