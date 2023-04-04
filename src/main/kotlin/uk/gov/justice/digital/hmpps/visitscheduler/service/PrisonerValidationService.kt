package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.exception.PrisonerNotInSuppliedPrisonException

@Service
class PrisonerValidationService(
  private val prisonerService: PrisonerService,
) {
  companion object {
    const val PRISON_INFORMATION_MISSING_ERROR_MESSAGE = "Prisoner with ID - %s cannot be found"
    const val PRISON_CODE_DEV_ERROR_MESSAGE = "Prisoner with ID - %s is not in prison - %s but %s"
  }

  /**
   * Gets the prisoners prison code from Prison API and checks against the passed prison code.
   */
  fun validatePrisonerIsFromPrison(prisonerId: String, prisonCode: String) {
    val prisonerDetails = prisonerService.getPrisonerFullStatus(prisonerId)

    var message: String? = null
    prisonerDetails?.let {
      if (prisonCode != it.establishmentCode) {
        message = PRISON_CODE_DEV_ERROR_MESSAGE.format(prisonerId, prisonCode, it.establishmentCode)
      }
    } ?: run {
      message = PRISON_INFORMATION_MISSING_ERROR_MESSAGE.format(prisonerId)
    }

    message?.let {
      throw PrisonerNotInSuppliedPrisonException(message)
    }
  }
}
