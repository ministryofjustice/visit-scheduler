package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_SESSION_SCHEDULE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionScheduleDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Get $GET_SESSION_SCHEDULE")
class GetSessionScheduleTest : IntegrationTestBase() {

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  private val prisonCode = "MDI"

  @BeforeEach
  internal fun setUpTests() {
  }

  @Test
  fun `Session schedule is returned for a prison`() {
    // Given
    val sessionDate = LocalDate.now()

    val sessionLocationGroup = sessionLocationGroupHelper.create(prisonCode = prisonCode)
    val sessionCategoryGroup = sessionPrisonerCategoryHelper.create(prisonCode = prisonCode)

    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = sessionDate,
      validToDate = sessionDate.plusDays(7),
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = sessionDate.dayOfWeek,
      permittedLocationGroups = mutableListOf(sessionLocationGroup),
      permittedCategories = mutableListOf(sessionCategoryGroup),
    )

    // When
    val responseSpec = callGetSessionSchedule(prisonCode, sessionDate)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionScheduleResults = getResults(returnResult)
    Assertions.assertThat(sessionScheduleResults.size).isEqualTo(1)
    Assertions.assertThat(sessionScheduleResults[0].sessionTimeSlot.startTime).isEqualTo(sessionTemplate.startTime)
    Assertions.assertThat(sessionScheduleResults[0].sessionTimeSlot.endTime).isEqualTo(sessionTemplate.endTime)
    Assertions.assertThat(sessionScheduleResults[0].sessionTemplateReference).isEqualTo(sessionTemplate.reference)
    Assertions.assertThat(sessionScheduleResults[0].weeklyFrequency).isEqualTo(1)
    Assertions.assertThat(sessionScheduleResults[0].sessionDateRange.validToDate).isEqualTo(sessionTemplate.validToDate)
    Assertions.assertThat(sessionScheduleResults[0].capacity.open).isEqualTo(sessionTemplate.openCapacity)
    Assertions.assertThat(sessionScheduleResults[0].capacity.closed).isEqualTo(sessionTemplate.closedCapacity)
    Assertions.assertThat(sessionScheduleResults[0].prisonerLocationGroupNames[0]).isEqualTo(sessionLocationGroup.name)
    Assertions.assertThat(sessionScheduleResults[0].prisonerCategoryGroupNames[0]).isEqualTo(sessionCategoryGroup.name)
    Assertions.assertThat(sessionScheduleResults[0].areLocationGroupsIncluded).isTrue()
  }

  @Test
  fun `All session schedules are returned for a prison for that day in time order`() {
    // Given
    val sessionDate = LocalDate.now()

    val sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = sessionDate,
      validToDate = sessionDate.plusDays(7),
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = sessionDate.dayOfWeek,
      includeLocationGroupType = false,
    )

    val sessionTemplate2 = sessionTemplateEntityHelper.create(
      validFromDate = sessionDate,
      validToDate = sessionDate.plusDays(7),
      startTime = LocalTime.parse("11:00"),
      endTime = LocalTime.parse("12:00"),
      dayOfWeek = sessionDate.dayOfWeek,
    )

    val sessionTemplate3 = sessionTemplateEntityHelper.create(
      validFromDate = sessionDate,
      validToDate = sessionDate.plusDays(7),
      startTime = LocalTime.parse("08:00"),
      endTime = LocalTime.parse("09:00"),
      dayOfWeek = sessionDate.dayOfWeek,
    )

    val sessionTemplate4 = sessionTemplateEntityHelper.create(
      validFromDate = sessionDate,
      validToDate = sessionDate.plusDays(7),
      startTime = LocalTime.parse("08:00"),
      endTime = LocalTime.parse("08:30"),
      dayOfWeek = sessionDate.dayOfWeek,
    )

    // When
    val responseSpec = callGetSessionSchedule(prisonCode, sessionDate)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionScheduleResults = getResults(returnResult)
    Assertions.assertThat(sessionScheduleResults.size).isEqualTo(4)
    Assertions.assertThat(sessionScheduleResults[0].sessionTimeSlot.startTime).isEqualTo(sessionTemplate4.startTime)
    Assertions.assertThat(sessionScheduleResults[0].sessionTimeSlot.endTime).isEqualTo(sessionTemplate4.endTime)
    Assertions.assertThat(sessionScheduleResults[0].areLocationGroupsIncluded).isTrue()
    Assertions.assertThat(sessionScheduleResults[1].sessionTimeSlot.startTime).isEqualTo(sessionTemplate3.startTime)
    Assertions.assertThat(sessionScheduleResults[1].sessionTimeSlot.endTime).isEqualTo(sessionTemplate3.endTime)
    Assertions.assertThat(sessionScheduleResults[1].areLocationGroupsIncluded).isTrue()
    Assertions.assertThat(sessionScheduleResults[2].sessionTimeSlot.startTime).isEqualTo(sessionTemplate1.startTime)
    Assertions.assertThat(sessionScheduleResults[2].sessionTimeSlot.endTime).isEqualTo(sessionTemplate1.endTime)
    Assertions.assertThat(sessionScheduleResults[2].areLocationGroupsIncluded).isFalse()
    Assertions.assertThat(sessionScheduleResults[3].sessionTimeSlot.startTime).isEqualTo(sessionTemplate2.startTime)
    Assertions.assertThat(sessionScheduleResults[3].sessionTimeSlot.endTime).isEqualTo(sessionTemplate2.endTime)
    Assertions.assertThat(sessionScheduleResults[3].areLocationGroupsIncluded).isTrue()
  }

  @Test
  fun `only active session schedules are returned for a prison`() {
    // Given
    val sessionDate = LocalDate.now()

    // active session 1
    sessionTemplateEntityHelper.create(
      validFromDate = sessionDate,
      validToDate = sessionDate.plusDays(7),
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = sessionDate.dayOfWeek,
    )

    // active session 2
    sessionTemplateEntityHelper.create(
      validFromDate = sessionDate,
      validToDate = sessionDate.plusDays(7),
      startTime = LocalTime.parse("11:00"),
      endTime = LocalTime.parse("12:00"),
      dayOfWeek = sessionDate.dayOfWeek,
    )

    // inactive session
    val inactiveSession = sessionTemplateEntityHelper.create(
      validFromDate = sessionDate,
      validToDate = sessionDate.plusDays(7),
      startTime = LocalTime.parse("14:00"),
      endTime = LocalTime.parse("15:00"),
      dayOfWeek = sessionDate.dayOfWeek,
      isActive = false,
    )

    // When
    val responseSpec = callGetSessionSchedule(prisonCode, sessionDate)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionScheduleResults = getResults(returnResult)
    val sessionScheduleReferences = sessionScheduleResults.map { it.sessionTemplateReference }.toList()
    Assertions.assertThat(sessionScheduleResults.size).isEqualTo(2)
    Assertions.assertThat(sessionScheduleReferences).doesNotContain(inactiveSession.reference)
  }

  @Test
  fun `Session schedule is not returned when date is excluded`() {
    // Given
    val sessionDate = LocalDate.now()

    prisonEntityHelper.create(prisonCode, true, listOf(sessionDate))

    sessionTemplateEntityHelper.create(
      validFromDate = sessionDate,
      validToDate = sessionDate.plusDays(7),
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = sessionDate.dayOfWeek,
      prisonCode = prisonCode,
    )

    // When
    val responseSpec = callGetSessionSchedule(prisonCode, sessionDate)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionScheduleResults = getResults(returnResult)
    Assertions.assertThat(sessionScheduleResults.size).isEqualTo(0)
  }

  @Test
  fun `When no schedules none are returned`() {
    // Given
    val sessionDate = LocalDate.now()
    prisonEntityHelper.create(prisonCode, true)

    // When
    val responseSpec = callGetSessionSchedule(prisonCode, sessionDate)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val sessionScheduleResults = getResults(returnResult)
    Assertions.assertThat(sessionScheduleResults.size).isEqualTo(0)
  }

  @Test
  fun `weeklyFrequency session schedule are returned for a prison`() {
    // Given
    val sessionDate = LocalDate.now()

    sessionTemplateEntityHelper.create(
      validFromDate = sessionDate,
      validToDate = sessionDate.plusMonths(4),
      dayOfWeek = sessionDate.dayOfWeek,
      weeklyFrequency = 2,
    )
    // When
    val responseSpec = callGetSessionSchedule(prisonCode, sessionDate)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionScheduleResults = getResults(returnResult)
    Assertions.assertThat(sessionScheduleResults.size).isEqualTo(1)
    Assertions.assertThat(sessionScheduleResults[0].weeklyFrequency).isEqualTo(2)
  }

  @Test
  fun `weeklyFrequency session schedule are not returned for a prison when weeklyFrequency not for this week`() {
    // Given
    val sessionDate = LocalDate.now()

    sessionTemplateEntityHelper.create(
      validFromDate = sessionDate.minusWeeks(1),
      validToDate = sessionDate.plusMonths(4),
      dayOfWeek = sessionDate.dayOfWeek,
      weeklyFrequency = 2,
    )
    // When
    val responseSpec = callGetSessionSchedule(prisonCode, sessionDate)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionScheduleResults = getResults(returnResult)
    Assertions.assertThat(sessionScheduleResults.size).isEqualTo(0)
  }

  @Test
  fun `Session schedule is returned for a prison, with many location groups`() {
    // Given
    val sessionDate = LocalDate.now()

    val sessionLocationGroup1 = sessionLocationGroupHelper.create(prisonCode = prisonCode)
    val sessionLocationGroup2 = sessionLocationGroupHelper.create(name = "group B", prisonCode = prisonCode)

    sessionTemplateEntityHelper.create(
      validFromDate = sessionDate,
      validToDate = sessionDate.plusDays(7),
      dayOfWeek = sessionDate.dayOfWeek,
      permittedLocationGroups = mutableListOf(sessionLocationGroup1, sessionLocationGroup2),
    )

    // When
    val responseSpec = callGetSessionSchedule(prisonCode, sessionDate)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionScheduleResults = getResults(returnResult)
    Assertions.assertThat(sessionScheduleResults.size).isEqualTo(1)
    Assertions.assertThat(sessionScheduleResults[0].prisonerLocationGroupNames[0]).isEqualTo(sessionLocationGroup1.name)
    Assertions.assertThat(sessionScheduleResults[0].prisonerLocationGroupNames[1]).isEqualTo(sessionLocationGroup2.name)
  }

  @Test
  fun `Session schedule is returned for a prison, with many category groups`() {
    // Given
    val sessionDate = LocalDate.now()

    val sessionCategoryGroup1 = sessionPrisonerCategoryHelper.create(name = "Category A prisoners", prisonCode = prisonCode)
    val sessionCategoryGroup2 = sessionPrisonerCategoryHelper.create(name = "Vulnerable Categories", prisonCode = prisonCode)

    sessionTemplateEntityHelper.create(
      validFromDate = sessionDate,
      validToDate = sessionDate.plusDays(7),
      dayOfWeek = sessionDate.dayOfWeek,
      permittedCategories = mutableListOf(sessionCategoryGroup1, sessionCategoryGroup2),
    )

    // When
    val responseSpec = callGetSessionSchedule(prisonCode, sessionDate)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionScheduleResults = getResults(returnResult)
    Assertions.assertThat(sessionScheduleResults.size).isEqualTo(1)
    Assertions.assertThat(sessionScheduleResults[0].prisonerCategoryGroupNames[0]).isEqualTo(sessionCategoryGroup1.name)
    Assertions.assertThat(sessionScheduleResults[0].prisonerCategoryGroupNames[1]).isEqualTo(sessionCategoryGroup2.name)
  }

  @Test
  fun `Session schedule is returned for a prison, with multiple incentive level groups`() {
    // Given
    val sessionDate = LocalDate.now()

    val sessionIncentiveLevelGroup1 = sessionPrisonerIncentiveLevelHelper.create(name = "Enhanced Incentive prisoners", prisonCode = prisonCode, incentiveLevelList = mutableListOf(IncentiveLevel.ENHANCED, IncentiveLevel.ENHANCED_2))
    val sessionIncentiveLevelGroup2 = sessionPrisonerIncentiveLevelHelper.create(name = "Basic Incentive prisoners", prisonCode = prisonCode, incentiveLevelList = mutableListOf(IncentiveLevel.BASIC))

    sessionTemplateEntityHelper.create(
      validFromDate = sessionDate,
      validToDate = sessionDate.plusDays(7),
      dayOfWeek = sessionDate.dayOfWeek,
      permittedIncentiveLevels = mutableListOf(sessionIncentiveLevelGroup1, sessionIncentiveLevelGroup2),
    )

    // When
    val responseSpec = callGetSessionSchedule(prisonCode, sessionDate)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionScheduleResults = getResults(returnResult)
    Assertions.assertThat(sessionScheduleResults.size).isEqualTo(1)
    Assertions.assertThat(sessionScheduleResults[0].prisonerIncentiveLevelGroupNames[0]).isEqualTo(sessionIncentiveLevelGroup1.name)
    Assertions.assertThat(sessionScheduleResults[0].prisonerIncentiveLevelGroupNames[1]).isEqualTo(sessionIncentiveLevelGroup2.name)
  }

  private fun callGetSessionSchedule(
    prisonCode: String = "MDI",
    scheduleDate: LocalDate,
  ): ResponseSpec {
    return webTestClient.get().uri("$GET_SESSION_SCHEDULE?prisonId=$prisonCode&date=$scheduleDate")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()
  }

  private fun getResults(returnResult: BodyContentSpec): Array<SessionScheduleDto> {
    return objectMapper.readValue(returnResult.returnResult().responseBody, Array<SessionScheduleDto>::class.java)
  }
}
