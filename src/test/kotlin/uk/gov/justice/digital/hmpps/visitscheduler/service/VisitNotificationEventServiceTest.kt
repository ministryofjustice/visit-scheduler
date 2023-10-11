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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.OffenderNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.OffenderNonAssociationDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.OffenderNonAssociationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NonAssociationChangedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitNotificationEventRepository
import java.time.LocalDate

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

  private fun mockOffenderNonAssociationList(effectiveDate: LocalDate = LocalDate.now(), expiryDate: LocalDate? = null) {
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
    visitNotificationEventService.handleNonAssociations(nonAssociationChangedNotification)

    // Then
    Mockito.verify(visitService, times(0)).getBookedVisits(any(), any(), any(), any())
    Mockito.verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }

  @Test
  fun `when non association prisoners have no visits then no calls are made to handle visit with non association`() {
    // Given
    val fromDate = LocalDate.now().minusMonths(1)
    mockOffenderNonAssociationList(fromDate)

    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryNonAssociationNumber, secondaryNonAssociationNumber, fromDate, null)

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
