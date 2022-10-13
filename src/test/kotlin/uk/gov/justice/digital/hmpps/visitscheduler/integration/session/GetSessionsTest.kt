package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CHANGING
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@DisplayName("Get /visit-sessions")
class GetSessionsTest(@Autowired private val objectMapper: ObjectMapper) : IntegrationTestBase() {

  @Autowired
  private lateinit var sessionTemplateRepository: SessionTemplateRepository

  @Autowired
  private lateinit var visitRepository: VisitRepository

  @AfterEach
  internal fun deleteAllSessionTemplates() = sessionTemplateEntityHelper.deleteAll()

  @AfterEach
  internal fun deleteAllVisitSessions() = visitEntityHelper.deleteAll()

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  @Test
  fun `visit sessions are returned for a prison for a single schedule`() {

    // Given
    val dateTime = LocalDate.now().plusDays(2).with(TemporalAdjusters.next(DayOfWeek.FRIDAY))

    val sessionTemplate = sessionTemplate(
      validFromDate = dateTime,
      validToDate = dateTime,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00")
    )

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].startTimestamp").isEqualTo(dateTime.atTime(sessionTemplate.startTime).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
      .jsonPath("$[0].endTimestamp").isEqualTo(dateTime.atTime(sessionTemplate.endTime).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
  }

  @Test
  fun `visit sessions are returned for a prison for a weekly schedule`() {
    // Given
    val sessionTemplate1 = sessionTemplate(
      validFromDate = LocalDate.now().plusWeeks(1),
      dayOfWeek = DayOfWeek.SUNDAY,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00")
    )

    val sessionTemplate2 = sessionTemplate(
      validFromDate = LocalDate.now().plusWeeks(1),
      dayOfWeek = DayOfWeek.MONDAY,
      startTime = LocalTime.parse("10:30"),
      endTime = LocalTime.parse("11:30")
    )

    sessionTemplateRepository.saveAndFlush(sessionTemplate1)
    sessionTemplateRepository.saveAndFlush(sessionTemplate2)

    // 1st valid session after session template 1 starts
    val firstValidSessionAfterTemplate1Start =
      sessionTemplate1.validFromDate.with(TemporalAdjusters.next(sessionTemplate1.dayOfWeek))
    // 1st valid session after session template 2 starts
    val firstValidSessionAfterTemplate2Start =
      sessionTemplate2.validFromDate.with(TemporalAdjusters.next(sessionTemplate2.dayOfWeek))

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()

    val visitSessionDto = objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)

