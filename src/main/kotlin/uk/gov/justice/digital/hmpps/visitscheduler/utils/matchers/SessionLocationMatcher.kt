package uk.gov.justice.digital.hmpps.visitscheduler.utils.matchers

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.PermittedSessionLocationDto
import java.util.Comparator
import java.util.function.BiPredicate

@Component
class SessionLocationMatcher : SessionGroupMatcher<PermittedSessionLocationDto>, Comparator<PermittedSessionLocationDto> {
  private enum class LocationMatchLevel(val matchValue: Int) {
    EXACT_MATCH(0),
    LEFT_LOWER_MATCH(1),
    LEFT_HIGHER_MATCH(2),
    NO_MATCH(-1),
  }

  private val validSessionLocationMatch = object : BiPredicate<String?, String?> {
    override fun test(primaryLocationLevel: String?, comparedLocationLevel: String?): Boolean {
      if (primaryLocationLevel != null && comparedLocationLevel != null) {
        return primaryLocationLevel == comparedLocationLevel
      }
      // If any of them is null - return true
      return true
    }
  }

  private val multipleLocationMatcher =
    BiPredicate<PermittedSessionLocationDto, Set<PermittedSessionLocationDto>> { primarySessionLocation, comparedSessionLocations ->
      comparedSessionLocations.stream().anyMatch { comparedSessionLocation ->
        compare(primarySessionLocation, comparedSessionLocation) >= 0
      }
    }

  private val multipleLocationEqualOrLowerMatcher =
    BiPredicate<PermittedSessionLocationDto, Set<PermittedSessionLocationDto>> { primarySessionLocation, comparedSessionLocations ->
      comparedSessionLocations.stream().anyMatch { comparedSessionLocation ->
        (compare(primarySessionLocation, comparedSessionLocation) in 0..1)
      }
    }

  private fun hasLocationMatch(
    primarySessionLocation: PermittedSessionLocationDto,
    comparedSessionLocations: Set<PermittedSessionLocationDto>,
  ): Boolean {
    return multipleLocationMatcher.test(primarySessionLocation, comparedSessionLocations)
  }

  override fun hasAllMatch(o1: Set<PermittedSessionLocationDto>, o2: Set<PermittedSessionLocationDto>): Boolean {
    return o2.isEmpty() || o1.stream().allMatch {
      hasLocationMatch(it, o2)
    }
  }

  fun hasAllLowerOrEqualMatch(o1: Set<PermittedSessionLocationDto>, o2: Set<PermittedSessionLocationDto>): Boolean {
    return if ((o1.isEmpty() && o2.isEmpty()) || o2.isEmpty()) {
      true
    } else if (o1.isEmpty()) {
      false
    } else {
      o1.stream().allMatch {
        multipleLocationEqualOrLowerMatcher.test(it, o2)
      }
    }
  }

  override fun hasAnyMatch(o1: Set<PermittedSessionLocationDto>, o2: Set<PermittedSessionLocationDto>): Boolean {
    // if o2 is empty or any of the locations
    return o2.isEmpty() || o1.stream().anyMatch {
      hasLocationMatch(it, o2)
    }
  }

  private fun getMatchLevel(o1: PermittedSessionLocationDto, o2: PermittedSessionLocationDto): LocationMatchLevel {
    return if (o1 == o2) {
      LocationMatchLevel.EXACT_MATCH
    } else if (
      validSessionLocationMatch.test(o1.levelOneCode, o2.levelOneCode)
        .and(validSessionLocationMatch.test(o1.levelTwoCode, o2.levelTwoCode))
        .and(validSessionLocationMatch.test(o1.levelThreeCode, o2.levelThreeCode))
        .and(validSessionLocationMatch.test(o1.levelFourCode, o2.levelFourCode))
    ) {
      if ((o1.levelTwoCode == null && o2.levelTwoCode != null) ||
        (o1.levelThreeCode == null && o2.levelThreeCode != null) ||
        (o1.levelFourCode == null && o2.levelFourCode != null)
      ) {
        LocationMatchLevel.LEFT_HIGHER_MATCH
      } else {
        LocationMatchLevel.LEFT_LOWER_MATCH
      }
    } else {
      LocationMatchLevel.NO_MATCH
    }
  }

  override fun compare(o1: PermittedSessionLocationDto?, o2: PermittedSessionLocationDto?): Int {
    return if (o1 != null && o2 != null) getMatchLevel(o1, o2).matchValue else -1
  }
}
