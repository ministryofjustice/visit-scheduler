package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.OtherPrisonerDetails
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerNonAssociationDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NonAssociationChangedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.NonAssociationDomainEventType.NON_ASSOCIATION_CREATED

@ExtendWith(MockitoExtension::class)
class VisitNotificationEventServiceTest {

  private val visitService = mock<VisitService>()
  private val telemetryClientService = mock<TelemetryClientService>()
  private val visitNotificationEventRepository = mock<VisitNotificationEventRepository>()
  private val prisonerService = mock<PrisonerService>()

  private lateinit var visitNotificationEventService: VisitNotificationEventService

  private val primaryNonAssociationNumber = "AB23456"
  private val secondaryNonAssociationNumber = "ZZ67890"
  private val prisonCode = "ABC"

  @BeforeEach
  fun beforeEachTestSetup() {
    visitNotificationEventService = VisitNotificationEventService(visitService, telemetryClientService, visitNotificationEventRepository, prisonerService)

    whenever(prisonerService.getPrisoner(primaryNonAssociationNumber)).thenReturn(
      PrisonerDto(
        incentiveLevel = null,
        prisonCode = prisonCode,
      ),
    )
  }

  private fun mockPrisonerNonAssociationList() {
    whenever(
      prisonerService.hasPrisonerGotANonAssociationWith(primaryNonAssociationNumber, secondaryNonAssociationNumber),
    ).thenReturn(
      true,
    )

    whenever(
      prisonerService.getPrisonerNonAssociationList(primaryNonAssociationNumber),
    ).thenReturn(
      PrisonerNonAssociationDetailsDto(
        listOf(
          PrisonerNonAssociationDetailDto(
            otherPrisonerDetails = OtherPrisonerDetails(prisonerNumber = secondaryNonAssociationNumber),
          ),
        ),
      ).nonAssociations,
    )
  }

  @Test
  fun `when create non association prisoners have no visits then no calls are made to handle visit with non association`() {
    // Given
    mockPrisonerNonAssociationList()

    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(NON_ASSOCIATION_CREATED, primaryNonAssociationNumber, secondaryNonAssociationNumber)

    whenever(prisonerService.getPrisonerSupportedPrisonCode(any())).thenReturn(
      "CFI",
    )
    whenever(visitService.getBookedVisits(any(), any(), any(), any())).thenReturn(
      emptyList(),
    )

    // When
    visitNotificationEventService.handleNonAssociations(nonAssociationChangedNotification)

    // Then
    Mockito.verify(visitService, times(2)).getBookedVisits(any(), any(), any(), anyOrNull())
    Mockito.verify(telemetryClientService, times(0)).trackEvent(any(), any())
    Mockito.verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }
}