    Assertions.assertThat(visitSessionDto.size).isEqualTo(6)
    Assertions.assertThat(visitSessionDto[0].startTimestamp).isEqualTo(
      firstValidSessionAfterTemplate1Start
        .atTime(sessionTemplate1.startTime).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    )
    Assertions.assertThat(visitSessionDto[0].endTimestamp).isEqualTo(
      firstValidSessionAfterTemplate1Start
        .atTime(sessionTemplate1.endTime).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    )
    Assertions.assertThat(visitSessionDto[0].startTimestamp.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
    Assertions.assertThat(visitSessionDto[0].endTimestamp.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)

    Assertions.assertThat(visitSessionDto[1].startTimestamp).isEqualTo(
      firstValidSessionAfterTemplate2Start
        .atTime(sessionTemplate2.startTime).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    )
    Assertions.assertThat(visitSessionDto[1].endTimestamp).isEqualTo(
      firstValidSessionAfterTemplate2Start
        .atTime(sessionTemplate2.endTime).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    )
    Assertions.assertThat(visitSessionDto[1].startTimestamp.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
    Assertions.assertThat(visitSessionDto[1].endTimestamp.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)

    Assertions.assertThat(visitSessionDto[2].startTimestamp).isEqualTo(
      firstValidSessionAfterTemplate1Start.plusWeeks(1)
        .atTime(sessionTemplate1.startTime).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    )
    Assertions.assertThat(visitSessionDto[2].endTimestamp).isEqualTo(
      firstValidSessionAfterTemplate1Start.plusWeeks(1)
        .atTime(sessionTemplate1.endTime).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    )
    Assertions.assertThat(visitSessionDto[2].startTimestamp.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
    Assertions.assertThat(visitSessionDto[2].endTimestamp.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)

    Assertions.assertThat(visitSessionDto[3].startTimestamp).isEqualTo(
      firstValidSessionAfterTemplate2Start.plusWeeks(1)
        .atTime(sessionTemplate2.startTime).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    )
    Assertions.assertThat(visitSessionDto[3].endTimestamp).isEqualTo(
      firstValidSessionAfterTemplate2Start.plusWeeks(1)
        .atTime(sessionTemplate2.endTime).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    )
    Assertions.assertThat(visitSessionDto[3].startTimestamp.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
    Assertions.assertThat(visitSessionDto[3].endTimestamp.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
  }

  @Test
  fun `visit sessions are returned for a prison when day of week is Friday and schedule starts and ends on the same Friday`() {
    // Given
    val policyNoticeDaysMin = 0
    val policyNoticeDaysMax = 10

    val sessionTemplate = sessionTemplate(
      validFromDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY)),
      validToDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY)),
      dayOfWeek = DayOfWeek.FRIDAY,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00")
    )

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI&min=$policyNoticeDaysMin&max=$policyNoticeDaysMax")
      .headers(setAuthorisation(roles = requiredRole))
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
      validFromDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY)),
      validToDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY)),
      dayOfWeek = DayOfWeek.SATURDAY,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00")
    )

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI&min=$policyNoticeDaysMin&max=$policyNoticeDaysMax")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(0)
  }

  @Test
  fun `visit sessions are not returned when policy notice min days is greater than max without valid to date`() {
    // Given
    val sessionTemplate = sessionTemplate(validFromDate = LocalDate.now().plusDays(2).with(TemporalAdjusters.next(DayOfWeek.FRIDAY)), validToDate = null)
    val policyNoticeDaysMin = 10
    val policyNoticeDaysMax = 1

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI&min=$policyNoticeDaysMin&max=$policyNoticeDaysMax")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(0)
  }

  @Test
  fun `visit sessions are not returned when start date is after policy notice min and max days`() {
    // Given
    val sessionTemplate = sessionTemplate(validFromDate = LocalDate.now().plusDays(2), validToDate = null)
    val policyNoticeDaysMin = 0
    val policyNoticeDaysMax = 1

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI&min=$policyNoticeDaysMin&max=$policyNoticeDaysMax")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(0)
  }

  @Test
  fun `visit sessions are not returned for when policy notice min days is greater than max with valid to date`() {
    // Given
    val sessionTemplate = sessionTemplate(validFromDate = LocalDate.now().plusDays(2).with(TemporalAdjusters.next(DayOfWeek.FRIDAY)), validToDate = LocalDate.now().plusMonths(3))
    val policyNoticeDaysMin = 10
    val policyNoticeDaysMax = 1

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI&min=$policyNoticeDaysMin&max=$policyNoticeDaysMax")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(0)
  }

  @Test
  fun `visit sessions that are no longer valid are not returned`() {
    // Given
    val sessionTemplate = sessionTemplate(
      validFromDate = LocalDate.now().minusDays(1),
      validToDate = LocalDate.now().minusDays(1)
    )

    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(0)
  }

  @Test
  fun `sessions that start after the max policy notice days after current date are not returned`() {
    // Given
    val sessionTemplate = sessionTemplate(
      validFromDate = LocalDate.now().plusDays(31),
      validToDate = LocalDate.now().plusDays(120)
    )
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(0)
  }

  @Test
  fun `visit sessions include reserved and booked open visit count`() {

    // Given
    val dateTime = LocalDate.now().plusDays(2).with(TemporalAdjusters.next(DayOfWeek.FRIDAY)).atTime(9, 0)
    val startTime = dateTime.toLocalTime()
    val endTime = dateTime.plusHours(1)

    val sessionTemplate = sessionTemplate(
      validFromDate = dateTime.toLocalDate(),
      validToDate = dateTime.toLocalDate(),
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
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].openVisitBookedCount").isEqualTo(2)
      .jsonPath("$[0].closedVisitBookedCount").isEqualTo(0)
  }

  @Test
  fun `visit sessions exclude visits with changing status in visit count`() {

    // Given
    val dateTime = LocalDate.now().plusDays(2).with(TemporalAdjusters.next(DayOfWeek.FRIDAY)).atTime(9, 0)
    val startTime = dateTime.toLocalTime()
    val endTime = dateTime.plusHours(1)

    val sessionTemplate = sessionTemplate(
      validFromDate = dateTime.toLocalDate(),
      validToDate = dateTime.toLocalDate(),
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
      visitStatus = CHANGING,
      visitRestriction = OPEN
    )

    val visit2 = Visit(
      prisonerId = "AF12345G",
      prisonId = "MDI",
      visitRoom = sessionTemplate.visitRoom,
      visitStart = dateTime.with(TemporalAdjusters.next(DayOfWeek.FRIDAY)),
      visitEnd = endTime,
      visitType = SOCIAL,
      visitStatus = CHANGING,
      visitRestriction = CLOSED
    )

    visitRepository.saveAndFlush(visit1)
    visitRepository.saveAndFlush(visit2)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].openVisitBookedCount").isEqualTo(0)
      .jsonPath("$[0].closedVisitBookedCount").isEqualTo(0)
  }

  @Test
  fun `visit sessions include visit count one matching room name`() {

    // Given
    val dateTime = LocalDate.now().plusDays(2).with(TemporalAdjusters.next(DayOfWeek.FRIDAY)).atTime(9, 0)
    val startTime = dateTime.toLocalTime()
    val endTime = dateTime.plusHours(1)

    val sessionTemplate = sessionTemplate(
      validFromDate = dateTime.toLocalDate(),
      validToDate = dateTime.toLocalDate(),
      startTime = startTime,
      endTime = endTime.toLocalTime(),
      dayOfWeek = dateTime.dayOfWeek
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
      .headers(setAuthorisation(roles = requiredRole))
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
    val dateTime = LocalDate.now().plusDays(2).with(TemporalAdjusters.next(DayOfWeek.FRIDAY)).atTime(9, 0)
    val startTime = dateTime.toLocalTime()
    val endTime = dateTime.plusHours(1)

    val sessionTemplate = sessionTemplate(
      validFromDate = dateTime.toLocalDate(),
      validToDate = dateTime.toLocalDate(),
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
      visitRestriction = CLOSED
    )

    val visit2 = visit1.copy(visitStatus = BOOKED, visitRestriction = CLOSED)
    val visit3 = visit1.copy(visitStatus = CANCELLED, visitRestriction = CLOSED)

    visitRepository.saveAndFlush(visit1)
    visitRepository.saveAndFlush(visit2)
    visitRepository.saveAndFlush(visit3)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = requiredRole))
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
    val dateTime = LocalDate.now().plusDays(2).with(TemporalAdjusters.next(DayOfWeek.FRIDAY)).atTime(9, 0)
    val startTime = dateTime.toLocalTime()
    val endTime = dateTime.plusHours(1)

    val sessionTemplate = sessionTemplate(
      validFromDate = dateTime.toLocalDate(),
      validToDate = dateTime.toLocalDate(),
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
      .headers(setAuthorisation(roles = requiredRole))
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
    val validFromDate = LocalDate.now().plusDays(2)

    val sessionTemplate = sessionTemplate(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerId)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
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
    val validFromDate = LocalDate.now().plusDays(2)
    val sessionTemplate = sessionTemplate(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationId,
      validFromDate.toString()
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
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
    val validFromDate = LocalDate.now().plusDays(2)
    val sessionTemplate = sessionTemplate(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit = Visit(
      prisonerId = prisonerId,
      prisonId = prisonId,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = validFromDate.atTime(9, 0),
      visitEnd = validFromDate.atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    visitRepository.saveAndFlush(visit)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationId,
      validFromDate.plusMonths(6).toString()
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
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
    val validFromDate = LocalDate.now().plusDays(2)
    val sessionTemplate = sessionTemplate(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit = Visit(
      prisonerId = prisonerId,
      prisonId = prisonId,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = validFromDate.atTime(9, 0),
      visitEnd = validFromDate.atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    visitRepository.saveAndFlush(visit)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationId,
      validFromDate.minusMonths(6).toString(),
      validFromDate.minusMonths(1).toString()
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
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
    val validFromDate = LocalDate.now().plusDays(2)
    val sessionTemplate = sessionTemplate(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit = Visit(
      prisonerId = associationPrisonerId,
      prisonId = prisonId,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = validFromDate.atTime(9, 0),
      visitEnd = validFromDate.atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    visitRepository.saveAndFlush(visit)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationPrisonerId,
      validFromDate.minusMonths(6).toString(),
      validFromDate.plusMonths(1).toString()
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
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
    val validFromDate = LocalDate.now().plusDays(2)

    val sessionTemplate = sessionTemplate(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit = Visit(
      prisonerId = associationPrisonerId,
      prisonId = prisonId,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = validFromDate.atTime(9, 0),
      visitEnd = validFromDate.atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = CANCELLED,
      visitRestriction = OPEN
    )

    visitRepository.saveAndFlush(visit)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationPrisonerId,
      validFromDate.minusMonths(6).toString(),
      validFromDate.plusMonths(1).toString()
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
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
    val validFromDate = LocalDate.now().plusDays(2)
    val sessionTemplate = sessionTemplate(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit = Visit(
      prisonerId = associationPrisonerId,
      prisonId = prisonId,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = validFromDate.minusMonths(6).atTime(9, 0),
      visitEnd = validFromDate.minusMonths(6).atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    visitRepository.saveAndFlush(visit)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationPrisonerId,
      validFromDate.minusMonths(6).toString(),
      validFromDate.plusMonths(1).toString()
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
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
    val validFromDate = LocalDate.now().plusDays(2)
    val sessionTemplate = sessionTemplate(validFromDate = validFromDate, dayOfWeek = validFromDate.dayOfWeek)
    sessionTemplateRepository.saveAndFlush(sessionTemplate)

    val visit = Visit(
      prisonerId = associationPrisonerId,
      prisonId = prisonId,
      visitRoom = sessionTemplate.visitRoom,
      visitStart = validFromDate.plusMonths(6).atTime(9, 0),
      visitEnd = validFromDate.plusMonths(6).atTime(9, 30),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    visitRepository.saveAndFlush(visit)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationPrisonerId,
      validFromDate.minusYears(1).toString(),
      validFromDate.plusYears(1).toString()
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(4)
  }
}
