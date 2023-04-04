package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.apache.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatusCode
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.integration.mock.dto.PrisonerCellContentNativeDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.mock.dto.PrisonerCellHistoryNativeDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.TransitionalLocationTypes
import uk.gov.justice.digital.hmpps.visitscheduler.model.TransitionalLocationTypes.COURT
import uk.gov.justice.digital.hmpps.visitscheduler.model.TransitionalLocationTypes.CSWAP
import uk.gov.justice.digital.hmpps.visitscheduler.model.TransitionalLocationTypes.ECL
import uk.gov.justice.digital.hmpps.visitscheduler.model.TransitionalLocationTypes.RECP
import uk.gov.justice.digital.hmpps.visitscheduler.model.TransitionalLocationTypes.TAP
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Get /visit-sessions")
class GetSessionsWithLocationsWhenPrisonerIsTransitionalTest : IntegrationTestBase() {
  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")
  private val prison: Prison = Prison(code = "MDI", active = true)

  private val nextAllowedDay = getNextAllowedDay()

  @BeforeEach
  internal fun createAllSessionTemplates() {
  }

  @Test
  fun `visit sessions are returned for prisoner in COURT with last location`() {
    // Given
    val prisonCode = "CR1"
    val prisonerId = "A0000001"

    stubLastCellLocation(prisonCode, listOf(COURT), 1)
    setUpStub(prisonerId, prisonCode, COURT)
    val sessionTemplate = setupSessionTemplate(prisonCode)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prisonCode, prisonerId)

