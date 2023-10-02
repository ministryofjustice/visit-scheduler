package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitscheduler.client.NonAssociationsApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonerOffenderSearchClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.PrisonerNotInSuppliedPrisonException

@ExtendWith(MockitoExtension::class)
class PrisonerValidationServiceTest {

  private val prisonApiClient = mock<PrisonApiClient>()
  private val nonAssociationsApiClient = mock<NonAssociationsApiClient>()
  private val prisonerOffenderSearchClient = mock<PrisonerOffenderSearchClient>()

  private val prisonerService = PrisonerService(prisonApiClient, nonAssociationsApiClient, prisonerOffenderSearchClient)

  private val prisonerValidationService = PrisonerValidationService(prisonerService)

  @Test
  fun `when prison code passed matches prisoners establishment code no exceptions are thrown`() {
    val prisonerId = "AA1234BB"
    val prisonCode = "MDI"
    val prisonerDetails = PrisonerDetailsDto(prisonerId, prisonCode, 1)
    whenever(
      prisonerService.getPrisonerFullStatus(prisonerId),
    ).thenReturn(prisonerDetails)

    // When
    assertThatCode {
      prisonerValidationService.validatePrisonerIsFromPrison(prisonerId, "MDI")
    }.doesNotThrowAnyException()

    // Then
    Mockito.verify(prisonApiClient, times(1)).getPrisonerDetails(prisonerId)
  }

  @Test
  fun `when prison code does not match prisoners establishment code an exception is thrown`() {
    val prisonerId = "AA1234BB"
    val prisonCode = "MDI"
    val prisonerDetails = PrisonerDetailsDto(prisonerId, prisonCode, 1)
    whenever(
      prisonerService.getPrisonerFullStatus(prisonerId),
    ).thenReturn(prisonerDetails)

    assertThrows<PrisonerNotInSuppliedPrisonException> {
      prisonerValidationService.validatePrisonerIsFromPrison(prisonerId, "ABC")
    }

    // Then
    Mockito.verify(prisonApiClient, times(1)).getPrisonerDetails(prisonerId)
  }
}
