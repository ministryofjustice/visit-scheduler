package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonerOffenderSearchClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.OffenderNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.OffenderNonAssociationDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.OffenderNonAssociationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch.PrisonerSearchResultDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NonAssociationChangedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.specification.VisitSpecification
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class VisitNotificationServiceTest {

  private val visitRepository = mock<VisitRepository>()
  private val prisonerOffenderSearchClient = mock<PrisonerOffenderSearchClient>()
  private val telemetryClientService = mock<TelemetryClientService>()
  private val visitNotificationEventRepository = mock<VisitNotificationEventRepository>()
  private val prisonerService = mock<PrisonerService>()

  private lateinit var visitNotificationService: VisitNotificationService

  private val primaryNonAssociationNumber = "AB23456"
  private val secondaryNonAssociationNumber = "ZZ67890"
  private val prisonCode = "ABC"

  @BeforeEach
  fun beforeEachTestSetup() {
    visitNotificationService = VisitNotificationService(visitRepository, telemetryClientService, prisonerOffenderSearchClient, visitNotificationEventRepository, prisonerService)

    whenever(prisonerOffenderSearchClient.getPrisoner(primaryNonAssociationNumber)).thenReturn(
      PrisonerSearchResultDto(
        prisonerNumber = primaryNonAssociationNumber,
        prisonId = prisonCode,
      ),
    )
  }

  fun mockOffenderNonAssociationList(effectiveDate: LocalDate = LocalDate.now(), expiryDate: LocalDate? = null) {
    whenever(
      prisonerService.getOffenderNonAssociationList(primaryNonAssociationNumber),
    ).thenReturn(
      OffenderNonAssociationDetailsDto(
        listOf(
          OffenderNonAssociationDetailDto(
            effectiveDate = effectiveDate,
            expiryDate = expiryDate,
            offenderNonAssociation = OffenderNonAssociationDto(offenderNo = secondaryNonAssociationNumber),
          ),
        ),
      ).nonAssociations,
    )
  }

  @Test
  fun `when to date is in the past then no call is made to get visits or save events`() {
    // Given
    val fromDate = LocalDate.now().minusMonths(1)
    val toDate = LocalDate.now().minusDays(1)

    mockOffenderNonAssociationList(fromDate, toDate)

    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryNonAssociationNumber, secondaryNonAssociationNumber, fromDate, toDate)

    // When
    visitNotificationService.handleNonAssociations(nonAssociationChangedNotification)

    // Then
    Mockito.verify(visitRepository, times(0)).findAll(any<VisitSpecification>())
    Mockito.verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }

  @Test
  fun `when non association prisoners have no visits then no calls are made to handle visit with non association`() {
    // Given
    val fromDate = LocalDate.now().minusMonths(1)
    mockOffenderNonAssociationList(fromDate)

    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryNonAssociationNumber, secondaryNonAssociationNumber, fromDate, null)

    whenever(visitRepository.findAll(any<VisitSpecification>())).thenReturn(
      emptyList(),
    )

    // When
    visitNotificationService.handleNonAssociations(nonAssociationChangedNotification)

    // Then
    Mockito.verify(visitRepository, times(2)).findAll(any<VisitSpecification>())
    Mockito.verify(telemetryClientService, times(0)).trackEvent(any(), any())
    Mockito.verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }
}
