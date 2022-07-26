package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
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
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.SessionConflict
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.specification.VisitSpecification
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@ExtendWith(MockitoExtension::class)
class SessionServiceTest {

  private val sessionTemplateRepository = mock<SessionTemplateRepository>()
  private val visitRepository = mock<VisitRepository>()
  private val prisonApiClient = mock<PrisonApiClient>()

  private lateinit var sessionService: SessionService

  // today is Friday Jan 1st
  private val date = LocalDate.parse("2021-01-01")
  private val time = LocalTime.parse("11:15")

  private val prisonId = "MDI"
  private val noticeDaysMin = 1L
  private val noticeDaysMax = 100L

  private fun mockSessionTemplateRepositoryResponse(response: List<SessionTemplate>) {
    whenever(
      sessionTemplateRepository.findValidSessionTemplatesByPrisonId(
        prisonId,
        date.plusDays(noticeDaysMin),
        date.plusDays(noticeDaysMax)
      )
    ).thenReturn(response)
  }

  private fun mockVisitRepositoryResponse(response: List<Visit>) {
    whenever(visitRepository.findAll(any(VisitSpecification::class.java)))
      .thenReturn(response)
  }

  @Nested
  @DisplayName("simple session generation")
  inner class SlotGeneration {
    @BeforeEach
    fun setUp() {
      sessionService = SessionService(
        sessionTemplateRepository,
        visitRepository,
        prisonApiClient,
        Clock.fixed(date.atTime(time).toInstant(ZoneOffset.UTC), ZoneId.systemDefault()),
        policyNoticeDaysMin = noticeDaysMin,
        policyNoticeDaysMax = noticeDaysMax,
        policyFilterDoubleBooking = false,
        policyFilterNonAssociation = false,
        policyNonAssociationWholeDay = true,
      )
    }

    @Test
    fun `a weekly session will return 6 sessions including today and valid to date`() {

      // Given
      val weeklySession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(5),
        openCapacity = 10,
        closedCapacity = 5,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
        dayOfWeek = FRIDAY
      )
      mockSessionTemplateRepositoryResponse(listOf(weeklySession))

      // When
      val sessions = sessionService.getVisitSessions(prisonId)

      // Then
      assertThat(sessions).size().isEqualTo(5) // expiry date is inclusive
      assertDate(sessions[0].startTimestamp, "2021-01-08T11:30:00", FRIDAY)
      assertDate(sessions[1].startTimestamp, "2021-01-15T11:30:00", FRIDAY)
      assertDate(sessions[2].startTimestamp, "2021-01-22T11:30:00", FRIDAY)
      assertDate(sessions[3].startTimestamp, "2021-01-29T11:30:00", FRIDAY)
      assertDate(sessions[4].startTimestamp, "2021-02-05T11:30:00", FRIDAY)
    }

    @Test
    fun `sessions are consistently generated, weekly sessions always fall on the same day regardless of date of generation`() {

      // Given
      val weeklySession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(5), // 5 weeks from today
        openCapacity = 10,
        closedCapacity = 5,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
        dayOfWeek = WEDNESDAY
      )
      mockSessionTemplateRepositoryResponse(listOf(weeklySession))

      // When
      val sessions = sessionService.getVisitSessions(prisonId)

