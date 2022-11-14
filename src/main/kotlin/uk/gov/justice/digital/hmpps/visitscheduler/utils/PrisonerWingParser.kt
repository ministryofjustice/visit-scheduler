package uk.gov.justice.digital.hmpps.visitscheduler.utils

import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDetailDto

/**
 * this is only a temporary solution till we can get it from an API
 */
class PrisonerWingParser {
  private enum class WingElementByPrison(
    val prisonCode: String,
    val expectedElements: Int,
    val wingIndex: Int
  ) {
    // example - BLI-C-1-007 so expected elements is 4 and wing is expected to be 2nd element
    BRISTOL("BLI", 4, 1)
  }

  companion object {
    private const val LOCATION_DELIMITER: String = "-"

    /**
     * Temporary method to get the wing based on the prisoner's location.
     * This information will later need to come from some other API like the Prison API.
     * Since the location style differs between prison getting this information only for Bristol.
     */
    fun getPrisonerWingFromLocation(prisonerDetailDto: PrisonerDetailDto?): String? {
      if (prisonerDetailDto != null && !prisonerDetailDto.internalLocation.isNullOrBlank()) {
        val values = prisonerDetailDto.internalLocation.split(LOCATION_DELIMITER)

        // the first element is always the prison Code
        val prisonCode = values[0]

        // check if the prison code exists in the WingElementByPrison enum
        val wingElementByPrison: WingElementByPrison? = WingElementByPrison.values().firstOrNull { it.prisonCode == prisonCode }
        wingElementByPrison?.also {
          // if the number of elements in internalLocation is same as expected get the wing based on index in enum
          if (it.expectedElements == values.size && values.size > wingElementByPrison.wingIndex) {
            return values[wingElementByPrison.wingIndex]
          }
        }
      }

      return null
    }
  }
}
