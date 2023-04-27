package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Get /visit-sessions")
class GetSessionsWithLocationsTest : IntegrationTestBase() {
  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")
  private val prison: Prison = Prison(code = "MDI", active = true)

  private val nextAllowedDay = getNextAllowedDay()

  private lateinit var sessionTemplateForAllPrisoners: SessionTemplate
  private lateinit var sessionTemplateForSomeLevel1s: SessionTemplate
  private lateinit var sessionTemplateForSomeLevel2s: SessionTemplate
  private lateinit var sessionTemplateForSomeLevel3sAnd1Level2: SessionTemplate
  private lateinit var sessionTemplateForSomeLevel4sAnd2s: SessionTemplate

  @BeforeEach
  internal fun createAllSessionTemplates() {
    // this session template is available for all prisoners in that prison
    sessionTemplateForAllPrisoners = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:01"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prison.code,
      visitRoom = "Session available to all prisoners",
    )

    // this session template is available to levels A,B,D, E and F but not for C
    var allowedPermittedLocations: List<AllowedSessionLocationHierarchy> = listOf(
      AllowedSessionLocationHierarchy("A", null, null, null),
      AllowedSessionLocationHierarchy("B", null, null, null),
      AllowedSessionLocationHierarchy("D", null, null, null),
      AllowedSessionLocationHierarchy("E", null, null, null),
      AllowedSessionLocationHierarchy("F", null, null, null),
    )