    // Then
    assertReturnedResult(responseSpec, sessionTemplate)
  }

  @Test
  fun `visit sessions are returned for prisoner in TAP with last location`() {
    // Given
    val prisonCode = "CR1"
    val prisonerId = "A0000001"

    stubLastCellLocation(prisonCode, listOf(TAP), 1)
    setUpStub(prisonerId, prisonCode, TAP)
    val sessionTemplate = setupSessionTemplate(prisonCode)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prisonCode, prisonerId)

    // Then
    assertReturnedResult(responseSpec, sessionTemplate)
  }

  @Test
  fun `visit sessions are returned for prisoner in RECP with last location`() {
    // Given
    val prisonCode = "CR1"
    val prisonerId = "A0000001"

    stubLastCellLocation(prisonCode, listOf(RECP), 1)
    setUpStub(prisonerId, prisonCode, RECP)
    val sessionTemplate = setupSessionTemplate(prisonCode)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prisonCode, prisonerId)

    // Then
    assertReturnedResult(responseSpec, sessionTemplate)
  }

  @Test
  fun `visit sessions are returned for prisoner in ECL with last location`() {
    // Given
    val prisonCode = "CR1"
    val prisonerId = "A0000001"

    stubLastCellLocation(prisonCode, listOf(ECL), 1)
    setUpStub(prisonerId, prisonCode, ECL)
    val sessionTemplate = setupSessionTemplate(prisonCode)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prisonCode, prisonerId)

    // Then
    assertReturnedResult(responseSpec, sessionTemplate)
  }

  @Test
  fun `visit sessions are returned for prisoner in CSWAP with last location`() {
    // Given
    val prisonCode = "CR1"
    val prisonerId = "A0000001"

    stubLastCellLocation(prisonCode, listOf(CSWAP), 1)
    setUpStub(prisonerId, prisonCode, CSWAP)
    val sessionTemplate = setupSessionTemplate(prisonCode)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prisonCode, prisonerId)

    // Then
    assertReturnedResult(responseSpec, sessionTemplate)
  }

  @Test
  fun `visit sessions are returned for prisoner when more than one transitional location is given`() {
    // Given
    val prisonCode = "CR1"
    val prisonerId = "A0000001"
    val transitionalLocations = TransitionalLocationTypes.values().asList()

    stubLastCellLocation(prisonCode, transitionalLocations, 1)
    setUpStub(prisonerId, prisonCode, transitionalLocations[0])
    val sessionTemplate = setupSessionTemplate(prisonCode)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prisonCode, prisonerId)

    // Then
    assertReturnedResult(responseSpec, sessionTemplate)
  }

  @Test
  fun `visit sessions are not returned for prisoner when historic locations are in different prisons`() {
    // Given
    val prisonCode = "CR1"
    val prisonerId = "A0000001"
    val transitionalLocations = TransitionalLocationTypes.values().asList()

    stubLastCellLocation("BAD", transitionalLocations, 1)
    setUpStub(prisonerId, prisonCode, transitionalLocations[0])
    setupSessionTemplate(prisonCode)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prisonCode, prisonerId)

    // Then
    assertNoResult(responseSpec)
  }

  @Test
  fun `visit sessions are not returned for prisoner when we cant find the prisoner historic location`() {
    // Given
    val prisonCode = "CR1"
    val prisonerId = "A0000001"
    val transitionalLocations = TransitionalLocationTypes.values().asList()

    setUpStub(prisonerId, prisonCode, transitionalLocations[0])
    setupSessionTemplate(prisonCode)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prisonCode, prisonerId)

    // Then
    assertNoResult(responseSpec)
  }

  @Test
  fun `visit sessions are not returned for prisoner when we cant find the prisoner location`() {
    // Given
    val prisonCode = "CR1"
    val prisonerId = "A0000001"
    val transitionalLocations = TransitionalLocationTypes.values().asList()

    stubLastCellLocation(prisonCode, transitionalLocations, 1)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prisonCode)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId = "A0000001", prisonCode = prison.code)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prisonCode, prisonerId)

    // Then
    assertNoResult(responseSpec)
  }

  @Test
  fun `visit sessions are not returned for prisoner when we cant find the prisoner using search`() {
    // Given
    val prisonCode = "CR1"
    val prisonerId = "A0000001"
    val transitionalLocations = TransitionalLocationTypes.values().asList()

    stubLastCellLocation(prisonCode, transitionalLocations, 1)

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-${RECP.name}")
    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prisonCode)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prisonCode, prisonerId)

    // Then
    assertErrorResult(
      responseSpec,
      HttpStatusCode.valueOf(HttpStatus.SC_NOT_FOUND),
      "Prisoner not found A0000001 with offender search",
    )
  }

  @Test
  fun `visit sessions are not returned for prisoner when we cant find the prisoners details`() {
    // Given
    val prisonCode = "CR1"
    val prisonerId = "A0000001"
    val transitionalLocations = TransitionalLocationTypes.values().asList()

    stubLastCellLocation(prisonCode, transitionalLocations, 1)

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-${RECP.name}")
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId = "A0000001", prisonCode = prison.code)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prisonCode, prisonerId)

    // Then
    assertErrorResult(
      responseSpec,
      HttpStatusCode.valueOf(HttpStatus.SC_BAD_REQUEST),
      "Prisoner with ID - A0000001 cannot be found",
    )
  }

  private fun setUpStub(
    prisonerId: String,
    prisonerPrisonCode: String,
    prisonerTransitionalLocation: TransitionalLocationTypes,
  ) {
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonerPrisonCode-${prisonerTransitionalLocation.name}")
    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prisonerPrisonCode)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId = "A0000001", prisonCode = prison.code)
  }

  private fun stubLastCellLocation(
    prisonerPrisonCode: String,
    transitionalTypes: List<TransitionalLocationTypes>,
    bookingID: Int,
  ) {
    val prisonerLastLocation = "$prisonerPrisonCode-A-1-100-1"
    val history = mutableListOf<PrisonerCellContentNativeDto>()
    transitionalTypes.forEach {
      history.add(PrisonerCellContentNativeDto(bookingID, prisonerPrisonCode, "$prisonerPrisonCode-${it.name}"))
    }
    history.add(PrisonerCellContentNativeDto(bookingID, prisonerPrisonCode, prisonerLastLocation))
    val prisonerCellHistoryNativeDto = PrisonerCellHistoryNativeDto(history)
    prisonApiMockServer.stubGetCellHistory(bookingID, prisonerCellHistoryNativeDto)
  }

  private fun setupSessionTemplate(sessionPrisonCode: String): SessionTemplate {
    val allowedPermittedLocations = listOf(
      AllowedSessionLocationHierarchy("A", "1", "100", "1"),
    )
    val location =
      sessionLocationGroupHelper.create(prisonCode = sessionPrisonCode, prisonHierarchies = allowedPermittedLocations)

    return sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("13:01"),
      endTime = LocalTime.parse("14:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = sessionPrisonCode,
      visitRoom = "session available to some level 4s and level 2s",
      permittedSessionGroups = mutableListOf(location),
    )
  }

  private fun callGetSessionsByPrisonerIdAndPrison(prisonId: String, prisonerId: String): ResponseSpec {
    return webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()
  }

  private fun getNextAllowedDay(): LocalDate {
    // The two days is based on the default SessionService.policyNoticeDaysMin
    return LocalDate.now().plusDays(2)
  }

  private fun assertReturnedResult(
    responseSpec: ResponseSpec,
    sessionTemplate: SessionTemplate,
  ) {
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    assertThat(visitSessionResults.size).isEqualTo(1)
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplate)
  }

  private fun assertErrorResult(
    responseSpec: ResponseSpec,
    httpStatusCode: HttpStatusCode = HttpStatusCode.valueOf(HttpStatus.SC_BAD_REQUEST),
    errorMessage: String ? = null,
  ) {
    responseSpec.expectStatus().isEqualTo(httpStatusCode)
    errorMessage?.let {
      val errorResponse =
        objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, ErrorResponse::class.java)
      assertThat(errorResponse.developerMessage).isEqualTo(errorMessage)
    }
  }

  private fun assertNoResult(
    responseSpec: ResponseSpec,
    httpStatusCode: HttpStatusCode = HttpStatusCode.valueOf(200),
  ) {
    responseSpec.expectStatus().isEqualTo(httpStatusCode)
    val visitSessionResults = getResults(responseSpec.expectBody())
    assertThat(visitSessionResults.size).isEqualTo(0)
  }

  private fun assertSession(
    visitSessionResult: VisitSessionDto,
    testDate: LocalDate,
    expectedSessionTemplate: SessionTemplate,
  ) {
    assertThat(visitSessionResult.startTimestamp).isEqualTo(testDate.atTime(expectedSessionTemplate.startTime))
    assertThat(visitSessionResult.endTimestamp).isEqualTo(testDate.atTime(expectedSessionTemplate.endTime))
    assertThat(visitSessionResult.startTimestamp.dayOfWeek).isEqualTo(expectedSessionTemplate.dayOfWeek)
    assertThat(visitSessionResult.endTimestamp.dayOfWeek).isEqualTo(expectedSessionTemplate.dayOfWeek)
    assertThat(visitSessionResult.visitRoomName).isEqualTo(expectedSessionTemplate.visitRoom)
  }

  private fun getResults(returnResult: WebTestClient.BodyContentSpec): Array<VisitSessionDto> {
    return objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)
  }
}
