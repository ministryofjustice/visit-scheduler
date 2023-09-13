package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.HttpHeaders
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_BOOK
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NonAssociationChangedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatNonAssociationHasChanged
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonerService
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Transactional(propagation = SUPPORTS)
@DisplayName("PUT $VISIT_BOOK")
class VisitNotificationControllerTest : IntegrationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @SpyBean
  private lateinit var visitNotificationEventRepository: VisitNotificationEventRepository

  @SpyBean
  private lateinit var prisonerService: PrisonerService

  @Autowired
  private lateinit var testVisitNotificationEventRepository: TestVisitNotificationEventRepository

  val primaryPrisonerId = "AA11BCC"
  val secondaryPrisonerId = "XX11YZZ"
  val prisonCode = "ABC"

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
    prisonOffenderSearchMockServer.stubGetPrisonerByString(primaryPrisonerId, prisonCode, IncentiveLevel.ENHANCED)
  }

  fun stubGetOffenderNonAssociationForPrisonApi(
    prisonerId: String = primaryPrisonerId,
    nonAssociationId: String = secondaryPrisonerId,
    effectiveDate: LocalDate = LocalDate.now(),
    expiryDate: LocalDate? = null,
  ) {
    Companion.prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      nonAssociationId,
      effectiveDate,
      expiryDate,
    )
  }

  @Test
  fun `when prisoners have overlapped visits then visits with same date and prison are flagged and saved`() {
    // Given
    stubGetOffenderNonAssociationForPrisonApi()
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryPrisonerId, secondaryPrisonerId, validFromDate = LocalDate.now())

    val primaryVisit1 = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(1),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )
    eventAuditEntityHelper.create(primaryVisit1)

    val primaryVisit2 = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(2),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )
    eventAuditEntityHelper.create(primaryVisit2)

    // visit does not overlap
    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(3),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    val secondaryVisit1 = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(1),
      visitStatus = BOOKED,
    )
    eventAuditEntityHelper.create(secondaryVisit1)

    val secondaryVisit2 = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(2),
      visitStatus = BOOKED,
    )
    eventAuditEntityHelper.create(secondaryVisit2)

    // visit does not overlap
    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(4),
      visitStatus = BOOKED,
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    assertBookedEvent(listOf(primaryVisit1, primaryVisit2, secondaryVisit1, secondaryVisit2))
    verify(telemetryClient, times(4)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(4)).saveAndFlush(any<VisitNotificationEvent>())
  }

  @Test
  fun `when prisoner with non associations visits has existing notification then they are not flagged or saved`() {
    // Given
    val today = LocalDateTime.now()
    stubGetOffenderNonAssociationForPrisonApi(effectiveDate = today.toLocalDate())
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryPrisonerId, secondaryPrisonerId, validFromDate = today.toLocalDate())

    val primaryVisit = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = today.plusDays(1),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    val secondaryVisit = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      visitStart = primaryVisit.visitStart,
      prisonCode = primaryVisit.prison.code,
      visitStatus = BOOKED,
    )

    testVisitNotificationEventRepository.saveAndFlush(
      VisitNotificationEvent(
        primaryVisit.reference,
        NotificationEventType.NON_ASSOCIATION_EVENT,
        today,
      ),
    )

    testVisitNotificationEventRepository.saveAndFlush(
      VisitNotificationEvent(
        secondaryVisit.reference,
        NotificationEventType.NON_ASSOCIATION_EVENT,
        today,
      ),
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }

  @Test
  fun `when non associations event is triggered but prisoner has no non associations they are not flagged or saved`() {
    // This can happen when non associations event is triggered by delete or an update

    // Given
    val today = LocalDateTime.now()
    stubGetOffenderNonAssociationForPrisonApi(prisonerId = "anotherOne", effectiveDate = today.toLocalDate())
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryPrisonerId, secondaryPrisonerId, validFromDate = today.toLocalDate())

    val primaryVisit = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = today.plusDays(1),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      visitStart = primaryVisit.visitStart,
      prisonCode = primaryVisit.prison.code,
      visitStatus = BOOKED,
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }

  @Test
  fun `when non associations event is triggered but prisoner has no non associations they are not flagged or saved 2`() {
    // This can happen when non associations event is triggered by delete or an update

    // Given
    val today = LocalDateTime.now()
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryPrisonerId, secondaryPrisonerId, validFromDate = today.toLocalDate())
    prisonApiMockServer.stubGetOffenderNonAssociationHttpError()

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    verify(prisonerService, times(1)).getOffenderNonAssociationList(nonAssociationChangedNotification.prisonerNumber)
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }

  @Test
  fun `when both prisoners have no overlapping visits then no visits are flagged or saved`() {
    // Given
    stubGetOffenderNonAssociationForPrisonApi()
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryPrisonerId, secondaryPrisonerId, validFromDate = LocalDate.now())

    // no visits overlap
    // visits for primary prisoners are for today + 1, +2 & +3
    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(1),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(2),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(3),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    // visits for secondary prisoners are for today + 4, +5 & +6
    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(4),
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(5),
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(6),
      visitStatus = BOOKED,
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }

  @Test
  fun `when primary prisoner has no future visits then no visits are flagged or saved`() {
    // Given
    stubGetOffenderNonAssociationForPrisonApi()
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryPrisonerId, secondaryPrisonerId, validFromDate = LocalDate.now())

    // no visits overlap
    // visits for primary prisoners are for today + 1, +2 & +3
    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = nonAssociationChangedNotification.validFromDate.minusDays(1).atTime(11, 0),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    // visits for secondary prisoners are for today + 4, +5 & +6
    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(4),
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(5),
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(6),
      visitStatus = BOOKED,
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }

  @Test
  fun `when secondary prisoner has no future visits then no visits are flagged or saved`() {
    // Given
    stubGetOffenderNonAssociationForPrisonApi()
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryPrisonerId, secondaryPrisonerId, validFromDate = LocalDate.now())

    // no visits overlap
    // visits for primary prisoners are for today + 1, +2 & +3
    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(1),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(2),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(3),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    // no visits for secondary prisoners
    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }

  @Test
  fun `when both prisoners have overlapping visits only in the past then no visits are flagged or saved`() {
    // Given
    stubGetOffenderNonAssociationForPrisonApi()
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryPrisonerId, secondaryPrisonerId, validFromDate = LocalDate.now())

    // no visits overlap
    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = nonAssociationChangedNotification.validFromDate.minusDays(1).atTime(11, 0),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = nonAssociationChangedNotification.validFromDate.minusDays(2).atTime(11, 0),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = nonAssociationChangedNotification.validFromDate.minusDays(3).atTime(11, 0),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = nonAssociationChangedNotification.validFromDate.minusDays(1).atTime(11, 0),
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = nonAssociationChangedNotification.validFromDate.minusDays(2).atTime(11, 0),
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = nonAssociationChangedNotification.validFromDate.minusDays(3).atTime(11, 0),
      visitStatus = BOOKED,
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }

  @Test
  fun `when future visits overlap but in different prisons then no visits are flagged or saved`() {
    // Given
    stubGetOffenderNonAssociationForPrisonApi()
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryPrisonerId, secondaryPrisonerId, validFromDate = LocalDate.now())

    // no visits overlap
    // visits for primary prisoner is for tomorrow at ABC prison
    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(1),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    // visits for secondary prisoner is for tomorrow at DEF prison
    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = "DEF",
      visitStart = LocalDateTime.now().plusDays(1),
      visitStatus = BOOKED,
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }

  @Test
  fun `when prisoners have overlapped visits only visits after from date with same date and prison are flagged and saved`() {
    // Given
    stubGetOffenderNonAssociationForPrisonApi()
    val fromDate = LocalDate.now().plusDays(2)
    stubGetOffenderNonAssociationForPrisonApi(effectiveDate = fromDate)
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryPrisonerId, secondaryPrisonerId, validFromDate = fromDate)

    // visit not flagged as before from date
    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(1),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    val primaryVisit2 = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(2),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )
    eventAuditEntityHelper.create(primaryVisit2)

    // visit does not overlap
    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(3),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    // visit not flagged as before from date
    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(1),
      visitStatus = BOOKED,
    )

    val secondaryVisit2 = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(2),
      visitStatus = BOOKED,
    )
    eventAuditEntityHelper.create(secondaryVisit2)

    // visit does not overlap
    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(4),
      visitStatus = BOOKED,
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    assertBookedEvent(listOf(primaryVisit2, secondaryVisit2))
    verify(telemetryClient, times(2)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(2)).saveAndFlush(any<VisitNotificationEvent>())
  }

  @Test
  fun `when prisoners have overlapped visits only visits before to date with same date and prison are flagged and saved`() {
    // Given
    val fromDate = LocalDate.now()
    val toDate = LocalDate.now().plusDays(1)
    stubGetOffenderNonAssociationForPrisonApi(expiryDate = toDate)

    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryPrisonerId, secondaryPrisonerId, validFromDate = fromDate, validToDate = toDate)

    val primaryVisit1 = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(1),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )
    eventAuditEntityHelper.create(primaryVisit1)

    // visit not flagged as after to date
    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(2),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    // visit does not overlap
    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(3),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    val secondaryVisit1 = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(1),
      visitStatus = BOOKED,
    )
    eventAuditEntityHelper.create(secondaryVisit1)

    // visit not flagged as after to date
    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(2),
      visitStatus = BOOKED,
    )

    // visit does not overlap
    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(4),
      visitStatus = BOOKED,
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    assertBookedEvent(listOf(primaryVisit1, secondaryVisit1))
    verify(telemetryClient, times(2)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(2)).saveAndFlush(any<VisitNotificationEvent>())
  }

  private fun assertBookedEvent(visits: List<Visit>) {
    visits.forEach { visit ->
      run {
        val eventAudit = eventAuditRepository.findLastBookedVisitEventByBookingReference(visit.reference)

        verify(telemetryClient).trackEvent(
          eq("flagged-visit-event"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["prisonId"]).isEqualTo(visit.prison.code)
            Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
            Assertions.assertThat(it["reviewType"]).isEqualTo("Non-association")
            Assertions.assertThat(it["visitBooked"]).isEqualTo(formatDateTimeToString(eventAudit.createTimestamp))
            Assertions.assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
            Assertions.assertThat(it["applicationReference"]).isEqualTo(visit.applicationReference)
            Assertions.assertThat(it["prisonerId"]).isEqualTo(visit.prisonerId)
            Assertions.assertThat(it["actionedBy"]).isEqualTo(eventAudit.actionedBy)
            Assertions.assertThat(it["visitRestriction"]).isEqualTo(visit.visitRestriction.name)
            Assertions.assertThat(it["visitStart"]).isEqualTo(formatDateTimeToString(visit.visitStart))
            Assertions.assertThat(it["visitType"]).isEqualTo(visit.visitType.name)
            Assertions.assertThat(it["visitRoom"]).isEqualTo(visit.visitRoom)
          },
          isNull(),
        )
      }
    }
  }

  private fun formatDateTimeToString(dateTime: LocalDateTime): String {
    return dateTime.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_DATE_TIME)
  }
}
