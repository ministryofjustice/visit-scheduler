package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_SESSION_CONTROLLER_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TransitionalLocationTypes
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("GET $VISIT_SESSION_CONTROLLER_PATH - tests for exclude locations")
class GetSessionsWithExcludeLocationsTest : IntegrationTestBase() {
  private lateinit var authHttpHeaders: (HttpHeaders) -> Unit

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  private val nextAllowedDay = getNextAllowedDay()

  private lateinit var sessionTemplateForAllPrisoners: SessionTemplate
  private lateinit var sessionTemplateForSomeLevel1s: SessionTemplate
  private lateinit var sessionTemplateForSomeLevel2s: SessionTemplate
  private lateinit var sessionTemplateForSomeLevel3sAnd1Level2: SessionTemplate
  private lateinit var sessionTemplateForSomeLevel4sAnd2s: SessionTemplate
  private lateinit var tapAsLocation: SessionLocationGroup

  @BeforeEach
  internal fun createAllSessionTemplates() {
    authHttpHeaders = setAuthorisation(roles = requiredRole)

    prison = prisonEntityHelper.create("SWL")
    tapAsLocation = sessionLocationGroupHelper.create(
      prisonCode = prison.code,
      prisonHierarchies = listOf(
        AllowedSessionLocationHierarchy("TAP", null, null, null),
      ),
    )

    // this session template is available for all prisoners in that prison except for TAP locations
    sessionTemplateForAllPrisoners = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:01"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prison.code,
      visitRoom = "Session available to all prisoners",
      includeLocationGroupType = false,
      permittedLocationGroups = mutableListOf(tapAsLocation),
    )

    // this session template is unavailable to levels A,B,D, E and F but not for C
    var disAllowedPermittedLocations: List<AllowedSessionLocationHierarchy> = listOf(
      AllowedSessionLocationHierarchy("A", null, null, null),
      AllowedSessionLocationHierarchy("B", null, null, null),
      AllowedSessionLocationHierarchy("D", null, null, null),
      AllowedSessionLocationHierarchy("E", null, null, null),
      AllowedSessionLocationHierarchy("F", null, null, null),
    )

