package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels.LEVEL_FOUR
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels.LEVEL_ONE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels.LEVEL_THREE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels.LEVEL_TWO
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.PermittedSessionLocation
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import java.util.function.BiPredicate

@Component
class PrisonerLevelMatcher : BiPredicate<SessionLocationGroup?, Map<PrisonerHousingLevels, String?>> {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private val validMatch = object : BiPredicate<String?, String?> {
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
        if (hasLevelMatch(permittedSessionLocation, levelsMap)) {
          return true
        }
      }
    }

    return false
  }

  fun hasLevelMatch(
    permittedSessionLocation: PermittedSessionLocation,
    levelsMap: Map<PrisonerHousingLevels, String?>,
  ): Boolean {
    if (LOG.isDebugEnabled) {
      logMatch(permittedSessionLocation, levelsMap)
    }
    return validMatch.test(permittedSessionLocation.levelOneCode, levelsMap.get(LEVEL_ONE))
      .and(validMatch.test(permittedSessionLocation.levelTwoCode, levelsMap.get(LEVEL_TWO)))
      .and(validMatch.test(permittedSessionLocation.levelThreeCode, levelsMap.get(LEVEL_THREE)))
      .and(validMatch.test(permittedSessionLocation.levelFourCode, levelsMap.get(LEVEL_FOUR)))
  }

  private fun logMatch(
    permittedSessionLocation: PermittedSessionLocation,
    levelsMap: Map<PrisonerHousingLevels, String?>,
  ) {
    val debugLog = StringBuilder()
    debugLog.append("Level match :")
    with(permittedSessionLocation) {
      debugLog.append(" 1:$levelOneCode=${levelsMap.get(LEVEL_ONE)}")
      levelTwoCode?.let {
        debugLog.append(" 2:$levelTwoCode=${levelsMap.get(LEVEL_TWO)}")
      }
      levelThreeCode?.let {
        debugLog.append(" 3:$levelThreeCode=${levelsMap.get(LEVEL_THREE)}")
      }
      levelFourCode?.let {
        debugLog.append(" 4:$levelFourCode=${levelsMap.get(LEVEL_FOUR)}")
      }
    }
    LOG.debug(debugLog.toString())
  }
}
