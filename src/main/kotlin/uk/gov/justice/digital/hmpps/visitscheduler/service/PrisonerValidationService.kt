package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.PrisonerNotInSuppliedPrisonException

@Service
class PrisonerValidationService {
  companion object {
    const val PRISON_INFORMATION_MISSING_ERROR_MESSAGE = "Prisoner with ID - %s cannot be found"
    const val PRISON_CODE_MISSING_ERROR_MESSAGE = "Prisoner with ID - %s is not in prison - %s but %s"
  }

  /**
   * Gets the prisoners prison code from Prison API and checks against the passed prison code.
   */
  fun validatePrisonerIsFromPrison(prisoner: PrisonerDto, prisonCode: String) {
    if (prisonCode != prisoner.prisonCode) {
      throw PrisonerNotInSuppliedPrisonException(PRISON_CODE_MISSING_ERROR_MESSAGE.format(prisoner.prisonerId, prisonCode, prisoner.prisonCode))
    }
  }

  fun validatePrisonerNotNull(prisonerId: String, prisoner: PrisonerDto?) {
    prisoner ?: throw PrisonerNotInSuppliedPrisonException(PRISON_INFORMATION_MISSING_ERROR_MESSAGE.format(prisonerId))
  }
}
