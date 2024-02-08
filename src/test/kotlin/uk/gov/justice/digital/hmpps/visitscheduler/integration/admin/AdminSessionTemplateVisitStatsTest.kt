package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.SESSION_TEMPLATE_VISIT_STATS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.RequestSessionTemplateVisitStatsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionCapacityDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateVisitCountsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateVisitStatsDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGetVisitStats
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionTemplateRepository
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Session template tests for visit stats - $SESSION_TEMPLATE_VISIT_STATS")
class AdminSessionTemplateVisitStatsTest(
  @Autowired private val testTemplateRepository: TestSessionTemplateRepository,
  @Value("\${policy.session.booking-notice-period.maximum-days:28}")
  private val policyNoticeDaysMax: Long,
) : IntegrationTestBase() {

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  private lateinit var sessionTemplate1: SessionTemplate
  private lateinit var sessionTemplate2: SessionTemplate

  @BeforeEach
  internal fun setUp() {
    sessionTemplate1 = sessionTemplateEntityHelper.create(
      name = "Session template 1",
      validFromDate = LocalDate.now(),
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      isActive = true,
    )
    sessionTemplate2 = sessionTemplateEntityHelper.create(
      name = "Session template 2",
      validFromDate = LocalDate.now(),
      startTime = LocalTime.parse("11:00"),
      endTime = LocalTime.parse("12:00"),
      isActive = true,
    )

    testTemplateRepository.saveAndFlush(sessionTemplate1)
    testTemplateRepository.saveAndFlush(sessionTemplate2)
  }

  @Test
  fun `when session template has visits then return expected results`() {
    // Given
    val reference1 = sessionTemplate1.reference

    val visitsFromDate = LocalDate.now()
    val requestSessionTemplateVisitStatsDto = RequestSessionTemplateVisitStatsDto(visitsFromDate, null)
    val tomorrow = visitsFromDate.plusDays(1)

    visitEntityHelper.create(visitStatus = BOOKED, sessionTemplate = sessionTemplate1, visitRestriction = OPEN, slotDate = tomorrow)
    visitEntityHelper.create(visitStatus = BOOKED, sessionTemplate = sessionTemplate1, visitRestriction = CLOSED, slotDate = tomorrow)

    visitEntityHelper.create(visitStatus = CANCELLED, outcomeStatus = OutcomeStatus.CANCELLATION, sessionTemplate = sessionTemplate1, visitRestriction = CLOSED, slotDate = tomorrow)
    visitEntityHelper.create(visitStatus = CANCELLED, sessionTemplate = sessionTemplate1, visitRestriction = CLOSED, slotDate = tomorrow)

    // applications are not counted
    applicationEntityHelper.create(sessionTemplate = sessionTemplate1, visitRestriction = CLOSED, slotDate = tomorrow, completed = false, reservedSlot = true)

    visitEntityHelper.create(sessionTemplate = sessionTemplate1, visitRestriction = OPEN, slotDate = visitsFromDate.plusDays(2))
    visitEntityHelper.create(sessionTemplate = sessionTemplate1, visitRestriction = OPEN, slotDate = visitsFromDate.plusDays(3))
    visitEntityHelper.create(visitStatus = BOOKED, sessionTemplate = sessionTemplate1, visitRestriction = CLOSED, slotDate = visitsFromDate.plusDays(4))
    visitEntityHelper.create(sessionTemplate = sessionTemplate2, visitRestriction = OPEN, slotDate = visitsFromDate.plusDays(3))
    visitEntityHelper.create(sessionTemplate = sessionTemplate2, visitRestriction = CLOSED, slotDate = visitsFromDate.plusDays(3))
    visitEntityHelper.create(visitStatus = BOOKED, sessionTemplate = sessionTemplate1, visitRestriction = OPEN, slotDate = visitsFromDate.plusDays(policyNoticeDaysMax))
    visitEntityHelper.create(sessionTemplate = sessionTemplate1, visitRestriction = CLOSED, slotDate = visitsFromDate.plusDays(policyNoticeDaysMax - 1))

    // When
    val responseSpec = callGetVisitStats(webTestClient, reference1, requestSessionTemplateVisitStatsDto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
    val sessionTemplateVisitStatsDto = getSessionTemplateVisitStatsDto(responseSpec)

    Assertions.assertThat(sessionTemplateVisitStatsDto.visitCount).isEqualTo(7)
    Assertions.assertThat(sessionTemplateVisitStatsDto.cancelCount).isEqualTo(2)
    Assertions.assertThat(sessionTemplateVisitStatsDto.minimumCapacity.open).isEqualTo(1)
    Assertions.assertThat(sessionTemplateVisitStatsDto.minimumCapacity.closed).isEqualTo(1)
    Assertions.assertThat(sessionTemplateVisitStatsDto.visitsByDate).size().isEqualTo(6)
    val visitsByDate = sessionTemplateVisitStatsDto.visitsByDate
    Assertions.assertThat(visitsByDate!![0]).isEqualTo(SessionTemplateVisitCountsDto(visitsFromDate.plusDays(1), SessionCapacityDto(open = 1, closed = 1)))
    Assertions.assertThat(visitsByDate[1]).isEqualTo(SessionTemplateVisitCountsDto(visitsFromDate.plusDays(2), SessionCapacityDto(open = 1, closed = 0)))
    Assertions.assertThat(visitsByDate[2]).isEqualTo(SessionTemplateVisitCountsDto(visitsFromDate.plusDays(3), SessionCapacityDto(open = 1, closed = 0)))
    Assertions.assertThat(visitsByDate[3]).isEqualTo(SessionTemplateVisitCountsDto(visitsFromDate.plusDays(4), SessionCapacityDto(open = 0, closed = 1)))
    Assertions.assertThat(visitsByDate[4]).isEqualTo(SessionTemplateVisitCountsDto(visitsFromDate.plusDays(policyNoticeDaysMax - 1), SessionCapacityDto(open = 0, closed = 1)))
  }

  private fun getSessionTemplateVisitStatsDto(responseSpec: ResponseSpec) =
    objectMapper.readValue(
      responseSpec.expectBody().returnResult().responseBody,
      SessionTemplateVisitStatsDto::class.java,
    )

  @Test
  fun `when session templates has no visits then return expected results`() {
    // Given
    val reference1 = sessionTemplate1.reference
    val visitsFromDate = LocalDate.now()
    val requestSessionTemplateVisitStatsDto = RequestSessionTemplateVisitStatsDto(visitsFromDate, null)

    // When
    val responseSpec = callGetVisitStats(webTestClient, reference1, requestSessionTemplateVisitStatsDto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
    val sessionTemplateVisitStatsDto = getSessionTemplateVisitStatsDto(responseSpec)

    Assertions.assertThat(sessionTemplateVisitStatsDto.visitCount).isEqualTo(0)
    Assertions.assertThat(sessionTemplateVisitStatsDto.minimumCapacity.open).isEqualTo(0)
    Assertions.assertThat(sessionTemplateVisitStatsDto.minimumCapacity.closed).isEqualTo(0)
    Assertions.assertThat(sessionTemplateVisitStatsDto.visitsByDate).size().isEqualTo(0)
  }

  @Test
  fun `when session templates has visits in the past then return expected results`() {
    // Given
    val reference1 = sessionTemplate1.reference
    val visitsFromDate = LocalDate.now()
    val requestSessionTemplateVisitStatsDto = RequestSessionTemplateVisitStatsDto(visitsFromDate, null)

    visitEntityHelper.create(sessionTemplate = sessionTemplate1, visitRestriction = OPEN, slotDate = visitsFromDate.minusDays(1))
    visitEntityHelper.create(sessionTemplate = sessionTemplate1, visitRestriction = OPEN, slotDate = visitsFromDate.minusDays(1))

    // When
    val responseSpec = callGetVisitStats(webTestClient, reference1, requestSessionTemplateVisitStatsDto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
    val sessionTemplateVisitStatsDto = getSessionTemplateVisitStatsDto(responseSpec)

    Assertions.assertThat(sessionTemplateVisitStatsDto.visitCount).isEqualTo(0)
    Assertions.assertThat(sessionTemplateVisitStatsDto.minimumCapacity.open).isEqualTo(0)
    Assertions.assertThat(sessionTemplateVisitStatsDto.minimumCapacity.closed).isEqualTo(0)
    Assertions.assertThat(sessionTemplateVisitStatsDto.visitsByDate).size().isEqualTo(0)
  }

  @Test
  fun `when session templates has visits past the visits to date then return expected results`() {
    // Given
    val reference1 = sessionTemplate1.reference
    val visitsFromDate = LocalDate.now()
    val visitsToDate = LocalDate.now().plusDays(policyNoticeDaysMax - 1)
    val requestSessionTemplateVisitStatsDto = RequestSessionTemplateVisitStatsDto(visitsFromDate, visitsToDate)

    // visit falls after to date
    visitEntityHelper.create(sessionTemplate = sessionTemplate1, visitRestriction = OPEN, slotDate = visitsFromDate.plusDays(policyNoticeDaysMax))

    // When
    val responseSpec = callGetVisitStats(webTestClient, reference1, requestSessionTemplateVisitStatsDto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
    val sessionTemplateVisitStatsDto = getSessionTemplateVisitStatsDto(responseSpec)

    Assertions.assertThat(sessionTemplateVisitStatsDto.visitCount).isEqualTo(0)
    Assertions.assertThat(sessionTemplateVisitStatsDto.minimumCapacity.open).isEqualTo(0)
    Assertions.assertThat(sessionTemplateVisitStatsDto.minimumCapacity.closed).isEqualTo(0)
    Assertions.assertThat(sessionTemplateVisitStatsDto.visitsByDate).size().isEqualTo(0)
  }

  @Test
  fun `when session templates has visits before the passed to date then the visit is included in stats`() {
    // Given
    val reference1 = sessionTemplate1.reference
    val visitsFromDate = LocalDate.now()

    // visitsToDate added
    val visitsToDate = LocalDate.now().plusMonths(6)
    val requestSessionTemplateVisitStatsDto = RequestSessionTemplateVisitStatsDto(visitsFromDate, visitsToDate)

    // visit date is before visits to Date
    visitEntityHelper.create(sessionTemplate = sessionTemplate1, visitRestriction = OPEN, slotDate = visitsFromDate.plusMonths(5))

    // When
    val responseSpec = callGetVisitStats(webTestClient, reference1, requestSessionTemplateVisitStatsDto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
    val sessionTemplateVisitStatsDto = getSessionTemplateVisitStatsDto(responseSpec)

    Assertions.assertThat(sessionTemplateVisitStatsDto.visitCount).isEqualTo(1)
    Assertions.assertThat(sessionTemplateVisitStatsDto.minimumCapacity.open).isEqualTo(1)
    Assertions.assertThat(sessionTemplateVisitStatsDto.minimumCapacity.closed).isEqualTo(0)
    Assertions.assertThat(sessionTemplateVisitStatsDto.visitsByDate).size().isEqualTo(1)
  }

  @Test
  fun `when session templates has visits before the passed to date then the visit is not included in stats`() {
    // Given
    val reference1 = sessionTemplate1.reference
    val visitsFromDate = LocalDate.now()

    // visitsToDate added
    val visitsToDate = LocalDate.now().plusMonths(6)
    val requestSessionTemplateVisitStatsDto = RequestSessionTemplateVisitStatsDto(visitsFromDate, visitsToDate)

    // visit date is after visits to Date
    visitEntityHelper.create(sessionTemplate = sessionTemplate1, visitRestriction = OPEN, slotDate = visitsFromDate.plusMonths(7))

    // When
    val responseSpec = callGetVisitStats(webTestClient, reference1, requestSessionTemplateVisitStatsDto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
    val sessionTemplateVisitStatsDto = getSessionTemplateVisitStatsDto(responseSpec)

    Assertions.assertThat(sessionTemplateVisitStatsDto.visitCount).isEqualTo(0)
    Assertions.assertThat(sessionTemplateVisitStatsDto.minimumCapacity.open).isEqualTo(0)
    Assertions.assertThat(sessionTemplateVisitStatsDto.minimumCapacity.closed).isEqualTo(0)
    Assertions.assertThat(sessionTemplateVisitStatsDto.visitsByDate).size().isEqualTo(0)
  }
}