    val location1 = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = allowedPermittedLocations)

    sessionTemplateForSomeLevel1s = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("10:01"),
      endTime = LocalTime.parse("11:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prison.code,
      visitRoom = "session available to some level 1",
      permittedSessionGroups = mutableListOf(location1),
    )

    // this session template is available to levels A-1,A-2,A-3 and B-1
    allowedPermittedLocations = listOf(
      AllowedSessionLocationHierarchy("A", "1", null, null),
      AllowedSessionLocationHierarchy("A", "2", null, null),
      AllowedSessionLocationHierarchy("A", "3", null, null),
      AllowedSessionLocationHierarchy("B", "1", null, null),
    )
    val location2 = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = allowedPermittedLocations)

    sessionTemplateForSomeLevel2s = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("11:01"),
      endTime = LocalTime.parse("12:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prison.code,
      visitRoom = "session available to some level 2s",
      permittedSessionGroups = mutableListOf(location2),
    )

    // this session template is available to levels A-1-100, A-1-200, and B-1
    allowedPermittedLocations = listOf(
      AllowedSessionLocationHierarchy("A", "1", "100", null),
      AllowedSessionLocationHierarchy("A", "2", "200", null),
      AllowedSessionLocationHierarchy("B", "1", null, null),
    )
    val location3 = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = allowedPermittedLocations)

    sessionTemplateForSomeLevel3sAnd1Level2 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("12:01"),
      endTime = LocalTime.parse("13:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prison.code,
      visitRoom = "session available to some level 3s and level 2s",
      permittedSessionGroups = mutableListOf(location3),
    )

    allowedPermittedLocations = listOf(
      AllowedSessionLocationHierarchy("A", "1", "100", "1"),
      AllowedSessionLocationHierarchy("A", "2", "100", "3"),
      AllowedSessionLocationHierarchy("B", "1", null, null),
    )
    val location4 = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = allowedPermittedLocations)

    sessionTemplateForSomeLevel4sAnd2s = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("13:01"),
      endTime = LocalTime.parse("14:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      prisonCode = prison.code,
      visitRoom = "session available to some level 4s and level 2s",
      permittedSessionGroups = mutableListOf(location4),
    )

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId = "A0000001", prisonCode = prison.code)
  }

  @Test
  fun `all visit sessions are returned for prison only search`() {
    // When
    val responseSpec = callGetSessions(prison.code)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(5)

    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForSomeLevel1s)
    assertSession(visitSessionResults[2], nextAllowedDay, sessionTemplateForSomeLevel2s)
    assertSession(visitSessionResults[3], nextAllowedDay, sessionTemplateForSomeLevel3sAnd1Level2)
    assertSession(visitSessionResults[4], nextAllowedDay, sessionTemplateForSomeLevel4sAnd2s)
  }

  @Test
  fun `no visit sessions are returned for a different prison`() {
    // Given
    val otherPrison = Prison(code = "XYZ", active = true)

    // When
    val responseSpec = callGetSessions(otherPrison.code)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(0)
  }

  @Test
  fun `multiple visit sessions are returned for prisoner with location as MDI-A-1-100-1`() {
    // Given
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "MDI-A-1-100-1"

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prison.code)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prison.code, prisonerId)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(5)

    // session available to all prisoners
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
    // session available as all A level prisoners are allowed
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForSomeLevel1s)
    // session available as all A-1 level prisoners allowed
    assertSession(visitSessionResults[2], nextAllowedDay, sessionTemplateForSomeLevel2s)
    // session available as all A-1-100 level prisoners allowed
    assertSession(visitSessionResults[3], nextAllowedDay, sessionTemplateForSomeLevel3sAnd1Level2)
    // session available as all A-1-100-1 level prisoners allowed
    assertSession(visitSessionResults[4], nextAllowedDay, sessionTemplateForSomeLevel4sAnd2s)
  }

  @Test
  fun `multiple visit sessions are returned for prisoner with location as MDI-A-2-100-3`() {
    // Given
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "MDI-A-2-100-3"

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prison.code)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prison.code, prisonerId)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(4)

    // session available to all prisoners
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
    // session available as all A level prisoners are allowed
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForSomeLevel1s)
    // session available as all A-2 level prisoners allowed
    assertSession(visitSessionResults[2], nextAllowedDay, sessionTemplateForSomeLevel2s)
    // session available as all A-2-100-3 level prisoners allowed
    assertSession(visitSessionResults[3], nextAllowedDay, sessionTemplateForSomeLevel4sAnd2s)
  }

  @Test
  fun `multiple visit sessions are returned for prisoner with location as MDI-A-2-100-2`() {
    // Given
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "MDI-A-2-100-2"

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prison.code)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prison.code, prisonerId)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(3)

    // session available to all prisoners
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
    // session available as all A level prisoners are allowed
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForSomeLevel1s)
    // session available as all A-2 level prisoners allowed
    assertSession(visitSessionResults[2], nextAllowedDay, sessionTemplateForSomeLevel2s)
  }

  @Test
  fun `multiple visit sessions are returned for prisoner with location as MDI-A-1-100`() {
    // Given
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "MDI-A-1-100"

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prison.code)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prison.code, prisonerId)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(4)

    // session available to all prisoners
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
    // session available as all A level prisoners are allowed
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForSomeLevel1s)
    // session available as all A-2 level prisoners allowed
    assertSession(visitSessionResults[2], nextAllowedDay, sessionTemplateForSomeLevel2s)
  }

  @Test
  fun `multiple visit sessions are returned for prisoner with location as MDI-A-1`() {
    // Given
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "MDI-A-1"

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prison.code)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prison.code, prisonerId)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(3)

    // session available to all prisoners
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
    // session available as all A level prisoners are allowed
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForSomeLevel1s)
    // session available as all A-1 level prisoners allowed
    assertSession(visitSessionResults[2], nextAllowedDay, sessionTemplateForSomeLevel2s)
  }

  @Test
  fun `multiple visit sessions are returned for prisoner with location as MDI-A`() {
    // Given
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "MDI-A"

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prison.code)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prison.code, prisonerId)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(2)

    // session available to all prisoners
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
    // session available as all A level prisoners are allowed
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForSomeLevel1s)
  }

  @Test
  fun `multiple visit sessions are returned for prisoner with location as MDI-B`() {
    // Given
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "MDI-B"

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prison.code)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prison.code, prisonerId)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)
    Assertions.assertThat(visitSessionResults.size).isEqualTo(2)

    // session available to all prisoners
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
    // session available as all B level prisoners are allowed
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForSomeLevel1s)
  }

  @Test
  fun `multiple visit sessions are returned for prisoner with location as MDI-B-1-100-1`() {
    // Given
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "MDI-B-1-100-1"

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prison.code)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prison.code, prisonerId)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)

    Assertions.assertThat(visitSessionResults.size).isEqualTo(5)

    // session available to all prisoners
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
    // session available as all B level prisoners are allowed
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForSomeLevel1s)
    // session available as all B-1 level prisoners allowed
    assertSession(visitSessionResults[2], nextAllowedDay, sessionTemplateForSomeLevel2s)
    // session available as all B-1 level prisoners allowed
    assertSession(visitSessionResults[3], nextAllowedDay, sessionTemplateForSomeLevel3sAnd1Level2)
    // session available as all B-1 level prisoners allowed
    assertSession(visitSessionResults[4], nextAllowedDay, sessionTemplateForSomeLevel4sAnd2s)
  }

  @Test
  fun `multiple visit sessions are returned for prisoner with location as MDI-B-1`() {
    // Given
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "MDI-B-1"

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prison.code)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prison.code, prisonerId)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)

    Assertions.assertThat(visitSessionResults.size).isEqualTo(5)

    // session available to all prisoners
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
    // session available as all B level prisoners are allowed
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForSomeLevel1s)
    // session available as all B-1 level prisoners allowed
    assertSession(visitSessionResults[2], nextAllowedDay, sessionTemplateForSomeLevel2s)
    // session available as all B-1 level prisoners allowed
    assertSession(visitSessionResults[3], nextAllowedDay, sessionTemplateForSomeLevel3sAnd1Level2)
    // session available as all B-1 level prisoners allowed
    assertSession(visitSessionResults[4], nextAllowedDay, sessionTemplateForSomeLevel4sAnd2s)
  }

  @Test
  fun `single visit sessions are returned for prisoner in C Level`() {
    // Given
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "MDI-C-100-1"

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prison.code)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prison.code, prisonerId)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)

    Assertions.assertThat(visitSessionResults.size).isEqualTo(1)

    // session available to all prisoners
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
  }

  @Test
  fun `multiple visit sessions are returned for prisoner with location as MDI-D-100-1`() {
    // Given
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "MDI-D-100-1"

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prison.code)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prison.code, prisonerId)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)

    Assertions.assertThat(visitSessionResults.size).isEqualTo(2)

    // session available to all prisoners
    assertSession(visitSessionResults[0], nextAllowedDay, sessionTemplateForAllPrisoners)
    // session available as all D level prisoners are allowed
    assertSession(visitSessionResults[1], nextAllowedDay, sessionTemplateForSomeLevel1s)
  }

  @Test
  fun `one session unavailable when non association has a visit for one of the sessions`() {
    // Given
    val prisonerId = "A0000001"
    val prisonerInternalLocation = "MDI-A-1-100-1"
    val associationId = "B1234BB"

    this.visitEntityHelper.create(
      prisonerId = associationId,
      prisonCode = prison.code,
      visitRoom = sessionTemplateForAllPrisoners.visitRoom,
      visitStart = nextAllowedDay.atTime(sessionTemplateForAllPrisoners.startTime),
      visitEnd = nextAllowedDay.atTime(sessionTemplateForAllPrisoners.endTime),
      visitType = VisitType.SOCIAL,
      visitStatus = VisitStatus.BOOKED,
      visitRestriction = VisitRestriction.OPEN,
    )

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationId,
      LocalDate.now().minusMonths(6),
      LocalDate.now().plusMonths(6),
    )

    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, prisonerInternalLocation)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerId, prison.code)

    // When
    val responseSpec = callGetSessionsByPrisonerIdAndPrison(prison.code, prisonerId)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val visitSessionResults = getResults(returnResult)

    // none of the sessions on the day will be available
    Assertions.assertThat(visitSessionResults.size).isEqualTo(0)
  }

  private fun callGetSessions(prisonId: String): WebTestClient.ResponseSpec {
    return webTestClient.get().uri("/visit-sessions?prisonId=$prisonId")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()
  }

  private fun callGetSessionsByPrisonerIdAndPrison(prisonId: String, prisonerId: String): WebTestClient.ResponseSpec {
    return webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
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
    expectedSessionTemplate: SessionTemplate,
  ) {
    Assertions.assertThat(visitSessionResult.startTimestamp)
      .isEqualTo(testDate.atTime(expectedSessionTemplate.startTime))
    Assertions.assertThat(visitSessionResult.endTimestamp).isEqualTo(testDate.atTime(expectedSessionTemplate.endTime))
    Assertions.assertThat(visitSessionResult.startTimestamp.dayOfWeek).isEqualTo(expectedSessionTemplate.dayOfWeek)
    Assertions.assertThat(visitSessionResult.endTimestamp.dayOfWeek).isEqualTo(expectedSessionTemplate.dayOfWeek)
    Assertions.assertThat(visitSessionResult.visitRoom).isEqualTo(expectedSessionTemplate.visitRoom)
  }

  private fun getResults(returnResult: WebTestClient.BodyContentSpec): Array<VisitSessionDto> {
    return objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitSessionDto>::class.java)
  }
}