      // Then
      assertThat(sessions).size().isEqualTo(5) // expiry date is inclusive
      assertDate(sessions[0].startTimestamp, "2021-01-06T11:30:00", WEDNESDAY)
      assertDate(sessions[1].startTimestamp, "2021-01-13T11:30:00", WEDNESDAY)
      assertDate(sessions[2].startTimestamp, "2021-01-20T11:30:00", WEDNESDAY)
      assertDate(sessions[3].startTimestamp, "2021-01-27T11:30:00", WEDNESDAY)
      assertDate(sessions[4].startTimestamp, "2021-02-03T11:30:00", WEDNESDAY)
    }

    @Test
    fun `a single session will return 1 session`() {

      // Given
      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"), // future time
        endTime = LocalTime.parse("12:30") // future time
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      // When
      val sessions = sessionService.getVisitSessions(prisonId)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      assertDate(sessions[0].startTimestamp, "2021-01-04T11:30:00", MONDAY)
    }

    @Test
    fun `all sessions are on past dates, no sessions are returned`() {

      // Given
      val dailySession = sessionTemplate(
        validFromDate = date.minusDays(8),
        validToDate = date.minusDays(1),
        dayOfWeek = MONDAY,
      )
      mockSessionTemplateRepositoryResponse(listOf(dailySession))

      // When
      val sessions = sessionService.getVisitSessions(prisonId)

      // Then
      assertThat(sessions).size().isEqualTo(0)
    }

    @Test
    fun `Single Session without Visit has zero Open and zero Closed slot count`() {
      // Given
      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"), // future time
        endTime = LocalTime.parse("12:30") // future time
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      // When
      val sessions = sessionService.getVisitSessions(prisonId)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      assertThat(sessions[0].openVisitBookedCount).isEqualTo(0)
      assertThat(sessions[0].closedVisitBookedCount).isEqualTo(0)
    }

    @Test
    fun `Single Session with BOOKED Visit has booked slot count`() {

      // Given
      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"), // future time
        endTime = LocalTime.parse("12:30") // future time
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      val visit = Visit(
        prisonerId = "Anythingwilldo",
        visitStart = date.atTime(11, 30),
        visitEnd = date.atTime(12, 30),
        visitType = SOCIAL,
        prisonId = prisonId,
        visitStatus = BOOKED,
        visitRestriction = OPEN,
        visitRoom = "123c"
      )
      mockVisitRepositoryResponse(listOf(visit))

      // When
      val sessions = sessionService.getVisitSessions(prisonId)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      assertThat(sessions[0].openVisitBookedCount).isEqualTo(1)
      assertThat(sessions[0].closedVisitBookedCount).isEqualTo(1)
    }

    @Test
    fun `Single Session with RESERVED Visit has booked slot count`() {
      // Given
      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"), // future time
        endTime = LocalTime.parse("12:30") // future time
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      val visit = Visit(
        prisonerId = "Anythingwilldo",
        visitStart = date.atTime(11, 30),
        visitEnd = date.atTime(12, 30),
        visitType = SOCIAL,
        prisonId = prisonId,
        visitStatus = RESERVED,
        visitRestriction = OPEN,
        visitRoom = "123c"
      )
      mockVisitRepositoryResponse(listOf(visit))

      // When
      val sessions = sessionService.getVisitSessions(prisonId)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      assertThat(sessions[0].openVisitBookedCount).isEqualTo(1)
      assertThat(sessions[0].closedVisitBookedCount).isEqualTo(1)
    }
  }

  @Nested
  @DisplayName("Available slots including conflicts")
  inner class IncludeConflicts {

    @BeforeEach
    fun setUp() {
      sessionService = SessionService(
        sessionTemplateRepository,
        visitRepository,
        prisonApiClient,
        Clock.fixed(date.atTime(time).toInstant(ZoneOffset.UTC), ZoneId.systemDefault()),
        policyNoticeDaysMin = noticeDaysMin,
        policyNoticeDaysMax = noticeDaysMax,
        policyFilterDoubleBooking = false,
        policyFilterNonAssociation = false,
        policyNonAssociationWholeDay = true,
      )
    }

    @Test
    fun `session does not contain conflicts when an offender has no non-associations and no double bookings`() {
      // Given
      val prisonerId = "A1234AA"

      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30")
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenReturn(OffenderNonAssociationDetailsDto())

      // When
      val sessions = sessionService.getVisitSessions(prisonId, prisonerId)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      assertDate(sessions[0].startTimestamp, "2021-01-04T11:30:00", MONDAY)
      assertThat(sessions[0].sessionConflicts).isEmpty()
      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }

    @Test
    fun `session does not contain conflicts when an offender has a valid non-association without bookings`() {
      // Given
      val prisonerId = "A1234AA"
      val associationId = "B1234BB"

      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = FRIDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30")
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenReturn(
        OffenderNonAssociationDetailsDto(
          listOf(
            OffenderNonAssociationDetailDto(
              effectiveDate = date.minusMonths(1),
              expiryDate = date.plusMonths(1),
              offenderNonAssociation = OffenderNonAssociationDto(offenderNo = associationId)
            )
          )
        )
      )

      whenever(visitRepository.findAll(any(VisitSpecification::class.java))).thenReturn(emptyList())

      // When
      val sessions = sessionService.getVisitSessions(prisonId, prisonerId)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      assertDate(sessions[0].startTimestamp, "2021-01-08T11:30:00", FRIDAY)
      assertThat(sessions[0].sessionConflicts).isEmpty()
      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }

    @Test
    fun `sessions contain conflicts when an offender has a valid non-association with a booking`() {
      // Given
      val prisonerId = "A1234AA"
      val associationId = "B1234BB"

      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = SATURDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30")
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenReturn(
        OffenderNonAssociationDetailsDto(
          listOf(
            OffenderNonAssociationDetailDto(
              effectiveDate = date.minusMonths(1),
              expiryDate = date.plusMonths(1),
              offenderNonAssociation = OffenderNonAssociationDto(offenderNo = associationId)
            )
          )
        )
      )

      whenever(visitRepository.findAll(any(VisitSpecification::class.java)))
        .thenReturn(
          listOf(
            Visit(
              prisonerId = associationId,
              visitStart = date.plusDays(2).atTime(10, 30),
              visitEnd = date.plusDays(2).atTime(11, 30),
              visitType = SOCIAL,
              prisonId = prisonId,
              visitStatus = BOOKED,
              visitRestriction = OPEN,
              visitRoom = "123c",
            )
          ),
          emptyList()
        )

      // When
      val sessions = sessionService.getVisitSessions(prisonId, prisonerId)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      assertDate(sessions[0].startTimestamp, "2021-01-02T11:30:00", SATURDAY)
      assertThat(sessions[0].sessionConflicts).size().isEqualTo(1)
      assertThat(sessions[0].sessionConflicts!!.first()).isEqualTo(SessionConflict.NON_ASSOCIATION)
      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }

    @Test
    fun `sessions contain conflicts when an offender has a double booking`() {
      // Given
      val prisonerId = "A1234AA"

      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
        dayOfWeek = SATURDAY,
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenReturn(OffenderNonAssociationDetailsDto())

      whenever(visitRepository.findAll(any(VisitSpecification::class.java)))
        .thenReturn(
          listOf(
            Visit(
              prisonerId = prisonId,
              visitStart = date.plusDays(1).atTime(11, 30),
              visitEnd = date.plusDays(1).atTime(12, 30),
              visitType = SOCIAL,
              prisonId = prisonId,
              visitStatus = BOOKED,
              visitRestriction = OPEN,
              visitRoom = "123c",
            )
          )
        )

      // When
      val sessions = sessionService.getVisitSessions(prisonId, prisonerId)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      assertDate(sessions[0].startTimestamp, "2021-01-02T11:30:00", SATURDAY)
      assertThat(sessions[0].sessionConflicts).size().isEqualTo(1)
      assertThat(sessions[0].sessionConflicts!!.first()).isEqualTo(SessionConflict.DOUBLE_BOOKED)
      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }

    @Test
    fun `session does not contain conflicts when an offender non-association NOT FOUND`() {
      // Given
      val prisonerId = "A1234AA"

      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30")
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenThrow(
        WebClientResponseException.create(HttpStatus.NOT_FOUND.value(), "", HttpHeaders.EMPTY, byteArrayOf(), null)
      )

      // When
      val sessions = sessionService.getVisitSessions(prisonId, prisonerId)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      assertDate(sessions[0].startTimestamp, "2021-01-04T11:30:00", MONDAY)
      assertThat(sessions[0].sessionConflicts).isEmpty()
      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }

    @Test
    fun `get sessions throws WebClientResponseException for BAD REQUEST`() {

      // Given
      val prisonerId = "A1234AA"

      val singleSession = sessionTemplate(
        validFromDate = date,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30")
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenThrow(
        WebClientResponseException.create(HttpStatus.BAD_REQUEST.value(), "", HttpHeaders.EMPTY, byteArrayOf(), null)
      )

      // When
      assertThrows<WebClientResponseException> {
        sessionService.getVisitSessions(prisonId, prisonerId)
      }

      // Then
      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }
  }

  @Nested
  @DisplayName("Available slots exclude conflicts")
  inner class ExcludeConflicts {

    @BeforeEach
    fun setUp() {
      sessionService = SessionService(
        sessionTemplateRepository,
        visitRepository,
        prisonApiClient,
        Clock.fixed(date.atTime(time).toInstant(ZoneOffset.UTC), ZoneId.systemDefault()),
        policyNoticeDaysMin = noticeDaysMin,
        policyNoticeDaysMax = noticeDaysMax,
        policyFilterDoubleBooking = true,
        policyFilterNonAssociation = true,
        policyNonAssociationWholeDay = true,
      )
    }

    @Test
    fun `all sessions are returned when an offender has no non-associations and no double bookings`() {
      // Given
      val prisonerId = "A1234AA"

      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30")
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenReturn(OffenderNonAssociationDetailsDto())

      // When
      val sessions = sessionService.getVisitSessions(prisonId, prisonerId)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      assertDate(sessions[0].startTimestamp, "2021-01-04T11:30:00", MONDAY)
      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }

    @Test
    fun `only available sessions are returned when an offender has a valid non-association without bookings`() {
      // Given
      val prisonerId = "A1234AA"
      val associationId = "B1234BB"

      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30")
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenReturn(
        OffenderNonAssociationDetailsDto(
          listOf(
            OffenderNonAssociationDetailDto(
              effectiveDate = date.minusMonths(1),
              expiryDate = date.plusMonths(1),
              offenderNonAssociation = OffenderNonAssociationDto(offenderNo = associationId)
            )
          )
        )
      )
      whenever(visitRepository.findAll(any(VisitSpecification::class.java))).thenReturn(emptyList())

      // When
      val sessions = sessionService.getVisitSessions(prisonId, prisonerId)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }

    @Test
    fun `only available sessions are returned when an offender has a valid non-association with a booking`() {
      // Given
      val prisonerId = "A1234AA"
      val associationId = "B1234BB"

      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30")
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenReturn(
        OffenderNonAssociationDetailsDto(
          listOf(
            OffenderNonAssociationDetailDto(
              effectiveDate = date.minusMonths(1),
              expiryDate = date.plusMonths(1),
              offenderNonAssociation = OffenderNonAssociationDto(offenderNo = associationId)
            )
          )
        )
      )

      whenever(visitRepository.findAll(any(VisitSpecification::class.java)))
        .thenReturn(
          listOf(
            Visit(
              prisonerId = associationId,
              visitStart = date.plusDays(2).atTime(10, 30),
              visitEnd = date.plusDays(2).atTime(11, 30),
              visitType = SOCIAL,
              prisonId = prisonId,
              visitStatus = BOOKED,
              visitRestriction = OPEN,
              visitRoom = "123c",
            )
          )
        )

      // When
      val sessions = sessionService.getVisitSessions(prisonId, prisonerId)

      // Then
      assertThat(sessions).size().isEqualTo(0)
      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }

    @Test
    fun `only available sessions are returned when an offender has a double booking`() {
      // Given
      val prisonerId = "A1234AA"

      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30")
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenReturn(OffenderNonAssociationDetailsDto())

      whenever(visitRepository.findAll(any(VisitSpecification::class.java)))
        .thenReturn(
          listOf(
            Visit(
              prisonerId = prisonId,
              visitStart = date.plusDays(2).atTime(11, 30),
              visitEnd = date.plusDays(2).atTime(12, 30),
              visitType = SOCIAL,
              prisonId = prisonId,
              visitStatus = BOOKED,
              visitRestriction = OPEN,
              visitRoom = "123c",
            )
          )
        )

      // When
      val sessions = sessionService.getVisitSessions(prisonId, prisonerId)

      // Then
      assertThat(sessions).size().isEqualTo(0)
      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }
  }

  private fun assertDate(localDateTime: LocalDateTime, expectedlyDateTime: String, dayOfWeek: DayOfWeek) {
    assertThat(localDateTime).isEqualTo(LocalDateTime.parse(expectedlyDateTime))
    assertThat(localDateTime.dayOfWeek).isEqualTo(dayOfWeek)
  }
}
