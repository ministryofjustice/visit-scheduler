package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.PermittedSessionLocation
import java.util.function.BiPredicate

@Component
class PrisonerLevelMatcher : BiPredicate<MutableList<PermittedSessionLocation>?, PrisonerDetailDto> {
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
    prisonerDetailDto: PrisonerDetailDto
  ): Boolean {
    permittedSessionLocationsList?.let { permittedSessionLocations ->
      for (permittedSessionLocation in permittedSessionLocations) {
        val result = levelMatcher.test(permittedSessionLocation.levelOneCode, prisonerDetailDto.unitCode1)
          .and(levelMatcher.test(permittedSessionLocation.levelTwoCode, prisonerDetailDto.unitCode2))
          .and(levelMatcher.test(permittedSessionLocation.levelThreeCode, prisonerDetailDto.unitCode3))
          .and(levelMatcher.test(permittedSessionLocation.levelFourCode, prisonerDetailDto.unitCode4))

        if (result)
          return true
      }
    }

    return false
  }
}
