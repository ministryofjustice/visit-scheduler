package uk.gov.justice.digital.hmpps.visitscheduler.integration

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.TestClockConfiguration
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplateDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// System time is altered using the TestClockConfiguration below
@Import(TestClockConfiguration::class)
class VisitSessionsControllerTest(@Autowired private val objectMapper: ObjectMapper) : IntegrationTestBase() {

  @Autowired
  private lateinit var sessionTemplateRepository: SessionTemplateRepository

  @Autowired
  private lateinit var visitRepository: VisitRepository

  @AfterEach
  internal fun deleteAllSessionTemplates() = sessionTemplateDeleter(sessionTemplateRepository)

  @AfterEach
  internal fun deleteAllVisitSessions() = visitDeleter(visitRepository)

  @Test
  fun `visit sessions are returned for a prison for a single schedule`() {

    // Given
    val dateTime = LocalDate.parse("2021-01-08")

    val sessionTemplate = sessionTemplate(
      startDate = dateTime,
      expiryDate = dateTime,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00")
    )

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].startTimestamp").isEqualTo("2021-01-08T09:00:00")
      .jsonPath("$[0].endTimestamp").isEqualTo("2021-01-08T10:00:00")
  }

  @Test
  fun `visit sessions are returned for a prison for a weekly schedule`() {
    // Given
    val sessionTemplate1 = sessionTemplate(
      startDate = LocalDate.parse("2021-01-08"),
      dayOfWeek = DayOfWeek.SUNDAY,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00")
    )

    val sessionTemplate2 = sessionTemplate(
      startDate = LocalDate.parse("2021-01-08"),
      dayOfWeek = DayOfWeek.MONDAY,
      startTime = LocalTime.parse("10:30"),
      endTime = LocalTime.parse("11:30")
    )

    sessionTemplateRepository.saveAndFlush(sessionTemplate1)
    sessionTemplateRepository.saveAndFlush(sessionTemplate2)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()

    val visitSessionDto = objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)

    Assertions.assertThat(visitSessionDto.size).isEqualTo(6)
    Assertions.assertThat(visitSessionDto[0].startTimestamp).isEqualTo(LocalDateTime.parse("2021-01-10T09:00:00"))
    Assertions.assertThat(visitSessionDto[0].endTimestamp).isEqualTo(LocalDateTime.parse("2021-01-10T10:00:00"))
    Assertions.assertThat(visitSessionDto[0].startTimestamp.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
    Assertions.assertThat(visitSessionDto[0].endTimestamp.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)

    Assertions.assertThat(visitSessionDto[1].startTimestamp).isEqualTo(LocalDateTime.parse("2021-01-11T10:30:00"))
    Assertions.assertThat(visitSessionDto[1].endTimestamp).isEqualTo(LocalDateTime.parse("2021-01-11T11:30:00"))
    Assertions.assertThat(visitSessionDto[1].startTimestamp.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
    Assertions.assertThat(visitSessionDto[1].endTimestamp.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)

    Assertions.assertThat(visitSessionDto[2].startTimestamp).isEqualTo(LocalDateTime.parse("2021-01-17T09:00:00"))
    Assertions.assertThat(visitSessionDto[2].endTimestamp).isEqualTo(LocalDateTime.parse("2021-01-17T10:00:00"))
    Assertions.assertThat(visitSessionDto[2].startTimestamp.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
    Assertions.assertThat(visitSessionDto[2].endTimestamp.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)

    Assertions.assertThat(visitSessionDto[3].startTimestamp).isEqualTo(LocalDateTime.parse("2021-01-18T10:30:00"))
    Assertions.assertThat(visitSessionDto[3].endTimestamp).isEqualTo(LocalDateTime.parse("2021-01-18T11:30:00"))
    Assertions.assertThat(visitSessionDto[3].startTimestamp.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
    Assertions.assertThat(visitSessionDto[3].endTimestamp.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)

    Assertions.assertThat(visitSessionDto[4].startTimestamp).isEqualTo(LocalDateTime.parse("2021-01-24T09:00:00"))
    Assertions.assertThat(visitSessionDto[4].endTimestamp).isEqualTo(LocalDateTime.parse("2021-01-24T10:00:00"))
    Assertions.assertThat(visitSessionDto[4].startTimestamp.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
    Assertions.assertThat(visitSessionDto[4].endTimestamp.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)

    Assertions.assertThat(visitSessionDto[5].startTimestamp).isEqualTo(LocalDateTime.parse("2021-01-25T10:30:00"))
    Assertions.assertThat(visitSessionDto[5].endTimestamp).isEqualTo(LocalDateTime.parse("2021-01-25T11:30:00"))
    Assertions.assertThat(visitSessionDto[5].startTimestamp.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
    Assertions.assertThat(visitSessionDto[5].endTimestamp.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
  }

  @Test
  fun `visit sessions are returned for a prison when day of week is Friday and schedule starts and ends on the same Friday`() {
    // Given
    val policyNoticeDaysMin = 0
    val policyNoticeDaysMax = 10

    val sessionTemplate = sessionTemplate(
      startDate = LocalDate.parse("2021-01-01"),
      expiryDate = LocalDate.parse("2021-01-01"),
      dayOfWeek = DayOfWeek.FRIDAY,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00")
    )

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI&min=$policyNoticeDaysMin&max=$policyNoticeDaysMax")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()

    val visitSessionDto = objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)

    Assertions.assertThat(visitSessionDto.size).isEqualTo(1)
    Assertions.assertThat(visitSessionDto[0].startTimestamp.dayOfWeek).isEqualTo(DayOfWeek.FRIDAY)
    Assertions.assertThat(visitSessionDto[0].endTimestamp.dayOfWeek).isEqualTo(DayOfWeek.FRIDAY)
  }

  @Test
  fun `visit sessions are not returned for a prison when day of week is Saturday and schedule starts and ends on previous Friday`() {
    // Given
    val policyNoticeDaysMin = 0
    val policyNoticeDaysMax = 10

    val sessionTemplate = sessionTemplate(
      startDate = LocalDate.parse("2021-01-01"),
      expiryDate = LocalDate.parse("2021-01-01"),
      dayOfWeek = DayOfWeek.SATURDAY,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00")
    )

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI&min=$policyNoticeDaysMin&max=$policyNoticeDaysMax")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(0)
  }

  @Test
  fun `visit sessions are not returned when policy notice min days is greater than max without expiry date`() {
    // Given
    val sessionTemplate = sessionTemplate(startDate = LocalDate.parse("2021-01-08"), expiryDate = null)
    val policyNoticeDaysMin = 10
    val policyNoticeDaysMax = 1

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI&min=$policyNoticeDaysMin&max=$policyNoticeDaysMax")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(0)
  }

  @Test
  fun `visit sessions are not returned when start date is after policy notice min and max days`() {
    // Given
    val sessionTemplate = sessionTemplate(startDate = LocalDate.parse("2021-01-10"), expiryDate = null)
    val policyNoticeDaysMin = 0
    val policyNoticeDaysMax = 1

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI&min=$policyNoticeDaysMin&max=$policyNoticeDaysMax")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(0)
  }

  @Test
  fun `visit sessions are not returned for when policy notice min days is greater than max with expiry date`() {
    // Given
    val sessionTemplate = sessionTemplate(startDate = LocalDate.parse("2021-01-08"), expiryDate = LocalDate.parse("2022-01-20"))
    val policyNoticeDaysMin = 10
    val policyNoticeDaysMax = 1

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI&min=$policyNoticeDaysMin&max=$policyNoticeDaysMax")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(0)
  }

  @Test
  fun `expired visit sessions are not returned`() {
    // Given
    val sessionTemplate = sessionTemplate(
      startDate = LocalDate.parse("2020-01-01"),
      expiryDate = LocalDate.parse("2020-06-01")
    )

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(0)
  }

  @Test
  fun `sessions that start after the max policy notice days after current date are not returned`() {
    // Given

    // System time is altered using the TestClockConfiguration above
    val sessionTemplate = sessionTemplate(
      startDate = LocalDate.parse("2022-01-01"),
      expiryDate = LocalDate.parse("2022-06-01")
    )
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(0)
  }

  @Test
  fun `visit sessions include reserved and booked open visit count`() {

    // Given
    val dateTime = LocalDate.parse("2021-01-08").atTime(9, 0)
    val startTime = dateTime.toLocalTime()
    val endTime = dateTime.plusHours(1)

    val sessionTemplate = sessionTemplate(
      startDate = dateTime.toLocalDate(),
      expiryDate = dateTime.toLocalDate(),
      startTime = startTime,
      endTime = endTime.toLocalTime()
    )
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit1 = Visit(
      prisonerId = "AF12345G",
      prisonId = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime,
      visitEnd = endTime,
      visitType = SOCIAL,
      visitStatus = RESERVED,
      visitRestriction = OPEN
    )

    val visit2 = visit1.copy(visitStatus = BOOKED)
    val visit3 = visit1.copy(visitStatus = CANCELLED)

    visitRepository.saveAndFlush(visit1)
    visitRepository.saveAndFlush(visit2)
    visitRepository.saveAndFlush(visit3)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].openVisitBookedCount").isEqualTo(2)
      .jsonPath("$[0].closedVisitBookedCount").isEqualTo(0)
  }

  @Test
  fun `visit sessions include visit count one matching room name`() {

    // Given
    val dateTime = LocalDate.parse("2021-01-08").atTime(9, 0)
    val startTime = dateTime.toLocalTime()
    val endTime = dateTime.plusHours(1)

    val sessionTemplate = sessionTemplate(
      startDate = dateTime.toLocalDate(),
      expiryDate = dateTime.toLocalDate(),
      startTime = startTime,
      endTime = endTime.toLocalTime(),
      dayOfWeek = DayOfWeek.FRIDAY
    )

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit1 = Visit(
      prisonerId = "AF12345G",
      prisonId = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime,
      visitEnd = endTime,
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    val visit2 = visit1.copy(visitRoom = sessionTemplate.visitRoom + "Anythingwilldo")

    visitRepository.saveAndFlush(visit1)
    visitRepository.saveAndFlush(visit2)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].openVisitBookedCount").isEqualTo(1)
      .jsonPath("$[0].closedVisitBookedCount").isEqualTo(0)
  }

  @Test
  fun `visit sessions include reserved and booked closed visit count`() {

    // Given
    val dateTime = LocalDate.parse("2021-01-08").atTime(9, 0)
    val startTime = dateTime.toLocalTime()
    val endTime = dateTime.plusHours(1)

    val sessionTemplate = sessionTemplate(
      startDate = dateTime.toLocalDate(),
      expiryDate = dateTime.toLocalDate(),
      startTime = startTime,
      endTime = endTime.toLocalTime()
    )

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit1 = Visit(
      prisonerId = "AF12345G",
      prisonId = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime,
      visitEnd = endTime,
      visitType = SOCIAL,
      visitStatus = RESERVED,
      visitRestriction = VisitRestriction.CLOSED
    )

    val visit2 = visit1.copy(visitStatus = BOOKED, visitRestriction = VisitRestriction.CLOSED)
    val visit3 = visit1.copy(visitStatus = CANCELLED, visitRestriction = VisitRestriction.CLOSED)

    visitRepository.saveAndFlush(visit1)
    visitRepository.saveAndFlush(visit2)
    visitRepository.saveAndFlush(visit3)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].openVisitBookedCount").isEqualTo(0)
      .jsonPath("$[0].closedVisitBookedCount").isEqualTo(2)
  }

  @Test
  fun `visit sessions visit count includes only visits within session period`() {

    // Given
    val dateTime = LocalDate.parse("2021-01-08").atTime(9, 0)
    val startTime = dateTime.toLocalTime()
    val endTime = dateTime.plusHours(1)

    val sessionTemplate = sessionTemplate(
      startDate = dateTime.toLocalDate(),
      expiryDate = dateTime.toLocalDate(),
      startTime = startTime,
      endTime = endTime.toLocalTime()
    )

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit1 = Visit(
      prisonerId = "AF12345G",
      prisonId = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime.minusHours(1),
      visitEnd = endTime,
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    val visit2 = visit1.copy(visitStart = dateTime, visitEnd = dateTime.plusMinutes(30))
    val visit3 = visit1.copy(visitStart = dateTime.plusMinutes(30), visitEnd = dateTime.plusHours(1))
    val visit4 = visit1.copy(visitStart = dateTime.plusHours(1), visitEnd = dateTime.plusHours(2))

    visitRepository.saveAndFlush(visit1)
    visitRepository.saveAndFlush(visit2)
    visitRepository.saveAndFlush(visit3)
    visitRepository.saveAndFlush(visit4)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].openVisitBookedCount").isEqualTo(2)
      .jsonPath("$[0].closedVisitBookedCount").isEqualTo(0)
  }

  @Test
  fun `visit sessions are returned for a prisoner without any non-associations`() {
    // Given
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val startDate = LocalDate.parse("2021-01-08")

    val sessionTemplate = sessionTemplate(startDate = startDate)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerId)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(4)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association without a booking`() {

    // Given
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val associationId = "B1234BB"
    val startDate = LocalDate.parse("2021-01-08")
    val sessionTemplate = sessionTemplate(startDate = startDate)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationId,
      startDate.toString()
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(4)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a future non-association with a booking`() {

    // Given
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val associationId = "B1234BB"
    val startDate = LocalDate.parse("2021-01-08")
    val sessionTemplate = sessionTemplate(startDate = startDate)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit = Visit(
      prisonerId = prisonerId,
      prisonId = prisonId,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = startDate.atTime(9, 0),
      visitEnd = startDate.atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    visitRepository.saveAndFlush(visit)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationId,
      startDate.plusMonths(6).toString()
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(4)
  }

  @Test
  fun `visit sessions are returned for a prisoner with an expired non-association with a booking`() {
    // Given
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val associationId = "B1234BB"
    val startDate = LocalDate.parse("2021-01-08")
    val sessionTemplate = sessionTemplate(startDate = startDate)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit = Visit(
      prisonerId = prisonerId,
      prisonId = prisonId,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = startDate.atTime(9, 0),
      visitEnd = startDate.atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    visitRepository.saveAndFlush(visit)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationId,
      startDate.minusMonths(6).toString(),
      startDate.minusMonths(1).toString()
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(4)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking`() {

    // Given
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val associationPrisonerId = "B1234BB"
    val startDate = LocalDate.parse("2021-01-08")
    val sessionTemplate = sessionTemplate(startDate = startDate)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit = Visit(
      prisonerId = associationPrisonerId,
      prisonId = prisonId,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = startDate.atTime(9, 0),
      visitEnd = startDate.atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    visitRepository.saveAndFlush(visit)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationPrisonerId,
      startDate.minusMonths(6).toString(),
      startDate.plusMonths(1).toString()
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(3)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking CANCELLED`() {

    // Given
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val associationPrisonerId = "B1234BB"
    val startDate = LocalDate.parse("2021-01-08")

    val sessionTemplate = sessionTemplate(startDate = startDate)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit = Visit(
      prisonerId = associationPrisonerId,
      prisonId = prisonId,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = startDate.atTime(9, 0),
      visitEnd = startDate.atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = CANCELLED,
      visitRestriction = OPEN
    )

    visitRepository.saveAndFlush(visit)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationPrisonerId,
      startDate.minusMonths(6).toString(),
      startDate.plusMonths(1).toString()
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(4)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking in the past`() {
    // Given
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val associationPrisonerId = "B1234BB"
    val startDate = LocalDate.parse("2021-01-08")
    val sessionTemplate = sessionTemplate(startDate = startDate)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit = Visit(
      prisonerId = associationPrisonerId,
      prisonId = prisonId,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = startDate.minusMonths(6).atTime(9, 0),
      visitEnd = startDate.minusMonths(6).atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    visitRepository.saveAndFlush(visit)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationPrisonerId,
      startDate.minusMonths(6).toString(),
      startDate.plusMonths(1).toString()
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(4)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking in the future`() {
    // Given
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val associationPrisonerId = "B1234BB"
    val startDate = LocalDate.parse("2021-01-08")
    val sessionTemplate = sessionTemplate(startDate = startDate)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit = Visit(
      prisonerId = associationPrisonerId,
      prisonId = prisonId,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = startDate.plusMonths(6).atTime(9, 0),
      visitEnd = startDate.plusMonths(6).atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    visitRepository.saveAndFlush(visit)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationPrisonerId,
      startDate.minusYears(1).toString(),
      startDate.plusYears(1).toString()
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(4)
  }
}
