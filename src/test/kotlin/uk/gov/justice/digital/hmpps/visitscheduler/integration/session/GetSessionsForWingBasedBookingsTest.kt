package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Get /visit-sessions for prisons without wing based bookings")
class GetSessionsForWingBasedBookingsTest(@Autowired private val objectMapper: ObjectMapper) : IntegrationTestBase() {

  @Autowired
  private lateinit var sessionTemplateRepository: SessionTemplateRepository

  private val nextAllowedDay = getNextAllowedDay()

  private lateinit var sessionTemplateForAllWings: SessionTemplate
  private lateinit var sessionTemplateForABandEWings: SessionTemplate
  private lateinit var sessionTemplateForBWingOnly: SessionTemplate
  private lateinit var expiredSessionTemplateForCWingOnly: SessionTemplate

  private val prisonId = "BLI"

  @Autowired
  private lateinit var visitRepository: VisitRepository

  @AfterEach
  internal fun deleteAllVisitSessions() = visitEntityHelper.deleteAll()

  @AfterEach
  internal fun deleteAllSessionTemplates() = sessionTemplateEntityHelper.deleteAll()

  @BeforeEach
  internal fun createAllSessionTemplates() {
    // this session template is available for all wings as no wings are added for this session
    sessionTemplateForAllWings = sessionTemplateEntityHelper.create(
      prisonId = prisonId,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      visitRoom = "Session for all wings",
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek
    )

    // this session template is available for A B and E wings
    sessionTemplateForABandEWings = sessionTemplateEntityHelper.create(
      prisonId = prisonId,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      visitRoom = "Session for A,B and E wing",
      startTime = LocalTime.parse("10:01"),
      endTime = LocalTime.parse("11:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek
    )
    sessionTemplateEntityHelper.createWings(sessionTemplateForABandEWings, setOf("A", "B", "E"))
    sessionTemplateRepository.save(sessionTemplateForABandEWings)

    // this session template is only available only for B wing
    sessionTemplateForBWingOnly = sessionTemplateEntityHelper.create(
      prisonId = prisonId,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      visitRoom = "Session for B wing only",
      startTime = LocalTime.parse("11:01"),
      endTime = LocalTime.parse("12:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek
    )
    sessionTemplateEntityHelper.createWings(sessionTemplateForBWingOnly, setOf("B"))
    sessionTemplateRepository.save(sessionTemplateForBWingOnly)

    // this session template is only available for C wing however it has expired so should not be returned
    expiredSessionTemplateForCWingOnly = sessionTemplateEntityHelper.create(
      prisonId = prisonId,
      validFromDate = LocalDate.now().minusDays(31),
      validToDate = LocalDate.now().minusDays(1),
      visitRoom = "Session for B wing only",
      startTime = LocalTime.parse("13:00"),
      endTime = LocalTime.parse("14:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek
    )
    sessionTemplateEntityHelper.createWings(expiredSessionTemplateForCWingOnly, setOf("C"))
    sessionTemplateRepository.save(expiredSessionTemplateForCWingOnly)
  }

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  @Test
  fun `both wing and non wing based sessions are returned for a prison`() {
    // When
    val responseSpec = callGetSessions(prisonId)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(3)

    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllWings)
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForABandEWings)
    assertSession(visitSessionResults[2], nextAllowedDay, sessionTemplateForBWingOnly)
  }

  @Test
  fun `sessions are not returned for a prison when no sessions exist for that prison`() {
    // When
    val prisonId = "XYZ"
    val responseSpec = callGetSessions(prisonId)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(0)
  }

  @Test
  fun `only sessions across wings are returned for a prisoner in wing without wing based sessions`() {
    val prisonerId = "A1234AA"
    val prisonId = "BLI"
    val prisonWing = "X"

    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerId)

    // the prisoner exists in X wing
    prisonApiMockServer.stubGetPrisonerDetails(
      prisonerId, "$prisonId-$prisonWing-1-007"
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val visitSessionResults = getResults(responseSpec.expectBody())

    // since X wing has no wing specific session templates only sessions common to all wings needs to be returned
    Assertions.assertThat(visitSessionResults.size).isEqualTo(1)

    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllWings)
  }

  @Test
  fun `sessions across wings and wing specific session are returned for a prisoner in wing with wing based sessions`() {
    val prisonerId = "A1234AA"
    val prisonId = "BLI"
    val prisonWing = "B"

    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerId)

