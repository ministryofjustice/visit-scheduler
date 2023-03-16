package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.exception.PrisonerNotInSuppliedPrisonException

@Service
class PrisonerValidationService(
  private val prisonerService: PrisonerService,
) {
  companion object {
    const val PRISON_CODE_DEV_ERROR_MESSAGE = "Prisoner with ID - %s is not in prison - %s"
    const val PRISON_CODE_USER_ERROR_MESSAGE = "prisoner's establishment and prison code passed do not match"
  }

  /**
   * Gets the prisoners prison code from Prison API and checks against the passed prison code.
   */
  fun validatePrisonerIsFromPrison(prisonerId: String, prisonCode: String) {
    val prisonerDetails = prisonerService.getPrisonerFullStatus(prisonerId)

    if (prisonCode != prisonerDetails?.establishmentCode) {
      val message = PRISON_CODE_DEV_ERROR_MESSAGE.format(prisonerId, prisonCode)

      throw PrisonerNotInSuppliedPrisonException(message, PrisonerNotInSuppliedPrisonException(PRISON_CODE_USER_ERROR_MESSAGE))
    }
  }
}
