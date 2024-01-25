package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.MOVE_VISITS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.MoveVisitsDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callMoveVisits
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionCategoryGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.SessionIncentiveLevelGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.LocalDate

@DisplayName("Post $MOVE_VISITS")
class AdminMoveTemplateVisitsTest : IntegrationTestBase() {

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  private lateinit var level1ALocations: SessionLocationGroup
  private lateinit var level2A1Locations: SessionLocationGroup
  private lateinit var level2A2Locations: SessionLocationGroup
  private lateinit var level3A12Locations: SessionLocationGroup
  private lateinit var level4A123Locations: SessionLocationGroup
  private lateinit var level1BLocations: SessionLocationGroup
  private lateinit var categoryAs: SessionCategoryGroup
  private lateinit var categoryBCandD: SessionCategoryGroup
  private lateinit var incentiveLevelEnhanced: SessionIncentiveLevelGroup
  private lateinit var incentiveLevelNonEnhanced: SessionIncentiveLevelGroup

  @BeforeEach
  internal fun setUpTests() {
    prison = prisonEntityHelper.create()

    level1ALocations = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = listOf(AllowedSessionLocationHierarchy("A")))
    level2A1Locations = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = listOf(AllowedSessionLocationHierarchy("A", "1")))
    level2A2Locations = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = listOf(AllowedSessionLocationHierarchy("A", "2")))
    level3A12Locations = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = listOf(AllowedSessionLocationHierarchy("A", "1", "2")))
    level4A123Locations = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = listOf(AllowedSessionLocationHierarchy("A", "1", "2", "3")))
    level1BLocations = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = listOf(AllowedSessionLocationHierarchy("B")))

    categoryAs = sessionPrisonerCategoryHelper.create(prisonerCategories = listOf(PrisonerCategoryType.A_STANDARD, PrisonerCategoryType.A_EXCEPTIONAL, PrisonerCategoryType.A_HIGH, PrisonerCategoryType.A_PROVISIONAL))
    categoryBCandD = sessionPrisonerCategoryHelper.create(prisonerCategories = listOf(PrisonerCategoryType.B, PrisonerCategoryType.C, PrisonerCategoryType.D))
    incentiveLevelEnhanced = sessionPrisonerIncentiveLevelHelper.create(incentiveLevelList = listOf(IncentiveLevel.ENHANCED, IncentiveLevel.ENHANCED_2, IncentiveLevel.ENHANCED_3))
    incentiveLevelNonEnhanced = sessionPrisonerIncentiveLevelHelper.create(incentiveLevelList = listOf(IncentiveLevel.BASIC, IncentiveLevel.STANDARD))
  }

  @Test
  fun `when session template visit moves are covered then move visits are successful`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY)
    val toSession = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY)
    val visitDate = LocalDate.now().plusDays(1)
    val visit = visitEntityHelper.create(sessionTemplate = fromSession, visitStart = fromSession.startTime, visitEnd = fromSession.endTime, slotDate = visitDate)
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))
    val updatedVisit = visitEntityHelper.getBookedVisit(visit.reference)

    // Then
    responseSpec.expectStatus().isOk

    val responseBody = responseSpec.expectBody().returnResult().responseBody
    val updateCount = String(responseBody!!, StandardCharsets.UTF_8).toInt()

    Assertions.assertThat(updateCount).isEqualTo(1)
    Assertions.assertThat(updatedVisit!!.sessionSlot.sessionTemplateReference).isEqualTo(toSession.reference)
    Assertions.assertThat(updatedVisit.sessionSlot.slotTime).isEqualTo(toSession.startTime)
    Assertions.assertThat(updatedVisit.sessionSlot.slotEndTime).isEqualTo(toSession.endTime)
    Assertions.assertThat(updatedVisit.sessionSlot.slotDate).isEqualTo(visitDate)
  }

  @Test
  fun `when session template has invalid from session template reference then validation fails`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY)
    val toSession = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY)
    val moveRequest = MoveVisitsDto(fromSession.reference + "aa", toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isNotFound
  }

  @Test
  fun `when session template has invalid to session template reference then validation fails`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY)
    val toSession = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY)
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference + "aa", LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isNotFound
  }

  @Test
  fun `when session template has invalid from date then validation fails`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY)
    val toSession = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY)
    val fromDate = LocalDate.now().minusDays(1)
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, fromDate)

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `when session template has different prison code then validation fails`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY, prisonCode = "MDI")
    val toSession = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY, prisonCode = "SBI")

    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").isEqualTo("From and to session templates have different prison codes")
  }

  @Test
  fun `when session template has different day of week then validation fails`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY)
    val toSession = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.TUESDAY)

    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").isEqualTo("From and to session templates have different day of week")
  }

  @Test
  fun `when session template has different session time - higher than allowed range then validation fails`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create()
    val toSession = sessionTemplateEntityHelper.create(startTime = fromSession.startTime.plusMinutes(61), endTime = fromSession.endTime.plusHours(1))

    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").isEqualTo("New session time is out of the current allowed range of 60 minutes for a visit move")
  }

  @Test
  fun `when session template has different session time - lower than allowed range then validation fails`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create()
    val toSession = sessionTemplateEntityHelper.create(startTime = fromSession.startTime.minusMinutes(61), endTime = fromSession.endTime.plusHours(1))

    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").isEqualTo("New session time is out of the current allowed range of 60 minutes for a visit move")
  }

  @Test
  fun `when session template has different session time but within allowed range then move visits are successful`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY)
    val toSession = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY, startTime = fromSession.startTime.minusMinutes(59), endTime = fromSession.endTime)
    val visitDate = LocalDate.now().plusDays(1)
    val visit = visitEntityHelper.create(
      sessionTemplate = fromSession,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
      slotDate = visitDate,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))
    val updatedVisit = visitEntityHelper.getVisit(visit.reference)

    // Then
    responseSpec.expectStatus().isOk

    val responseBody = responseSpec.expectBody().returnResult().responseBody
    val updateCount = String(responseBody!!, StandardCharsets.UTF_8).toInt()

    Assertions.assertThat(updateCount).isEqualTo(1)
    Assertions.assertThat(updatedVisit!!.sessionSlot.sessionTemplateReference).isEqualTo(toSession.reference)
    Assertions.assertThat(updatedVisit.sessionSlot.slotTime).isEqualTo(toSession.startTime)
    Assertions.assertThat(updatedVisit.sessionSlot.slotEndTime).isEqualTo(toSession.endTime)
    Assertions.assertThat(updatedVisit.sessionSlot.slotDate).isEqualTo(visitDate)
  }

  @Test
  fun `when session template has higher session time but within allowed range then move visits are successful`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY)
    val toSession = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY, startTime = fromSession.startTime.plusMinutes(59), endTime = fromSession.endTime)
    val visitDate = LocalDate.now().plusDays(1)
    val visit = visitEntityHelper.create(
      sessionTemplate = fromSession,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
      slotDate = visitDate,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))
    val updatedVisit = visitEntityHelper.getBookedVisit(visit.reference)

    // Then
    responseSpec.expectStatus().isOk

    val responseBody = responseSpec.expectBody().returnResult().responseBody
    val updateCount = String(responseBody!!, StandardCharsets.UTF_8).toInt()

    Assertions.assertThat(updateCount).isEqualTo(1)
    Assertions.assertThat(updatedVisit!!.sessionSlot.sessionTemplateReference).isEqualTo(toSession.reference)
    Assertions.assertThat(updatedVisit.sessionSlot.slotTime).isEqualTo(toSession.startTime)
    Assertions.assertThat(updatedVisit.sessionSlot.slotEndTime).isEqualTo(toSession.endTime)
    Assertions.assertThat(updatedVisit.sessionSlot.slotDate).isEqualTo(visitDate)
  }

  @Test
  fun `when to session template has higher weekly frequency but no visits to be migrated then move visits are successful`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(weeklyFrequency = 1)
    val toSession = sessionTemplateEntityHelper.create(weeklyFrequency = 2)

    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when to session template has lower weekly frequency but no visits to be migrated then move visits are successful`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(weeklyFrequency = 3)
    val toSession = sessionTemplateEntityHelper.create(weeklyFrequency = 2)

    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when to session template has lower weekly frequency than current then move visits are successful`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(weeklyFrequency = 3)
    val toSession = sessionTemplateEntityHelper.create(weeklyFrequency = 1)

    val visitDate = LocalDate.now().plusDays(1)
    val visit = visitEntityHelper.create(
      sessionTemplate = fromSession,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
      slotDate = visitDate,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))
    val updatedVisit = visitEntityHelper.getBookedVisit(visit.reference)

    // Then
    responseSpec.expectStatus().isOk

    val responseBody = responseSpec.expectBody().returnResult().responseBody
    val updateCount = String(responseBody!!, StandardCharsets.UTF_8).toInt()

    Assertions.assertThat(updateCount).isEqualTo(1)
    Assertions.assertThat(updatedVisit!!.sessionSlot.sessionTemplateReference).isEqualTo(toSession.reference)
    Assertions.assertThat(updatedVisit.sessionSlot.slotTime).isEqualTo(toSession.startTime)
    Assertions.assertThat(updatedVisit.sessionSlot.slotEndTime).isEqualTo(toSession.endTime)
    Assertions.assertThat(updatedVisit.sessionSlot.slotDate).isEqualTo(visitDate)
  }

  @Test
  fun `when to session template has higher weekly frequency than current but all visits can be moved are successful`() {
    // Given
    val today = LocalDate.now()
    val fromSession = sessionTemplateEntityHelper.create(weeklyFrequency = 1, dayOfWeek = today.dayOfWeek, validFromDate = today)
    val toSession = sessionTemplateEntityHelper.create(weeklyFrequency = 3, dayOfWeek = today.dayOfWeek, validFromDate = today, startTime = fromSession.startTime.minusMinutes(30), endTime = fromSession.endTime)

    // visit date falls after 3 weeks
    val visit1Date = today.plusWeeks(3)
    val visit1 = visitEntityHelper.create(
      sessionTemplate = fromSession,
      slotDate = visit1Date,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
    )

    val visit2Date = today.plusWeeks(6)
    val visit2 = visitEntityHelper.create(
      sessionTemplate = fromSession,
      slotDate = visit1Date,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))
    val updatedVisit1 = visitEntityHelper.getBookedVisit(visit1.reference)
    val updatedVisit2 = visitEntityHelper.getBookedVisit(visit2.reference)

    // Then
    responseSpec.expectStatus().isOk

    val responseBody = responseSpec.expectBody().returnResult().responseBody
    val updateCount = String(responseBody!!, StandardCharsets.UTF_8).toInt()

    Assertions.assertThat(updateCount).isEqualTo(2)
    Assertions.assertThat(updatedVisit1!!.sessionSlot.sessionTemplateReference).isEqualTo(toSession.reference)
    Assertions.assertThat(updatedVisit1.sessionSlot.slotTime).isEqualTo(toSession.startTime)
    Assertions.assertThat(updatedVisit1.sessionSlot.slotEndTime).isEqualTo(toSession.endTime)
    Assertions.assertThat(updatedVisit1.sessionSlot.slotDate).isEqualTo(visit1Date)

    Assertions.assertThat(updatedVisit2!!.sessionSlot.sessionTemplateReference).isEqualTo(toSession.reference)
    Assertions.assertThat(updatedVisit2.sessionSlot.slotTime).isEqualTo(toSession.startTime)
    Assertions.assertThat(updatedVisit2.sessionSlot.slotEndTime).isEqualTo(toSession.endTime)
    Assertions.assertThat(updatedVisit2.sessionSlot.slotDate).isEqualTo(visit2Date)
  }

  @Test
  fun `when to session template starts after first visit date then validation fails`() {
    // Given
    val fromDate = LocalDate.now().plusDays(1)
    val fromSession = sessionTemplateEntityHelper.create()
    val toSession = sessionTemplateEntityHelper.create(validFromDate = fromDate.plusDays(1))

    val visitDate = LocalDate.now().plusDays(1)
    visitEntityHelper.create(
      sessionTemplate = fromSession,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
      slotDate = visitDate,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, fromDate)

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").isEqualTo("New session template dates cannot accommodate all migrated visits.")
  }

  @Test
  fun `when last visit date is after to session end date then validation fails`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create()
    val toSession = sessionTemplateEntityHelper.create(validFromDate = LocalDate.now(), validToDate = LocalDate.now().plusDays(1))

    val visitDate = LocalDate.now().plusWeeks(2)
    visitEntityHelper.create(
      sessionTemplate = fromSession,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
      slotDate = visitDate,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").isEqualTo("New session template dates cannot accommodate all migrated visits.")
  }

  @Test
  fun `when all of the from sessions locations can be accommodated then move validation is successful`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(permittedLocationGroups = mutableListOf(level1ALocations))
    val toSession = sessionTemplateEntityHelper.create()

    val visitDate = LocalDate.now().plusWeeks(1)
    visitEntityHelper.create(
      sessionTemplate = fromSession,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
      slotDate = visitDate,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$").isEqualTo("1")
  }

  @Test
  fun `when all of the from sessions locations cannot be accommodated then move validation fails`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create()
    val toSession = sessionTemplateEntityHelper.create(permittedLocationGroups = mutableListOf(level1ALocations))

    val visitDate = LocalDate.now().plusWeeks(1)
    visitEntityHelper.create(
      sessionTemplate = fromSession,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
      slotDate = visitDate,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").isEqualTo("New session template locations cannot accommodate all locations in existing session template.")
  }

  @Test
  fun `when all of the from sessions locations - level 1 can be accommodated then move validation is successful`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(permittedLocationGroups = mutableListOf(level1ALocations))
    val toSession = sessionTemplateEntityHelper.create(permittedLocationGroups = mutableListOf(level1ALocations))

    val visitDate = LocalDate.now().plusWeeks(1)
    visitEntityHelper.create(
      sessionTemplate = fromSession,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
      slotDate = visitDate,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$").isEqualTo("1")
  }

  @Test
  fun `when all of the from sessions locations - level 1 cannot be accommodated then move validation fails`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(permittedLocationGroups = mutableListOf(level1ALocations))
    val toSession = sessionTemplateEntityHelper.create(permittedLocationGroups = mutableListOf(level2A1Locations))

    val visitDate = LocalDate.now().plusWeeks(1)
    visitEntityHelper.create(
      sessionTemplate = fromSession,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
      slotDate = visitDate,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").isEqualTo("New session template locations cannot accommodate all locations in existing session template.")
  }

  @Test
  fun `when all of the from sessions locations - level 2 can be accommodated then move validation is successful`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(permittedLocationGroups = mutableListOf(level2A1Locations))
    val toSession = sessionTemplateEntityHelper.create(permittedLocationGroups = mutableListOf(level1ALocations))

    val visitDate = LocalDate.now().plusWeeks(1)
    visitEntityHelper.create(
      sessionTemplate = fromSession,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
      slotDate = visitDate,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$").isEqualTo("1")
  }

  @Test
  fun `when all of the from sessions locations - level 2 cannot be accommodated then move validation fails`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(permittedLocationGroups = mutableListOf(level2A1Locations))
    val toSession = sessionTemplateEntityHelper.create(permittedLocationGroups = mutableListOf(level3A12Locations))

    val visitDate = LocalDate.now().plusWeeks(1)
    visitEntityHelper.create(
      sessionTemplate = fromSession,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
      slotDate = visitDate,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").isEqualTo("New session template locations cannot accommodate all locations in existing session template.")
  }

  @Test
  fun `when all of the from sessions locations - level 3 can be accommodated then move validation is successful`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(permittedLocationGroups = mutableListOf(level3A12Locations))
    val toSession = sessionTemplateEntityHelper.create(permittedLocationGroups = mutableListOf(level2A1Locations))

    val visitDate = LocalDate.now().plusWeeks(1)
    visitEntityHelper.create(
      sessionTemplate = fromSession,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
      slotDate = visitDate,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$").isEqualTo("1")
  }

  @Test
  fun `when all of the from sessions locations - level 3 cannot be accommodated then move validation fails`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(permittedLocationGroups = mutableListOf(level3A12Locations))
    val toSession = sessionTemplateEntityHelper.create(permittedLocationGroups = mutableListOf(level4A123Locations))

    val visitDate = LocalDate.now().plusWeeks(1)
    visitEntityHelper.create(
      sessionTemplate = fromSession,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
      slotDate = visitDate,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").isEqualTo("New session template locations cannot accommodate all locations in existing session template.")
  }

  @Test
  fun `when all of the from sessions locations - level 4 can be accommodated then move validation is successful`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(permittedLocationGroups = mutableListOf(level4A123Locations))
    val toSession = sessionTemplateEntityHelper.create(permittedLocationGroups = mutableListOf(level3A12Locations))

    val visitDate = LocalDate.now().plusWeeks(1)
    visitEntityHelper.create(
      sessionTemplate = fromSession,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
      slotDate = visitDate,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$").isEqualTo("1")
  }

  @Test
  fun `when all of the from sessions locations - different levels can be accommodated then move validation is successful`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(permittedLocationGroups = mutableListOf(level4A123Locations, level3A12Locations, level2A1Locations))
    val toSession = sessionTemplateEntityHelper.create(permittedLocationGroups = mutableListOf(level1ALocations))

    val visitDate = LocalDate.now().plusWeeks(1)
    visitEntityHelper.create(
      sessionTemplate = fromSession,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
      slotDate = visitDate,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$").isEqualTo("1")
  }

  @Test
  fun `when one of the from sessions locations - cannot be accommodated then move validation fails`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(permittedLocationGroups = mutableListOf(level4A123Locations, level3A12Locations, level2A1Locations, level1BLocations))
    val toSession = sessionTemplateEntityHelper.create(permittedLocationGroups = mutableListOf(level4A123Locations))

    val visitDate = LocalDate.now().plusWeeks(1)
    visitEntityHelper.create(
      sessionTemplate = fromSession,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
      slotDate = visitDate,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").isEqualTo("New session template locations cannot accommodate all locations in existing session template.")
  }

  @Test
  fun `when all of the categories can be accommodated then move validation is successful`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(permittedCategories = mutableListOf(categoryAs))
    val toSession = sessionTemplateEntityHelper.create()

    val visitDate = LocalDate.now().plusWeeks(1)
    visitEntityHelper.create(
      sessionTemplate = fromSession,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
      slotDate = visitDate,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$").isEqualTo("1")
  }

  @Test
  fun `when all of the categories - specific can be accommodated then move validation is successful`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(permittedCategories = mutableListOf(categoryAs))
    val toSession = sessionTemplateEntityHelper.create(permittedCategories = mutableListOf(categoryAs, categoryBCandD))

    val visitDate = LocalDate.now().plusWeeks(1)
    visitEntityHelper.create(
      sessionTemplate = fromSession,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
      slotDate = visitDate,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$").isEqualTo("1")
  }

  @Test
  fun `when all of the categories cannot be accommodated then move validation fails`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(permittedCategories = mutableListOf(categoryBCandD))
    val toSession = sessionTemplateEntityHelper.create(permittedCategories = mutableListOf(categoryAs))

    val visitDate = LocalDate.now().plusWeeks(1)
    visitEntityHelper.create(
      sessionTemplate = fromSession,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
      slotDate = visitDate,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").isEqualTo("New session template categories cannot accommodate all categories in existing session template.")
  }

  @Test
  fun `when all of the incentive levels can be accommodated then move validation is successful`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(permittedIncentiveLevels = mutableListOf(incentiveLevelEnhanced))
    val toSession = sessionTemplateEntityHelper.create()

    val visitDate = LocalDate.now().plusWeeks(1)
    visitEntityHelper.create(
      sessionTemplate = fromSession,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
      slotDate = visitDate,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$").isEqualTo("1")
  }

  @Test
  fun `when all of the incentive levels - specific can be accommodated then move validation is successful`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(permittedIncentiveLevels = mutableListOf(incentiveLevelEnhanced))
    val toSession = sessionTemplateEntityHelper.create(permittedIncentiveLevels = mutableListOf(incentiveLevelEnhanced, incentiveLevelNonEnhanced))

    val visitDate = LocalDate.now().plusWeeks(1)
    visitEntityHelper.create(
      sessionTemplate = fromSession,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
      slotDate = visitDate,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$").isEqualTo("1")
  }

  @Test
  fun `when all of the incentive levels cannot be accommodated then move validation fails`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(permittedIncentiveLevels = mutableListOf(incentiveLevelEnhanced))
    val toSession = sessionTemplateEntityHelper.create(permittedIncentiveLevels = mutableListOf(incentiveLevelNonEnhanced))

    val visitDate = LocalDate.now().plusWeeks(1)
    visitEntityHelper.create(
      sessionTemplate = fromSession,
      visitStart = fromSession.startTime,
      visitEnd = fromSession.endTime,
      slotDate = visitDate,
    )
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").isEqualTo("New session template incentive levels cannot accommodate all incentive levels in existing session template.")
  }

  @Test
  fun `when move visits is fired only visits after the from date are moved to new session template`() {
    // Given
    val fromSession = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY)
    val toSession = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY, startTime = fromSession.startTime.minusMinutes(30), endTime = fromSession.endTime.minusMinutes(30))
    val futureVisitDate = LocalDate.now().plusDays(1)
    val futureVisit = visitEntityHelper.create(sessionTemplate = fromSession, visitStart = fromSession.startTime, visitEnd = fromSession.endTime)
    val pastVisitDate = LocalDate.now().minusDays(1)
    val pastVisit = visitEntityHelper.create(sessionTemplate = fromSession, visitStart = fromSession.startTime, visitEnd = fromSession.endTime)
    val moveRequest = MoveVisitsDto(fromSession.reference, toSession.reference, LocalDate.now())

    // When
    val responseSpec = callMoveVisits(webTestClient, moveRequest, setAuthorisation(roles = adminRole))
    val updatedFutureVisit = visitEntityHelper.getBookedVisit(futureVisit.reference)
    val pastVisitPostMove = visitEntityHelper.getVisit(pastVisit.reference)

    // Then
    responseSpec.expectStatus().isOk

    val responseBody = responseSpec.expectBody().returnResult().responseBody
    val updateCount = String(responseBody!!, StandardCharsets.UTF_8).toInt()

    Assertions.assertThat(updateCount).isEqualTo(1)
    Assertions.assertThat(updatedFutureVisit!!.sessionSlot.sessionTemplateReference).isEqualTo(toSession.reference)
    Assertions.assertThat(updatedFutureVisit.sessionSlot.slotTime).isEqualTo(toSession.startTime)
    Assertions.assertThat(updatedFutureVisit.sessionSlot.slotEndTime).isEqualTo(toSession.endTime)
    Assertions.assertThat(updatedFutureVisit.sessionSlot.slotDate).isEqualTo(futureVisitDate)

    // past visit should not be updated
    Assertions.assertThat(pastVisitPostMove!!.sessionSlot.sessionTemplateReference).isEqualTo(fromSession.reference)
    Assertions.assertThat(pastVisitPostMove.sessionSlot.slotTime).isEqualTo(fromSession.startTime)
    Assertions.assertThat(pastVisitPostMove.sessionSlot.slotEndTime).isEqualTo(fromSession.endTime)
    Assertions.assertThat(pastVisitPostMove.sessionSlot.slotDate).isEqualTo(pastVisitDate)
  }
}
