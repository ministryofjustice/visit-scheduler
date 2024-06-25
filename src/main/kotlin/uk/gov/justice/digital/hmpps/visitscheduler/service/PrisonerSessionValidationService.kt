package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.utils.validators.SessionCategoryValidator
import uk.gov.justice.digital.hmpps.visitscheduler.utils.validators.SessionIncentiveValidator
import uk.gov.justice.digital.hmpps.visitscheduler.utils.validators.SessionLocationValidator

@Service
class PrisonerSessionValidationService(
  private val sessionLocationValidator: SessionLocationValidator,
  private val sessionIncentiveValidator: SessionIncentiveValidator,
  private val sessionCategoryValidator: SessionCategoryValidator,
) {
  fun isSessionAvailableToPrisoner(
    sessionTemplate: SessionTemplate,
    prisoner: PrisonerDto,
    prisonerHousingLevels: Map<PrisonerHousingLevels, String?>?,
  ): Boolean {
    return isSessionAvailableToPrisonerLocation(sessionTemplate, prisonerHousingLevels) &&
      isSessionAvailableToPrisonerCategory(sessionTemplate, prisoner) &&
      isSessionAvailableToPrisonerIncentiveLevel(sessionTemplate, prisoner)
  }

  private fun isSessionAvailableToPrisonerLocation(
    sessionTemplate: SessionTemplate,
    prisonerHousingLevels: Map<PrisonerHousingLevels, String?>?,
  ): Boolean {
    return if (prisonerHousingLevels != null) {
      sessionLocationValidator.isAvailableToPrisoner(sessionTemplate, prisonerHousingLevels)
    } else {
      true
    }
  }

  private fun isSessionAvailableToPrisonerCategory(
    sessionTemplate: SessionTemplate,
    prisoner: PrisonerDto,
  ): Boolean {
    return sessionCategoryValidator.isAvailableToPrisoner(sessionTemplate, prisoner)
  }

  private fun isSessionAvailableToPrisonerIncentiveLevel(
    sessionTemplate: SessionTemplate,
    prisoner: PrisonerDto,
  ): Boolean {
    return sessionIncentiveValidator.isAvailableToPrisoner(sessionTemplate, prisoner)
  }
}
