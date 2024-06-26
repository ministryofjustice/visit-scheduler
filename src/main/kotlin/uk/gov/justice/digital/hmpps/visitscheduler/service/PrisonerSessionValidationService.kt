package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.ValidationException
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.utils.validators.SessionCategoryValidator
import uk.gov.justice.digital.hmpps.visitscheduler.utils.validators.SessionIncentiveValidator
import uk.gov.justice.digital.hmpps.visitscheduler.utils.validators.SessionLocationValidator

@Service
class PrisonerSessionValidationService(
  private val sessionLocationValidator: SessionLocationValidator,
  private val sessionIncentiveValidator: SessionIncentiveValidator,
  private val sessionCategoryValidator: SessionCategoryValidator,
  private val sessionTemplateRepository: SessionTemplateRepository,
  private val prisonerService: PrisonerService,
  private val prisonsService: PrisonsService,
) {
  fun isSessionAvailableToPrisoner(
    sessionTemplates: List<SessionTemplate>? = null,
    sessionTemplate: SessionTemplate,
    prisoner: PrisonerDto,
    prisonerHousingLevels: Map<PrisonerHousingLevels, String?>?,
  ): Boolean {
    return isSessionAvailableToPrisonerLocation(sessionTemplates, sessionTemplate, prisonerHousingLevels) &&
      isSessionAvailableToPrisonerCategory(sessionTemplates, sessionTemplate, prisoner) &&
      isSessionAvailableToPrisonerIncentiveLevel(sessionTemplates, sessionTemplate, prisoner)
  }

  fun isApplicationValid(
    application: Application,
  ): Boolean{
    val sessionSlot  = application.sessionSlot

    return sessionSlot.sessionTemplateReference?.let {
      val sessionTemplate = sessionTemplateRepository.findByReference(it)
      return sessionTemplate?.let {
        val prisoner = prisonerService.getPrisoner(application.prisonerId)?: throw ValidationException("prisoner not found")
        val prison = prisonsService.findPrisonById(application.prisonId)
        val prisonerHousingLevels = prisonerService.getPrisonerHousingLevels(application.prisonerId, prison.code, listOf(sessionTemplate))
         isSessionAvailableToPrisoner(sessionTemplate = sessionTemplate, prisoner = prisoner, prisonerHousingLevels = prisonerHousingLevels)
      }?: throw ValidationException("session template not found")
    }?: throw ValidationException("session slot not found")

    // check non association visits

    // check prisoner's VOs - if user type = PUBLIC
    // check slot capacity
  }

  private fun isSessionAvailableToPrisonerLocation(
    sessionTemplates: List<SessionTemplate>?,
    sessionTemplate: SessionTemplate,
    prisonerHousingLevels: Map<PrisonerHousingLevels, String?>?,
  ): Boolean {
    val hasSessionsWithLocationGroups = sessionTemplates?.any { it.permittedSessionLocationGroups.isNotEmpty() } ?: true

    // if there are sessions with location groups, check if the session is available to prisoner else return true as this is available to all
    return if (hasSessionsWithLocationGroups) {
      if (prisonerHousingLevels != null) {
        sessionLocationValidator.isAvailableToPrisoner(sessionTemplate, prisonerHousingLevels)
      } else {
        true
      }
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
      sessionCategoryValidator.isAvailableToPrisoner(sessionTemplate, prisoner)
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
      return sessionIncentiveValidator.isAvailableToPrisoner(sessionTemplate, prisoner)
    } else {
      true
    }
  }
}
