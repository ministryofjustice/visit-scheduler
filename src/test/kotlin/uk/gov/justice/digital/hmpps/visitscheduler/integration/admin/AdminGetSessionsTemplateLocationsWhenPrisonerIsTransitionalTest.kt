package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.apache.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.ADMIN_SESSION_TEMPLATES_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TransitionalLocationTypes
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TransitionalLocationTypes.COURT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TransitionalLocationTypes.CSWAP
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TransitionalLocationTypes.ECL
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TransitionalLocationTypes.RECP
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TransitionalLocationTypes.TAP
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.integration.mock.dto.PrisonerCellContentNativeDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.mock.dto.PrisonerCellHistoryNativeDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("GET $ADMIN_SESSION_TEMPLATES_PATH with temp location")
class AdminGetSessionsTemplateLocationsWhenPrisonerIsTransitionalTest : IntegrationTestBase() {
  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  private lateinit var authHttpHeaders: (HttpHeaders) -> Unit

  private val nextAllowedDay = getNextAllowedDay()

  lateinit var prisonOther: Prison

  @BeforeEach
  internal fun setUpTests() {
    prisonOther = prisonEntityHelper.create()
    prison = prisonEntityHelper.create("CR1")
    authHttpHeaders = setAuthorisation(roles = requiredRole)
  }

  @Test
  fun `visit sessions are returned for prisoner in COURT with last location`() {
    // Given
    val prisonCode = "CR1"
    val prisonerId = "A0000001"

    setUpStub(prisonerId, prisonCode, COURT)
    val sessionTemplate = setupSessionTemplate(prisonCode)

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    assertReturnedResult(responseSpec, sessionTemplate)
  }

  @Test
  fun `when prisoner is in TAP and TAP visit sessions do not exist sessions are returned for last location`() {
    // Given
    val prisonCode = "CR1"
    val prisonerId = "A0000001"

    setUpStub(prisonerId, prisonCode, TAP)
    // no TAP sessions exist for the prison
    val sessionTemplate = setupSessionTemplate(prisonCode)

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    assertReturnedResult(responseSpec, sessionTemplate)
  }

  @Test
  fun `when prisoner is in TAP and TAP visit sessions exist in prison the only TAP sessions are returned for prisoner`() {
    // Given
    val prisonCode = "CR1"
    val prisonerId = "A0000001"

    setUpStub(prisonerId, prisonCode, TAP)
    setupSessionTemplate(prisonCode)
    val allowedSessionLocationHierarchy = AllowedSessionLocationHierarchy("TAP")
    val sessionTemplateTAP = setupSessionTemplate(prisonCode, allowedPermittedLocations = listOf(allowedSessionLocationHierarchy))

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    assertReturnedResult(responseSpec, sessionTemplateTAP)
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
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

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
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

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
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    assertReturnedResult(responseSpec, sessionTemplate)
  }

  @Test
  fun `visit sessions are returned for prisoner when more than one transitional location is given`() {
    // Given
    val prisonCode = "CR1"
    val prisonerId = "A0000001"
    val transitionalLocations = TransitionalLocationTypes.entries

    stubLastCellLocation(prisonCode, transitionalLocations, 1)
    setUpStub(prisonerId, prisonCode, transitionalLocations[0])
    val sessionTemplate = setupSessionTemplate(prisonCode)

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    assertReturnedResult(responseSpec, sessionTemplate)
  }

  @Test
  fun `visit sessions are not returned for prisoner when we cant find the prisoner historic location`() {
    // Given
    val prisonCode = "CR1"
    val prisonerId = "A0000001"
    val transitionalLocations = TransitionalLocationTypes.entries

    setUpStub(prisonerId, prisonCode, transitionalLocations[0], lastPermanentLevels = null)
    setupSessionTemplate(prisonCode)

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    assertNoResult(responseSpec)
  }

  @Test
  fun `visit sessions are not returned for prisoner when we cant find the prisoner location`() {
    // Given
    val prisonCode = "CR1"
    val prisonerId = "A0000001"
    val transitionalLocations = TransitionalLocationTypes.entries

    stubLastCellLocation(prisonCode, transitionalLocations, 1)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId = prisonerId, prisonCode = prisonCode)

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    assertNoResult(responseSpec)
  }

  @Test
  fun `visit sessions are not returned for prisoner when we cant find the prisoner using search`() {
    // Given
    val prisonCode = "CR1"
    val prisonerId = "A0000001"
    val transitionalLocations = TransitionalLocationTypes.entries

    stubLastCellLocation(prisonCode, transitionalLocations, 1)

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-${RECP.name}")
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId = prisonerId, null)

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    assertErrorResult(
      responseSpec,
      HttpStatusCode.valueOf(HttpStatus.SC_NOT_FOUND),
      "Prisoner with prisonNumber - A0000001 not found on offender search",
    )
  }

  @Test
  fun `visit sessions are not returned for prisoner when we cant find the prisoners details`() {
    // Given
    val prisonCode = "CR1"
    val prisonerId = "A0000001"
    val transitionalLocations = TransitionalLocationTypes.entries

    stubLastCellLocation(prisonCode, transitionalLocations, 1)

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-${RECP.name}")
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, null)

    // When
    val responseSpec = callGetSessions(prisonCode, prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    assertErrorResult(
      responseSpec,
      HttpStatusCode.valueOf(HttpStatus.SC_NOT_FOUND),
      "Prisoner with prisonNumber - A0000001 not found on offender search",
    )
  }

  private fun setUpStub(
    prisonerId: String,
    prisonerPrisonCode: String,
    prisonerTransitionalLocation: TransitionalLocationTypes,
    lastPermanentLevels: String? = "$prisonerPrisonCode-A-1-100-1",
  ) {
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonerPrisonCode-${prisonerTransitionalLocation.name}", lastPermanentLevels = lastPermanentLevels)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId = "A0000001", prisonCode = prisonerPrisonCode)
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

  private fun setupSessionTemplate(
    sessionPrisonCode: String,
    includeLocationGroupType: Boolean = true,
    allowedPermittedLocations: List<AllowedSessionLocationHierarchy> = listOf(
      AllowedSessionLocationHierarchy("A", "1", "100", "1"),
    ),
  ): SessionTemplate {
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
      permittedLocationGroups = mutableListOf(location),
      includeLocationGroupType = includeLocationGroupType,
    )
  }

  private fun getNextAllowedDay(): LocalDate {
    // The two days is based on the default SessionService.policyNoticeDaysMin
    // VB-5790 - adding 1 day after adding policyNoticeDaysMin as there is a change wherein
    // fix sessions are returned after n whole days and not and not today + n so adding a day
    // e.g if today is WED and policyNoticeDaysMin is 2 sessions need to be returned from SATURDAY and not FRIDAY
    return LocalDate.now().plusDays(2).plusDays(1)
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
    errorMessage: String? = null,
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
    assertThat(visitSessionResult.visitRoom).isEqualTo(expectedSessionTemplate.visitRoom)
  }

  private fun getResults(returnResult: WebTestClient.BodyContentSpec): Array<VisitSessionDto> = objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)
}
