package uk.gov.justice.digital.hmpps.visitscheduler.utils

import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.PermittedSessionLocation
import java.util.function.BiPredicate

class PrisonerLevelMatcher : BiPredicate<MutableList<PermittedSessionLocation>?, PrisonerDetailDto> {
  override fun test(
    permittedSessionLocationsList: MutableList<PermittedSessionLocation>?,
    prisonerDetailDto: PrisonerDetailDto
  ): Boolean {
    permittedSessionLocationsList?.let { permittedSessionLocations ->
      for (permittedSessionLocation in permittedSessionLocations) {
        val result = LevelMatcher().test(permittedSessionLocation.levelOneCode, prisonerDetailDto.unitCode1)
          .and(LevelMatcher().test(permittedSessionLocation.levelTwoCode, prisonerDetailDto.unitCode2))
          .and(LevelMatcher().test(permittedSessionLocation.levelThreeCode, prisonerDetailDto.unitCode3))
          .and(LevelMatcher().test(permittedSessionLocation.levelFourCode, prisonerDetailDto.unitCode4))

        if (result)
          return true
      }
    }

    return false
  }

  inner class LevelMatcher : BiPredicate<String?, String?> {
    override fun test(permittedSessionLevel: String?, prisonerLevel: String?): Boolean {
      permittedSessionLevel?.let {
        return it == prisonerLevel
      }

      return true
    }
  }
}
