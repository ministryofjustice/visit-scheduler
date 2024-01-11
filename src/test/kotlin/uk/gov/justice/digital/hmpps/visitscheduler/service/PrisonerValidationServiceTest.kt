package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.PrisonerNotInSuppliedPrisonException

@ExtendWith(MockitoExtension::class)
class PrisonerValidationServiceTest {

  private val prisonerValidationService = PrisonerValidationService()

  @Test
  fun `when prison code passed matches prisoners establishment code no exceptions are thrown`() {
    val prisonerId = "AA1234BB"
    val prisonCode = "MDI"
    val prisonerDetails = PrisonerDto(prisonerId = prisonerId, prisonCode = prisonCode)

    // When
    assertThatCode {
      prisonerValidationService.validatePrisonerIsFromPrison(prisonerDetails, "MDI")
    }.doesNotThrowAnyException()
  }

  @Test
  fun `when prison code does not match prisoners establishment code an exception is thrown`() {
    val prisonerId = "AA1234BB"
    val prisonCode = "MDI"
    val prisonerDetails = PrisonerDto(prisonerId = prisonerId, category = null, incentiveLevel = null, prisonCode = prisonCode)

    assertThrows<PrisonerNotInSuppliedPrisonException> {
      prisonerValidationService.validatePrisonerIsFromPrison(prisonerDetails, "ABC")
    }
  }
}
