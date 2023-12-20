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
import uk.gov.justice.digital.hmpps.visitscheduler.client.NonAssociationsApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonerOffenderSearchClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.OtherPrisonerDetails
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevelDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLocationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerNonAssociationDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch.CurrentIncentiveDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch.IncentiveLevelDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch.PrisonerSearchResultDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class PrisonerServiceTest {

  private val prisonApiClient = mock<PrisonApiClient>()
  private val nonAssociationsApiClient = mock<NonAssociationsApiClient>()
  private val prisonerOffenderSearchClient = mock<PrisonerOffenderSearchClient>()
  private val prisonsService = mock<PrisonsService>()

  private val prisonerService: PrisonerService = PrisonerService(prisonApiClient, nonAssociationsApiClient, prisonerOffenderSearchClient, prisonsService)
  private val prisonerCode = "Code"

  @Test
  fun `when prisoner has no non associations no non associations are returned `() {
    val prisonerId = "AA1234BB"

    whenever(
      nonAssociationsApiClient.getPrisonerNonAssociation(prisonerId),
    ).thenReturn(PrisonerNonAssociationDetailsDto())

    // When
    val prisonerNonAssociations = prisonerService.getPrisonerNonAssociationList(prisonerId)

    // Then
    assertThat(prisonerNonAssociations).isEmpty()
    Mockito.verify(nonAssociationsApiClient, times(1)).getPrisonerNonAssociation(prisonerId)
  }

  @Test
  fun `when prisoner has a single non association one non association is returned`() {
    val prisonerId = "AA1234BB"
    val associationId = "AA1234CC"

    whenever(
      nonAssociationsApiClient.getPrisonerNonAssociation(prisonerId),
    ).thenReturn(
      PrisonerNonAssociationDetailsDto(
        listOf(
          PrisonerNonAssociationDetailDto(
            otherPrisonerDetails = OtherPrisonerDetails(prisonerNumber = associationId),
          ),
        ),
      ),
    )

    // When
    val prisonerNonAssociations = prisonerService.getPrisonerNonAssociationList(prisonerId)

    // Then
    assertThat(prisonerNonAssociations.size).isEqualTo(1)
    val prisonerNonAssociationList = prisonerNonAssociations.stream().map { o -> o.otherPrisonerDetails.prisonerNumber }.toList()
    assertThat(prisonerNonAssociationList.contains(associationId))
    Mockito.verify(nonAssociationsApiClient, times(1)).getPrisonerNonAssociation(prisonerId)
  }

  @Test
  fun ` when prisoner has multiple non associations multiple non associations are returned`() {
    val prisonerId = "AA1234BB"
    val association1 = PrisonerNonAssociationDetailDto(
      otherPrisonerDetails = OtherPrisonerDetails(prisonerNumber = "AA1234CC"),
    )
    val association2 = PrisonerNonAssociationDetailDto(
      otherPrisonerDetails = OtherPrisonerDetails(prisonerNumber = "AA1234DD"),
    )
    val association3 = PrisonerNonAssociationDetailDto(
      otherPrisonerDetails = OtherPrisonerDetails(prisonerNumber = "AA1234EE"),
    )

    whenever(
      nonAssociationsApiClient.getPrisonerNonAssociation(prisonerId),
    ).thenReturn(
      PrisonerNonAssociationDetailsDto(
        listOf(
          association1,
          association2,
          association3,
        ),
      ),
    )

    // When
    val prisonerNonAssociations = prisonerService.getPrisonerNonAssociationList(prisonerId)

    // Then
    assertThat(prisonerNonAssociations.size).isEqualTo(3)
    val prisonerNonAssociationList = prisonerNonAssociations.stream().map { o -> o.otherPrisonerDetails.prisonerNumber }.toList()
    assertThat(prisonerNonAssociationList.containsAll(listOf("AA1234EE", "AA1234EE", "AA1234EE")))
    Mockito.verify(nonAssociationsApiClient, times(1)).getPrisonerNonAssociation(prisonerId)
  }

  @Test
  fun `when prison api non association call returns NOT FOUND empty list is returned`() {
    val prisonerId = "AA1234BB"

    whenever(
      nonAssociationsApiClient.getPrisonerNonAssociation(prisonerId),
    ).thenReturn(null)

    // When
    val prisonerNonAssociations = prisonerService.getPrisonerNonAssociationList(prisonerId)

    // Then
    assertThat(prisonerNonAssociations).isEmpty()
    Mockito.verify(nonAssociationsApiClient, times(1)).getPrisonerNonAssociation(prisonerId)
  }

  @Test
  fun `when prison api non association call throws WebClientResponseException for BAD REQUEST it is thrown`() {
    val prisonerId = "AA1234BB"

    whenever(
      nonAssociationsApiClient.getPrisonerNonAssociation(prisonerId),
    ).thenThrow(
      WebClientResponseException.create(HttpStatus.BAD_REQUEST.value(), "", HttpHeaders.EMPTY, byteArrayOf(), null),
    )

    // When
    assertThrows<WebClientResponseException> {
      prisonerService.getPrisonerNonAssociationList(prisonerId)
    }

    // Then
    Mockito.verify(nonAssociationsApiClient, times(1)).getPrisonerNonAssociation(prisonerId)
  }

  @Test
  fun `when valid prisoner then prisoner housing location is returned`() {
    val prisonerId = "AA1234BB"

    val level1 = PrisonerHousingLevelDto(level = 1, code = "A", description = "level 1")
    val level2 = PrisonerHousingLevelDto(level = 2, code = "B", description = "level 2")
    val level3 = PrisonerHousingLevelDto(level = 3, code = "C", description = "level 3")
    val prisonerHousingLocationsDto = PrisonerHousingLocationsDto(listOf(level1, level2, level3))

    whenever(
      prisonApiClient.getPrisonerHousingLocation(prisonerId),
    ).thenReturn(prisonerHousingLocationsDto)

    // When
    val prisonerDetails = prisonerService.getPrisonerHousingLocation(prisonerId, prisonerCode)

    // Then
    assertThat(prisonerDetails).isNotNull
    assertThat(getLevel(prisonerDetails!!, PrisonerHousingLevels.LEVEL_ONE)).isEqualTo(level1.code)
    assertThat(getLevel(prisonerDetails, PrisonerHousingLevels.LEVEL_TWO)).isEqualTo(level2.code)
    assertThat(getLevel(prisonerDetails, PrisonerHousingLevels.LEVEL_THREE)).isEqualTo(level3.code)
    assertThat(getLevel(prisonerDetails, PrisonerHousingLevels.LEVEL_FOUR)).isNull()
    Mockito.verify(prisonApiClient, times(1)).getPrisonerHousingLocation(prisonerId)
  }

  @Test
  fun `when prison api call for housing location returns NOT FOUND null value is returned`() {
    val prisonerId = "AA1234BB"

    whenever(
      prisonApiClient.getPrisonerHousingLocation(prisonerId),
    ).thenReturn(null)

    // When
    val prisonerDetails = prisonerService.getPrisonerHousingLocation(prisonerId, prisonerCode)

    // Then
    assertThat(prisonerDetails).isNull()
    Mockito.verify(prisonApiClient, times(1)).getPrisonerHousingLocation(prisonerId)
  }

  @Test
  fun `when prison API throws WebClientResponseException for BAD REQUEST get housing location throws WebClientResponseException`() {
    val prisonerId = "AA1234BB"

    whenever(
      prisonApiClient.getPrisonerHousingLocation(prisonerId),
    ).thenThrow(
      WebClientResponseException.create(HttpStatus.BAD_REQUEST.value(), "", HttpHeaders.EMPTY, byteArrayOf(), null),
    )

    // When
    assertThrows<WebClientResponseException> {
      prisonerService.getPrisonerHousingLocation(prisonerId, prisonerCode)
    }

    // Then
    Mockito.verify(prisonApiClient, times(1)).getPrisonerHousingLocation(prisonerId)
  }

  @Test
  fun `when the prison has only 1 level housing a single level is returned for a valid prisoner`() {
    val prisonerId = "AA1234BB"
    val level1 = PrisonerHousingLevelDto(level = 1, code = "A", description = "test")
    val prisonerHousingLocationsDto = PrisonerHousingLocationsDto(listOf(level1))

    whenever(
      prisonApiClient.getPrisonerHousingLocation(prisonerId),
    ).thenReturn(prisonerHousingLocationsDto)

    // When
    val prisonerHousingLevels = prisonerService.getPrisonerHousingLocation(prisonerId, prisonerCode)

    // Then
    assertThat(prisonerHousingLevels).isNotNull
    assertThat(prisonerHousingLevels!!.levels).isNotEmpty
    assertThat(getLevel(prisonerHousingLevels, PrisonerHousingLevels.LEVEL_ONE)).isEqualTo(level1.code)
    assertThat(getLevel(prisonerHousingLevels, PrisonerHousingLevels.LEVEL_TWO)).isNull()
    assertThat(getLevel(prisonerHousingLevels, PrisonerHousingLevels.LEVEL_THREE)).isNull()
    assertThat(getLevel(prisonerHousingLevels, PrisonerHousingLevels.LEVEL_FOUR)).isNull()

    Mockito.verify(prisonApiClient, times(1)).getPrisonerHousingLocation(prisonerId)
  }

  @Test
  fun `when prison has multiple level housing all levels are returned for a valid prisoner`() {
    val prisonerId = "AA1234BB"
    val level1 = PrisonerHousingLevelDto(level = 1, code = "A", description = "level1")
    val level2 = PrisonerHousingLevelDto(level = 2, code = "001", description = "level2")
    val level3 = PrisonerHousingLevelDto(level = 3, code = "N", description = "level3")
    val level4 = PrisonerHousingLevelDto(level = 4, code = "30001", description = "level4")
    val prisonerHousingLocationsDto = PrisonerHousingLocationsDto(listOf(level1, level2, level3, level4))

    whenever(
      prisonApiClient.getPrisonerHousingLocation(prisonerId),
    ).thenReturn(prisonerHousingLocationsDto)

    // When
    val prisonerHousingLevels = prisonerService.getPrisonerHousingLocation(prisonerId, prisonerCode)

    // Then
    assertThat(prisonerHousingLevels).isNotNull
    assertThat(prisonerHousingLevels!!.levels).isNotEmpty
    assertThat(getLevel(prisonerHousingLevels, PrisonerHousingLevels.LEVEL_ONE)).isEqualTo(level1.code)
    assertThat(getLevel(prisonerHousingLevels, PrisonerHousingLevels.LEVEL_TWO)).isEqualTo(level2.code)
    assertThat(getLevel(prisonerHousingLevels, PrisonerHousingLevels.LEVEL_THREE)).isEqualTo(level3.code)
    assertThat(getLevel(prisonerHousingLevels, PrisonerHousingLevels.LEVEL_FOUR)).isEqualTo(level4.code)

    Mockito.verify(prisonApiClient, times(1)).getPrisonerHousingLocation(prisonerId)
  }

  @Test
  fun `when prisoner does not have housing levels no levels are returned for a valid prisoner`() {
    val prisonerId = "AA1234BB"
    val prisonerHousingLocationsDto = PrisonerHousingLocationsDto(listOf())

    whenever(
      prisonApiClient.getPrisonerHousingLocation(prisonerId),
    ).thenReturn(prisonerHousingLocationsDto)

    // When
    val prisonerHousingLevels = prisonerService.getPrisonerHousingLocation(prisonerId, prisonerCode)

    // Then
    assertThat(prisonerHousingLevels).isNotNull
    assertThat(prisonerHousingLevels!!.levels).isEmpty()
    assertThat(getLevel(prisonerHousingLevels, PrisonerHousingLevels.LEVEL_ONE)).isNull()
    assertThat(getLevel(prisonerHousingLevels, PrisonerHousingLevels.LEVEL_TWO)).isNull()
    assertThat(getLevel(prisonerHousingLevels, PrisonerHousingLevels.LEVEL_THREE)).isNull()
    assertThat(getLevel(prisonerHousingLevels, PrisonerHousingLevels.LEVEL_FOUR)).isNull()

    Mockito.verify(prisonApiClient, times(1)).getPrisonerHousingLocation(prisonerId)
  }

  @Test
  fun `when prison api client housing level call returns NOT FOUND empty list is returned`() {
    val prisonerId = "AA1234BB"

    whenever(
      prisonApiClient.getPrisonerHousingLocation(prisonerId),
    ).thenReturn(null)

    // When
    val prisonerHousingLevels = prisonerService.getPrisonerHousingLocation(prisonerId, prisonerCode)

    // Then
    assertThat(prisonerHousingLevels).isNull()
  }

  @Test
  fun `when prison api client housing level call throws WebClientResponseException for BAD REQUEST it is thrown`() {
    val prisonerId = "AA1234BB"

    whenever(
      prisonApiClient.getPrisonerHousingLocation(prisonerId),
    ).thenThrow(
      WebClientResponseException.create(HttpStatus.BAD_REQUEST.value(), "", HttpHeaders.EMPTY, byteArrayOf(), null),
    )

    // When
    assertThrows<WebClientResponseException> {
      prisonerService.getPrisonerHousingLocation(prisonerId, prisonerCode)
    }

    // Then
    Mockito.verify(prisonApiClient, times(1)).getPrisonerHousingLocation(prisonerId)
  }

  @Test
  fun `when valid prisoner then prisoner details are returned`() {
    val prisonerId = "AA1234BB"
    val prisonCode = "MDI"
    val prisonerDetailsDto = PrisonerSearchResultDto(prisonerId, null, prisonCode, null)

    whenever(
      prisonerOffenderSearchClient.getPrisoner(prisonerId),
    ).thenReturn(prisonerDetailsDto)

    // When
    val prisonerDetails = prisonerService.getPrisoner(prisonerId)

    // Then
    assertThat(prisonerDetails).isNotNull
    assertThat(prisonerDetails!!.prisonerId).isEqualTo(prisonerId)
    assertThat(prisonerDetails.prisonCode).isEqualTo("MDI")

    Mockito.verify(prisonerOffenderSearchClient, times(1)).getPrisoner(prisonerId)
  }

  @Test
  fun `when prison api call for prisoner details returns NOT FOUND null value is returned`() {
    val prisonerId = "AA1234BB"

    whenever(
      prisonerOffenderSearchClient.getPrisoner(prisonerId),
    ).thenReturn(null)
    // When
    val prisonerDetails = prisonerService.getPrisoner(prisonerId)

    // Then
    assertThat(prisonerDetails).isNull()
    Mockito.verify(prisonerOffenderSearchClient, times(1)).getPrisoner(prisonerId)
  }

  @Test
  fun `when prison API throws WebClientResponseException for BAD REQUEST get prisoner details throws WebClientResponseException`() {
    val prisonerId = "AA1234BB"

    whenever(
      prisonerOffenderSearchClient.getPrisoner(prisonerId),
    ).thenThrow(
      WebClientResponseException.create(HttpStatus.BAD_REQUEST.value(), "", HttpHeaders.EMPTY, byteArrayOf(), null),
    )

    // When
    assertThrows<WebClientResponseException> {
      prisonerService.getPrisoner(prisonerId)
    }

    // Then
    Mockito.verify(prisonerOffenderSearchClient, times(1)).getPrisoner(prisonerId)
  }

  @Test
  fun `when prisoner has category value is returned`() {
    // Given
    val prisonerId = "AA1234BB"
    val category = "category test"

    val prisonerSearchResultDto = PrisonerSearchResultDto(category = category)

    whenever(
      prisonerOffenderSearchClient.getPrisoner(prisonerId),
    ).thenReturn(prisonerSearchResultDto)

    // When
    val prisoner = prisonerService.getPrisoner(prisonerId)

    // Then
    assertThat(prisoner?.category).isEqualTo(category)
    Mockito.verify(prisonerOffenderSearchClient, times(1)).getPrisoner(prisonerId)
  }

  @Test
  fun `when prisoner has enhanced incentive level then enhanced incentive level is returned`() {
    // Given
    val prisonerId = "AA1234BB"
    val prisonId = "MDI"
    val enhancedIncentiveDto = IncentiveLevelDto("ENH", "Enhanced")

    val currentIncentive = CurrentIncentiveDto(level = enhancedIncentiveDto, dateTime = LocalDateTime.now().minusMonths(2))
    val prisonerSearchResultDto = PrisonerSearchResultDto(prisonerId, currentIncentive, prisonId)

    whenever(
      prisonerOffenderSearchClient.getPrisoner(prisonerId),
    ).thenReturn(prisonerSearchResultDto)

    // When
    val prisoner = prisonerService.getPrisoner(prisonerId)

    // Then
    assertThat(prisoner?.incentiveLevel).isEqualTo(IncentiveLevel.ENHANCED)
    Mockito.verify(prisonerOffenderSearchClient, times(1)).getPrisoner(prisonerId)
  }

  @Test
  fun `when prisoner has standard incentive level then standard incentive level is returned`() {
    // Given
    val prisonerId = "AA1234BB"
    val prisonId = "MDI"
    val standardIncentiveDto = IncentiveLevelDto("STD", "Standard")

    val currentIncentive = CurrentIncentiveDto(level = standardIncentiveDto, dateTime = LocalDateTime.now().minusMonths(2))
    val prisonerSearchResultDto = PrisonerSearchResultDto(prisonerId, currentIncentive, prisonId)

    whenever(
      prisonerOffenderSearchClient.getPrisoner(prisonerId),
    ).thenReturn(prisonerSearchResultDto)

    // When
    val prisoner = prisonerService.getPrisoner(prisonerId)

    // Then
    assertThat(prisoner?.incentiveLevel).isEqualTo(IncentiveLevel.STANDARD)
    Mockito.verify(prisonerOffenderSearchClient, times(1)).getPrisoner(prisonerId)
  }

  @Test
  fun `when prisoner has an invalid incentive level then incentive level is returned as null`() {
    // Given
    val prisonerId = "AA1234BB"
    val prisonId = "MDI"
    val otherIncentiveDto = IncentiveLevelDto("OTH", "OTHER")

    val currentIncentive = CurrentIncentiveDto(level = otherIncentiveDto, dateTime = LocalDateTime.now().minusMonths(2))
    val prisonerSearchResultDto = PrisonerSearchResultDto(prisonerId, currentIncentive, prisonId)

    whenever(
      prisonerOffenderSearchClient.getPrisoner(prisonerId),
    ).thenReturn(prisonerSearchResultDto)

    // When
    val prisoner = prisonerService.getPrisoner(prisonerId)

    // Then
    assertThat(prisoner?.incentiveLevel).isNull()
    Mockito.verify(prisonerOffenderSearchClient, times(1)).getPrisoner(prisonerId)
  }

  @Test
  fun `when prisoner has incentive level code as NULL then incentive level is returned as null`() {
    // Given
    val prisonerId = "AA1234BB"
    val prisonId = "MDI"

    // this scenario is not possible based on conversation with prisoner-offender-search team
    val nullIncentiveDto = IncentiveLevelDto(null, "NULL")

    val currentIncentive = CurrentIncentiveDto(level = nullIncentiveDto, dateTime = LocalDateTime.now().minusMonths(2))
    val prisonerSearchResultDto = PrisonerSearchResultDto(prisonerId, currentIncentive, prisonId)

    whenever(
      prisonerOffenderSearchClient.getPrisoner(prisonerId),
    ).thenReturn(prisonerSearchResultDto)

    // When
    val prisoner = prisonerService.getPrisoner(prisonerId)

    // Then
    assertThat(prisoner?.incentiveLevel).isNull()
    Mockito.verify(prisonerOffenderSearchClient, times(1)).getPrisoner(prisonerId)
  }

  @Test
  fun `when prisoner has current incentive as null then incentive level is returned as null`() {
    // Given
    val prisonerId = "AA1234BB"
    val prisonId = "MDI"

    val currentIncentive = null
    val prisonerSearchResultDto = PrisonerSearchResultDto(prisonerId, currentIncentive, prisonId)

    whenever(
      prisonerOffenderSearchClient.getPrisoner(prisonerId),
    ).thenReturn(prisonerSearchResultDto)

    // When
    val prisoner = prisonerService.getPrisoner(prisonerId)

    // Then
    assertThat(prisoner?.incentiveLevel).isNull()
    Mockito.verify(prisonerOffenderSearchClient, times(1)).getPrisoner(prisonerId)
  }

  @Test
  fun `when prisoner search call returns NOT FOUND then incentive level is returned as null`() {
    // Given
    val prisonerId = "AA1234BB"

    whenever(
      prisonerOffenderSearchClient.getPrisoner(prisonerId),
    ).thenReturn(null)

    // When
    val prisonerDto = prisonerService.getPrisoner(prisonerId)

    // Then
    assertThat(prisonerDto?.incentiveLevel).isNull()
    Mockito.verify(prisonerOffenderSearchClient, times(1)).getPrisoner(prisonerId)
  }

  @Test
  fun `when prisoner search call throws WebClientResponseException for BAD REQUEST then check for enhanced privilege throws WebClientResponseException`() {
    // Given
    val prisonerId = "AA1234BB"

    // When
    whenever(
      prisonerOffenderSearchClient.getPrisoner(prisonerId),
    ).thenThrow(
      WebClientResponseException.create(HttpStatus.BAD_REQUEST.value(), "", HttpHeaders.EMPTY, byteArrayOf(), null),
    )

    // Then
    assertThrows<WebClientResponseException> {
      prisonerService.getPrisoner(prisonerId)
    }

    Mockito.verify(prisonerOffenderSearchClient, times(1)).getPrisoner(prisonerId)
  }

  private fun getLevel(prisonerHousingLocationsDto: PrisonerHousingLocationsDto, level: PrisonerHousingLevels): String? {
    val levels = prisonerService.getLevelsMapForPrisoner(prisonerHousingLocationsDto)
    return levels[level]
  }
}
