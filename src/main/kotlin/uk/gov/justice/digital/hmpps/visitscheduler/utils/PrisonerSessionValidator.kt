package uk.gov.justice.digital.hmpps.visitscheduler.utils

import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate

class PrisonerSessionValidator {
  companion object {
    fun isSessionAvailableToPrisoner(
      prisonerDetails: PrisonerDetailDto,
      sessionTemplateDetails: SessionTemplate
    ): Boolean {
      val isSessionAvailableToAllPrisoners = SessionAllPrisonersMatcher().test(sessionTemplateDetails)
      if (!isSessionAvailableToAllPrisoners) {
        return PrisonerLevelMatcher().test(sessionTemplateDetails.permittedSessionLocations, prisonerDetails)
      }

      return true
    }

/*   isSessionAvailableToPrisoner version if include and exclude list is used
    fun isSessionAvailableToPrisoner(
      prisonerDetails: PrisonerDetailDto,
      sessionTemplateDetails: SessionTemplate
    ): Boolean {
      val isSessionAvailableToAllPrisoners = SessionAllPrisonersMatcher().test(sessionTemplateDetails)
      if (!isSessionAvailableToAllPrisoners) {
           val includedSessionLocations = sessionTemplateDetails.permittedSessionLocations.filter { p -> p.permittedType == PermittedType.INCLUDING }
         // val excludedSessionLocations = sessionTemplateDetails.permittedSessionLocations.filter { p -> p.permittedType == PermittedType.EXCLUDING }
        return PrisonerLevelMatcher().test(sessionTemplateDetails.permittedSessionLocations, prisonerDetails)
        // val isPrisonerOnExcludeList = PrisonerLevelMatcher().test(excludedSessionLocations, prisonerDetails)

        // return isPrisonerOnIncludeList && !isPrisonerOnExcludeList
      }

      return true
    }*/
  }
}