    // the prisoner exists in B wing
    prisonApiMockServer.stubGetPrisonerDetails(
      prisonerId, "$prisonId-$prisonWing-1-007"
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val visitSessionResults = getResults(responseSpec.expectBody())

    // since B wing has wing specific session templates common and wing specific sessions needs to be returned
    Assertions.assertThat(visitSessionResults.size).isEqualTo(3)

    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllWings)
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForABandEWings)
    assertSession(visitSessionResults[2], nextAllowedDay, sessionTemplateForBWingOnly)
  }

  @Test
  fun `expired sessions are not returned for a prisoner in wing with wing based sessions`() {
    val prisonerId = "A1234AA"
    val prisonId = "BLI"
    val prisonWing = "C"

    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerId)

    // the prisoner exists in C wing
    prisonApiMockServer.stubGetPrisonerDetails(
      prisonerId, "$prisonId-$prisonWing-1-007"
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val visitSessionResults = getResults(responseSpec.expectBody())

    // since C wing has wing specific session templates that are expired only common sessions needs to be returned
    Assertions.assertThat(visitSessionResults.size).isEqualTo(1)
    Assertions.assertThat(visitSessionResults[0].visitRoomName).isNotEqualTo(expiredSessionTemplateForCWingOnly.visitRoom)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllWings)
  }

  @Test
  fun `sessions are not returned when a non association prisoner has a visit at same time`() {
    // Given
    val prisonId = "BLI"
    val prisonerId = "A1234AA"
    val prisonWing = "X"

    val nonAssociationPrisonerId = "B1234BB"
    val validFromDate = sessionTemplateForAllWings.validFromDate

    // the prisoner exists in X wing
    prisonApiMockServer.stubGetPrisonerDetails(
      prisonerId, "$prisonId-$prisonWing-1-007"
    )

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      nonAssociationPrisonerId,
      validFromDate.minusMonths(6).toString(),
      validFromDate.plusMonths(1).toString()
    )

    // non association prisoner has a visit at the same time
    val visit = Visit(
      prisonerId = nonAssociationPrisonerId,
      prisonId = prisonId,
      visitRoom = sessionTemplateForAllWings.visitRoom,
      visitStart = validFromDate.atTime(sessionTemplateForAllWings.startTime),
      visitEnd = validFromDate.atTime(sessionTemplateForAllWings.endTime),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    visitRepository.saveAndFlush(visit)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    // no session should be returned as non association has a visit at the same time
    assertResponseLength(responseSpec, 0)
  }

  /**
   * this test is disabled as we do not allow non association for the whole day.
   * we will need to enable this test if non association is only by visit times.
   */
  @Disabled("disabled as currently non associations are not allowed for whole day.")
  @Test
  fun `sessions are returned when a non association prisoner has a visit at a different time`() {
    // Given
    val prisonId = "BLI"
    val prisonerId = "A1234AA"
    val prisonWing = "A"

    val nonAssociationPrisonerId = "B1234BB"
    val validFromDate = sessionTemplateForAllWings.validFromDate

    // the prisoner exists in A wing
    prisonApiMockServer.stubGetPrisonerDetails(
      prisonerId, "$prisonId-$prisonWing-1-007"
    )

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      nonAssociationPrisonerId,
      validFromDate.minusMonths(6).toString(),
      validFromDate.plusMonths(1).toString()
    )

    // non association prisoner has a visit at the time that is different to other 2 session templates
    val visit = Visit(
      prisonerId = nonAssociationPrisonerId,
      prisonId = prisonId,
      visitRoom = sessionTemplateForBWingOnly.visitRoom,
      visitStart = validFromDate.atTime(sessionTemplateForBWingOnly.startTime),
      visitEnd = validFromDate.atTime(sessionTemplateForBWingOnly.endTime),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    visitRepository.saveAndFlush(visit)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val visitSessionResults = getResults(responseSpec.expectBody())

    // since the non association's session is at a different time - 2 visit sessions should be returned
    Assertions.assertThat(visitSessionResults.size).isEqualTo(2)

    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllWings)
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForABandEWings)
  }

  @Test
  fun `sessions are returned when a non association prisoner has a visit in a different prison`() {
    // Given
    val prisonId = "BLI"
    val prisonerId = "A1234AA"
    val prisonWing = "B"

    val nonAssociationPrisonerId = "B1234BB"
    val nonAssociationPrisonId = "HEI"
    val validFromDate = sessionTemplateForAllWings.validFromDate

    // the prisoner exists in A wing
    prisonApiMockServer.stubGetPrisonerDetails(
      prisonerId, "$prisonId-$prisonWing-1-007"
    )

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      nonAssociationPrisonerId,
      validFromDate.minusMonths(6).toString(),
      validFromDate.plusMonths(1).toString()
    )

    // non association prisoner has a visit at the time but in a different prison
    val visit = Visit(
      prisonerId = nonAssociationPrisonerId,
      prisonId = nonAssociationPrisonId,
      visitRoom = sessionTemplateForBWingOnly.visitRoom,
      visitStart = validFromDate.atTime(sessionTemplateForBWingOnly.startTime),
      visitEnd = validFromDate.atTime(sessionTemplateForBWingOnly.endTime),
      visitType = SOCIAL,
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    visitRepository.saveAndFlush(visit)

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val visitSessionResults = getResults(responseSpec.expectBody())

    // since the non association's session is at a different prison - all 3 visit sessions for wing B should be returned
    Assertions.assertThat(visitSessionResults.size).isEqualTo(3)

    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllWings)
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForABandEWings)
    assertSession(visitSessionResults[2], nextAllowedDay, sessionTemplateForBWingOnly)
  }

  @Test
  fun `sessions for a different prison are not returned`() {
    val prisonerId = "A1234AA"
    val prisonId = "BLI"
    val prisonWing = "B"

    val sessionTemplateForDifferentPrison = sessionTemplateEntityHelper.create(
      prisonId = "XYZ",
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      visitRoom = "Session for XYZ prison",
      startTime = LocalTime.parse("11:30"),
      endTime = LocalTime.parse("12:30"),
      dayOfWeek = nextAllowedDay.dayOfWeek
    )
    sessionTemplateRepository.save(sessionTemplateForDifferentPrison)

    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerId)

    // the prisoner exists in C wing
    prisonApiMockServer.stubGetPrisonerDetails(
      prisonerId, "$prisonId-$prisonWing-1-007"
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val visitSessionResults = getResults(responseSpec.expectBody())

    Assertions.assertThat(visitSessionResults.size).isEqualTo(3)

    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllWings)
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForABandEWings)
    assertSession(visitSessionResults[2], nextAllowedDay, sessionTemplateForBWingOnly)
  }

  @Test
  fun `all sessions for a non wing based prison are returned`() {
    val prisonerId = "A1234AA"
    val prisonId = "XYZ"
    val prisonWing = "A"

    val sessionTemplate1ForDifferentPrison = sessionTemplateEntityHelper.create(
      prisonId = prisonId,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      visitRoom = "Session 1 for XYZ prison",
      startTime = LocalTime.parse("11:30"),
      endTime = LocalTime.parse("12:30"),
      dayOfWeek = nextAllowedDay.dayOfWeek
    )
    sessionTemplateRepository.save(sessionTemplate1ForDifferentPrison)

    val sessionTemplate2ForDifferentPrison = sessionTemplateEntityHelper.create(
      prisonId = prisonId,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      visitRoom = "Session 2 for XYZ prison",
      startTime = LocalTime.parse("14:30"),
      endTime = LocalTime.parse("15:30"),
      dayOfWeek = nextAllowedDay.dayOfWeek
    )
    sessionTemplateRepository.save(sessionTemplate2ForDifferentPrison)

    val sessionTemplate3ForDifferentPrison = sessionTemplateEntityHelper.create(
      prisonId = prisonId,
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      visitRoom = "Session 3 for XYZ prison",
      startTime = LocalTime.parse("17:30"),
      endTime = LocalTime.parse("18:30"),
      dayOfWeek = nextAllowedDay.dayOfWeek
    )
    sessionTemplateEntityHelper.createWings(sessionTemplate3ForDifferentPrison, "A")
    sessionTemplateRepository.save(sessionTemplate3ForDifferentPrison)
    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerId)

    prisonApiMockServer.stubGetPrisonerDetails(
      prisonerId, "$prisonId-$prisonWing-1-007"
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val visitSessionResults = getResults(responseSpec.expectBody())

    Assertions.assertThat(visitSessionResults.size).isEqualTo(2)

    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate1ForDifferentPrison)
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplate2ForDifferentPrison)
  }

  @Test
  fun `only sessions across wings are returned when internal location is incorrect format`() {
    val prisonerId = "A1234AA"
    val prisonId = "BLI"

    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerId)

    // the prisoner's internal location is null on Prison API
    prisonApiMockServer.stubGetPrisonerDetails(
      prisonerId, "locationnotdelimited"
    )

    // When
    val responseSpec = webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val visitSessionResults = getResults(responseSpec.expectBody())

    // since internal location is null only sessions common to all wings needs to be returned
    Assertions.assertThat(visitSessionResults.size).isEqualTo(1)

    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllWings)
  }

  private fun callGetSessions(prisonId: String = "MDI"): ResponseSpec {
    return webTestClient.get().uri("/visit-sessions?prisonId=$prisonId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()
  }

  private fun getNextAllowedDay(): LocalDate {
    // The two days is based on the default SessionService.policyNoticeDaysMin
    return LocalDate.now().plusDays(2)
  }

  private fun assertSession(
    visitSessionResult: VisitSessionDto,
    testDate: LocalDate,
    expectedSessionTemplate: SessionTemplate
  ) {
    Assertions.assertThat(visitSessionResult.startTimestamp)
      .isEqualTo(testDate.atTime(expectedSessionTemplate.startTime))
    Assertions.assertThat(visitSessionResult.endTimestamp).isEqualTo(testDate.atTime(expectedSessionTemplate.endTime))
    Assertions.assertThat(visitSessionResult.startTimestamp.dayOfWeek).isEqualTo(expectedSessionTemplate.dayOfWeek)
    Assertions.assertThat(visitSessionResult.endTimestamp.dayOfWeek).isEqualTo(expectedSessionTemplate.dayOfWeek)
    Assertions.assertThat(visitSessionResult.visitRoomName).isEqualTo(expectedSessionTemplate.visitRoom)
  }

  private fun assertResponseLength(responseSpec: ResponseSpec, length: Int) {
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(length)
  }

  private fun getResults(returnResult: BodyContentSpec): Array<VisitSessionDto> {
    return objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)
  }
}
