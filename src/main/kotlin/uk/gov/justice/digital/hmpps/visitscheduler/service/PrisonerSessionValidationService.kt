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
    sessionTemplates: List<SessionTemplate>? = null,
    sessionTemplate: SessionTemplate,
    prisoner: PrisonerDto,
    prisonerHousingLevels: Map<PrisonerHousingLevels, String?>?,
  ): Boolean = isSessionAvailableToPrisonerLocation(sessionTemplates, sessionTemplate, prisonerHousingLevels) &&
    isSessionAvailableToPrisonerCategory(sessionTemplates, sessionTemplate, prisoner) &&
    isSessionAvailableToPrisonerIncentiveLevel(sessionTemplates, sessionTemplate, prisoner)

  private fun isSessionAvailableToPrisonerLocation(
    sessionTemplates: List<SessionTemplate>?,
    sessionTemplate: SessionTemplate,
    prisonerHousingLevels: Map<PrisonerHousingLevels, String?>?,
  ): Boolean {
    val hasSessionsWithLocationGroups = sessionTemplates?.any { it.permittedSessionLocationGroups.isNotEmpty() } ?: true

    // if there are sessions with location groups, check if the session is available to prisoner else return true as this is available to all
    return if (hasSessionsWithLocationGroups) {
      sessionLocationValidator.isValid(sessionTemplate, prisonerHousingLevels)
    } else {
      true
    }
  }

  private fun isSessionAvailableToPrisonerCategory(
    sessionTemplates: List<SessionTemplate>?,
    sessionTemplate: SessionTemplate,
    prisoner: PrisonerDto,
  ): Boolean {
    // if there are sessions with category groups, check if the session is available to prisoner else return true as this is available to all
    val hasSessionsWithCategoryGroups = sessionTemplates?.any { it.permittedSessionCategoryGroups.isNotEmpty() } ?: true

    return if (hasSessionsWithCategoryGroups) {
      sessionCategoryValidator.isValid(sessionTemplate, prisoner)
    } else {
      true
    }
  }

  private fun isSessionAvailableToPrisonerIncentiveLevel(
    sessionTemplates: List<SessionTemplate>?,
    sessionTemplate: SessionTemplate,
    prisoner: PrisonerDto,
  ): Boolean {
    // if there are sessions with incentive levels, check if the session is available to prisoner else return true as this is available to all
    val hasSessionsWithIncentiveGroups = sessionTemplates?.any { it.permittedSessionIncentiveLevelGroups.isNotEmpty() } ?: true
    return if (hasSessionsWithIncentiveGroups) {
      sessionIncentiveValidator.isValid(sessionTemplate, prisoner)
    } else {
      true
    }
  }
}
