package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OffenderNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OffenderNonAssociationDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OffenderNonAssociationDto
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class PrisonApiServiceTest {

  private val prisonApiClient = mock<PrisonApiClient>()

  private val prisonApiService: PrisonApiService = PrisonApiService(prisonApiClient)

  @Test
  fun `no non associations are returned when prisoner has no non associations`() {
    val prisonerId = "AA1234BB"

    whenever(
      prisonApiClient.getOffenderNonAssociation(prisonerId)
    ).thenReturn(OffenderNonAssociationDetailsDto())

    // When
    val offenderNonAssociations = prisonApiService.getOffenderNonAssociationList(prisonerId)

    // Then
    assertThat(offenderNonAssociations).isEmpty()
    Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
  }

  @Test
  fun `one non association is returned when prisoner has a single non associations`() {
    val prisonerId = "AA1234BB"
    val associationId = "AA1234CC"

    whenever(
      prisonApiClient.getOffenderNonAssociation(prisonerId)
    ).thenReturn(
      OffenderNonAssociationDetailsDto(
        listOf(
          OffenderNonAssociationDetailDto(
            effectiveDate = LocalDate.now().minusMonths(1),
            expiryDate = LocalDate.now().plusMonths(1),
            offenderNonAssociation = OffenderNonAssociationDto(offenderNo = associationId)
          )
        )
      )
    )

    // When
    val offenderNonAssociations = prisonApiService.getOffenderNonAssociationList(prisonerId)

    // Then
    assertThat(offenderNonAssociations.size).isEqualTo(1)
    val offenderNonAssociationList = offenderNonAssociations.stream().map { o -> o.offenderNonAssociation.offenderNo }.toList()
    assertThat(offenderNonAssociationList.contains(associationId))
    Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
  }

  @Test
  fun `multiple non associations are returned when prisoner has multiple non associations`() {
    val prisonerId = "AA1234BB"
    val association1 = OffenderNonAssociationDetailDto(
      effectiveDate = LocalDate.now().minusMonths(1),
      expiryDate = LocalDate.now().plusMonths(1),
      offenderNonAssociation = OffenderNonAssociationDto(offenderNo = "AA1234CC")
    )
    val association2 = OffenderNonAssociationDetailDto(
      effectiveDate = LocalDate.now().minusMonths(1),
      expiryDate = LocalDate.now().plusMonths(1),
      offenderNonAssociation = OffenderNonAssociationDto(offenderNo = "AA1234DD")
    )
    val association3 = OffenderNonAssociationDetailDto(
      effectiveDate = LocalDate.now().minusMonths(1),
      expiryDate = LocalDate.now().plusMonths(1),
      offenderNonAssociation = OffenderNonAssociationDto(offenderNo = "AA1234EE")
    )

    whenever(
      prisonApiClient.getOffenderNonAssociation(prisonerId)
    ).thenReturn(
      OffenderNonAssociationDetailsDto(
        listOf(
          association1, association2, association3
        )
      )
    )

    // When
    val offenderNonAssociations = prisonApiService.getOffenderNonAssociationList(prisonerId)

    // Then
    assertThat(offenderNonAssociations.size).isEqualTo(3)
    val offenderNonAssociationList = offenderNonAssociations.stream().map { o -> o.offenderNonAssociation.offenderNo }.toList()
    assertThat(offenderNonAssociationList.containsAll(listOf("AA1234EE", "AA1234EE", "AA1234EE")))
    Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
  }

  @Test
  fun `empty list is returned when prison api non association call returns NOT FOUND`() {
    val prisonerId = "AA1234BB"

    whenever(
      prisonApiClient.getOffenderNonAssociation(prisonerId)
    ).thenThrow(
      WebClientResponseException.create(HttpStatus.NOT_FOUND.value(), "", HttpHeaders.EMPTY, byteArrayOf(), null)
    )

    // When
    val offenderNonAssociations = prisonApiService.getOffenderNonAssociationList(prisonerId)

    // Then
    assertThat(offenderNonAssociations).isEmpty()
    Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
  }

  @Test
  fun `get non associations throws WebClientResponseException for BAD REQUEST`() {
    val prisonerId = "AA1234BB"

    whenever(
      prisonApiClient.getOffenderNonAssociation(prisonerId)
    ).thenThrow(
      WebClientResponseException.create(HttpStatus.BAD_REQUEST.value(), "", HttpHeaders.EMPTY, byteArrayOf(), null)
    )

    // When
    assertThrows<WebClientResponseException> {
      prisonApiService.getOffenderNonAssociationList(prisonerId)
    }

    // Then
    Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
  }
}