    val location1 = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = disAllowedPermittedLocations)

    sessionTemplateForSomeLevel1s = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("10:01"),
      endTime = LocalTime.parse("11:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prison.code,
      visitRoom = "session available to some level 1",
      permittedLocationGroups = mutableListOf(location1, tapAsLocation),
      includeLocationGroupType = false,
    )

    // this session template is unavailable to levels A-1,A-2,A-3 and B-1
    disAllowedPermittedLocations = listOf(
      AllowedSessionLocationHierarchy("A", "1", null, null),
      AllowedSessionLocationHierarchy("A", "2", null, null),
      AllowedSessionLocationHierarchy("A", "3", null, null),
      AllowedSessionLocationHierarchy("B", "1", null, null),
    )
    val location2 = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = disAllowedPermittedLocations)

    sessionTemplateForSomeLevel2s = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("11:01"),
      endTime = LocalTime.parse("12:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prison.code,
      visitRoom = "session available to some level 2s",
      permittedLocationGroups = mutableListOf(location2, tapAsLocation),
      includeLocationGroupType = false,
    )

    // this session template is unavailable to levels A-1-100, A-1-200, and B-1
    disAllowedPermittedLocations = listOf(
      AllowedSessionLocationHierarchy("A", "1", "100", null),
      AllowedSessionLocationHierarchy("A", "2", "200", null),
      AllowedSessionLocationHierarchy("B", "1", null, null),
    )
    val location3 = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = disAllowedPermittedLocations)

    sessionTemplateForSomeLevel3sAnd1Level2 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("12:01"),
      endTime = LocalTime.parse("13:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prison.code,
      visitRoom = "session available to some level 3s and level 2s",
      permittedLocationGroups = mutableListOf(location3, tapAsLocation),
      includeLocationGroupType = false,
    )

    disAllowedPermittedLocations = listOf(
      AllowedSessionLocationHierarchy("A", "1", "100", "1"),
      AllowedSessionLocationHierarchy("A", "2", "100", "3"),
      AllowedSessionLocationHierarchy("B", "1", null, null),
    )
    val location4 = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = disAllowedPermittedLocations)

    sessionTemplateForSomeLevel4sAnd2s = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("13:01"),
      endTime = LocalTime.parse("14:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prison.code,
      visitRoom = "session available to some level 4s and level 2s",
      permittedLocationGroups = mutableListOf(location4, tapAsLocation),
      includeLocationGroupType = false,
    )
    sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = disAllowedPermittedLocations)

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId = "A0000001", prisonCode = prison.code)
  }

  @Test
  fun `no visit sessions are returned for prisoner with location as SWL-A-1-100-1`() {
    // Given
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "SWL-A-1-100-1"

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation)

    // When
    val responseSpec = callGetSessions(prisonCode = prison.code, prisonerId = prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(1)

    // only session available to all prisoners is returned
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
  }

  @Test
  fun `multiple visit sessions are returned for prisoner with location as SWL-A-2-100-3`() {
    // Given
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "SWL-A-2-100-3"

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation)

    // When
    val responseSpec = callGetSessions(prisonCode = prison.code, prisonerId = prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(2)

    // session available to all prisoners
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
    // session available as A-2-100-3 level prisoners are not disallowed
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForSomeLevel3sAnd1Level2)
  }

  @Test
  fun `multiple visit sessions are returned for prisoner with location as SWL-A-2-100-2`() {
    // Given
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "SWL-A-2-100-2"

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation)

    // When
    val responseSpec = callGetSessions(prisonCode = prison.code, prisonerId = prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(3)

    // session available to all prisoners
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
    // session available as all A level prisoners are allowed
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForSomeLevel3sAnd1Level2)
    // session available as all A-2 level prisoners allowed
    assertSession(visitSessionResults[2], nextAllowedDay, sessionTemplateForSomeLevel4sAnd2s)
  }

  @Test
  fun `multiple visit sessions are returned for prisoner with location as SWL-A-1-100`() {
    // Given
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "SWL-A-1-100"

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation)

    // When
    val responseSpec = callGetSessions(prisonCode = prison.code, prisonerId = prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(2)

    // session available to all prisoners
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForSomeLevel4sAnd2s)
  }

  @Test
  fun `multiple visit sessions are returned for prisoner with location as SWL-A-1`() {
    // Given
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "SWL-A-1"

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation)

    // When
    val responseSpec = callGetSessions(prisonCode = prison.code, prisonerId = prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(3)

    // session available to all prisoners
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForSomeLevel3sAnd1Level2)
    assertSession(visitSessionResults[2], nextAllowedDay, sessionTemplateForSomeLevel4sAnd2s)
  }

  @Test
  fun `multiple visit sessions are returned for prisoner with location as SWL-A`() {
    // Given
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "SWL-A"

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation)

    // When
    val responseSpec = callGetSessions(prisonCode = prison.code, prisonerId = prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(4)

    // session available to all prisoners
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
    // session available as all A level prisoners are allowed
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForSomeLevel2s)
    assertSession(visitSessionResults[2], nextAllowedDay, sessionTemplateForSomeLevel3sAnd1Level2)
    assertSession(visitSessionResults[3], nextAllowedDay, sessionTemplateForSomeLevel4sAnd2s)
  }

  @Test
  fun `multiple visit sessions are returned for prisoner with location as SWL-B`() {
    // Given
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "SWL-B"

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation)

    // When
    val responseSpec = callGetSessions(prisonCode = prison.code, prisonerId = prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(4)

    // session available to all prisoners
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
    // session available as all B level prisoners are allowed
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForSomeLevel2s)
    assertSession(visitSessionResults[2], nextAllowedDay, sessionTemplateForSomeLevel3sAnd1Level2)
    assertSession(visitSessionResults[3], nextAllowedDay, sessionTemplateForSomeLevel4sAnd2s)
  }

  @Test
  fun `multiple visit sessions are returned for prisoner with location as SWL-B-1-100-1`() {
    // Given
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "SWL-B-1-100-1"

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation)

    // When
    val responseSpec = callGetSessions(prisonCode = prison.code, prisonerId = prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)

    Assertions.assertThat(visitSessionResults.size).isEqualTo(1)

    // session available to all prisoners
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
  }

  @Test
  fun `multiple visit sessions are returned for prisoner with location as SWL-B-1`() {
    // Given
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "SWL-B-1"

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation)

    // When
    val responseSpec = callGetSessions(prisonCode = prison.code, prisonerId = prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)

    Assertions.assertThat(visitSessionResults.size).isEqualTo(1)

    // session available to all prisoners
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
  }

  @Test
  fun `multiple visit sessions are returned for prisoner in C Level`() {
    // Given
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "SWL-C-100-1"

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation)

    // When
    val responseSpec = callGetSessions(prisonCode = prison.code, prisonerId = prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)

    Assertions.assertThat(visitSessionResults.size).isEqualTo(5)

    // session available to all prisoners
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForSomeLevel1s)
    assertSession(visitSessionResults[2], nextAllowedDay, sessionTemplateForSomeLevel2s)
    assertSession(visitSessionResults[3], nextAllowedDay, sessionTemplateForSomeLevel3sAnd1Level2)
    assertSession(visitSessionResults[4], nextAllowedDay, sessionTemplateForSomeLevel4sAnd2s)
  }

  @Test
  fun `multiple visit sessions are returned for prisoner with last location as SWL-D-100-1 although current prisoner location is COURT`() {
    // As the prisoner is in a transitional location (COURT) - sessions are returned based on his last permanent location
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "SWL-D-100-1"
    val prisonerTemporaryLocation = TransitionalLocationTypes.COURT.toString()

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation, prisonerTemporaryLocation)

    // When
    val responseSpec = callGetSessions(prisonCode = prison.code, prisonerId = prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)

    Assertions.assertThat(visitSessionResults.size).isEqualTo(4)

    // session available to all prisoners
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
    // session available as all D level prisoners are allowed
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForSomeLevel2s)
    assertSession(visitSessionResults[2], nextAllowedDay, sessionTemplateForSomeLevel3sAnd1Level2)
    assertSession(visitSessionResults[3], nextAllowedDay, sessionTemplateForSomeLevel4sAnd2s)
  }

  @Test
  fun `multiple visit sessions are returned for prisoner based on last location and current location as TAP in prison without TAP sessions`() {
    // Given no TAP sessions exist for the prisoner - sessions are returned based on his last permanent location
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "SWL-D-100-1"
    val prisonerTemporaryLocation = "SWL-${TransitionalLocationTypes.TAP}"

    prisonApiMockServer.stubGetPrisonerHousingLocation(offenderNo = prisonerId, internalLocation = prisonerTemporaryLocation, lastPermanentLevels = prisonerInternalLocation)

    // When
    val responseSpec = callGetSessions(prisonCode = prison.code, prisonerId = prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)

    Assertions.assertThat(visitSessionResults.size).isEqualTo(4)

    // session available to all prisoners
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForSomeLevel2s)
    assertSession(visitSessionResults[2], nextAllowedDay, sessionTemplateForSomeLevel3sAnd1Level2)
    assertSession(visitSessionResults[3], nextAllowedDay, sessionTemplateForSomeLevel4sAnd2s)
  }

  @Test
  fun `only TAP visit sessions are returned for prisoner with current location as TAP in prison with TAP sessions`() {
    // Given no TAP sessions exist for the prisoner - sessions are returned based on his last permanent location
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "SWL-D-100-1"
    val prisonerTemporaryLocation = "SWL-${TransitionalLocationTypes.TAP}"

    // session template for users in TAP location only
    val tapSessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("14:01"),
      endTime = LocalTime.parse("15:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prison.code,
      visitRoom = "session available to TAP location only",
      includeLocationGroupType = true,
      permittedLocationGroups = mutableListOf(tapAsLocation),
    )

    prisonApiMockServer.stubGetPrisonerHousingLocation(offenderNo = prisonerId, internalLocation = prisonerTemporaryLocation, lastPermanentLevels = prisonerInternalLocation)

    // When
    val responseSpec = callGetSessions(prisonCode = prison.code, prisonerId = prisonerId, userType = STAFF, authHttpHeaders = authHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)

    Assertions.assertThat(visitSessionResults.size).isEqualTo(1)

    // only TAP session is available to that prisoner
    assertSession(visitSessionResults[0], nextAllowedDay, tapSessionTemplate)
  }

  private fun getNextAllowedDay(): LocalDate {
    // The two days is based on the default SessionService.policyNoticeDaysMin
    // VB-5790 - adding 1 day after adding policyNoticeDaysMin as there is a change wherein
    // fix sessions are returned after n whole days and not and not today + n so adding a day
    // e.g if today is WED and policyNoticeDaysMin is 2 sessions need to be returned from SATURDAY and not FRIDAY
    return LocalDate.now().plusDays(2).plusDays(1)
  }

  private fun assertSession(
    visitSessionResult: VisitSessionDto,
    testDate: LocalDate,
    expectedSessionTemplate: SessionTemplate,
  ) {
    Assertions.assertThat(visitSessionResult.startTimestamp)
      .isEqualTo(testDate.atTime(expectedSessionTemplate.startTime))
    Assertions.assertThat(visitSessionResult.endTimestamp).isEqualTo(testDate.atTime(expectedSessionTemplate.endTime))
    Assertions.assertThat(visitSessionResult.startTimestamp.dayOfWeek).isEqualTo(expectedSessionTemplate.dayOfWeek)
    Assertions.assertThat(visitSessionResult.endTimestamp.dayOfWeek).isEqualTo(expectedSessionTemplate.dayOfWeek)
    Assertions.assertThat(visitSessionResult.visitRoom).isEqualTo(expectedSessionTemplate.visitRoom)
  }

  private fun getResults(returnResult: WebTestClient.BodyContentSpec): Array<VisitSessionDto> = objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)
}
