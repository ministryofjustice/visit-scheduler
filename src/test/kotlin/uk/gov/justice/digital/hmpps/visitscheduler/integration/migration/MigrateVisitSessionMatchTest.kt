package uk.gov.justice.digital.hmpps.visitscheduler.integration.migration

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.endsWith
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.UNKNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType.A_HIGH
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType.A_PROVISIONAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType.FEMALE_CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.utils.DEFAULT_MAX_PROX_MINUTES
import java.time.LocalDate
import java.time.LocalDateTime

@Transactional(propagation = SUPPORTS)
@DisplayName("Migrate POST /visits")
class MigrateVisitSessionMatchTest : MigrationIntegrationTestBase() {
  val enhancedIncentiveLevelGroupName = "ENH Incentive Level Group"
  val incentiveLevelList = listOf(
    IncentiveLevel.ENHANCED,
  )

  @BeforeEach
  internal fun setUp() {
    prisonEntityHelper.create(PRISON_CODE, true)
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS"))
  }

  @Test
  fun `Migrated session match - select session template with closest time `() {
    // Given

    val migrateVisitRequestDto = createMigrateVisitRequestDto()

    val validFromDate = migrateVisitRequestDto.startTimestamp.toLocalDate().minusDays(1)
    val startTime = migrateVisitRequestDto.startTimestamp.toLocalTime()
    val endTime = migrateVisitRequestDto.endTimestamp.toLocalTime()
    val dayOfWeek = migrateVisitRequestDto.startTimestamp.dayOfWeek

    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "1",
      startTime = startTime.minusMinutes(10),
      endTime = endTime,
    )

    val sessionTemplate = createSessionTemplateFrom(migrateVisitRequestDto, visitRoom = migrateVisitRequestDto.visitRoom + "2")

    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "3",
      startTime = startTime,
      endTime = endTime.plusMinutes(10),
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.sessionTemplateReference).isEqualTo(sessionTemplate.reference)
    }
  }

  @Test
  fun `Migrated session match - select session template with visit room match`() {
    // Given

    val migrateVisitRequestDto = createMigrateVisitRequestDto()

    val validFromDate = migrateVisitRequestDto.startTimestamp.toLocalDate().minusDays(1)
    val startTime = migrateVisitRequestDto.startTimestamp.toLocalTime()
    val endTime = migrateVisitRequestDto.endTimestamp.toLocalTime()
    val dayOfWeek = migrateVisitRequestDto.startTimestamp.dayOfWeek
    val enhancedIncentiveLevelGroup = sessionPrisonerIncentiveLevelHelper.create(enhancedIncentiveLevelGroupName, migrateVisitRequestDto.prisonCode, incentiveLevelList)

    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "1",
      startTime = startTime,
      endTime = endTime,
      permittedIncentiveLevels = mutableListOf(),
    )

    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom,
      startTime = startTime,
      endTime = endTime,
      permittedIncentiveLevels = mutableListOf(enhancedIncentiveLevelGroup),
    )

    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "3",
      startTime = startTime,
      endTime = endTime,
      permittedIncentiveLevels = mutableListOf(),
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.sessionTemplateReference).isEqualTo(sessionTemplate.reference)
    }
  }

  @Test
  fun `Migrated session match - select session template with enhanced match`() {
    // Given
    val migrateVisitRequestDto = createMigrateVisitRequestDto(incentiveLevelCode = "ENH")

    val validFromDate = migrateVisitRequestDto.startTimestamp.toLocalDate().minusDays(1)
    val startTime = migrateVisitRequestDto.startTimestamp.toLocalTime()
    val endTime = migrateVisitRequestDto.endTimestamp.toLocalTime()
    val dayOfWeek = migrateVisitRequestDto.startTimestamp.dayOfWeek
    val enhancedIncentiveLevelGroup = sessionPrisonerIncentiveLevelHelper.create(enhancedIncentiveLevelGroupName, migrateVisitRequestDto.prisonCode, incentiveLevelList)

    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "1",
      startTime = startTime,
      endTime = endTime,
      permittedIncentiveLevels = mutableListOf(),
    )

    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "2",
      startTime = startTime,
      endTime = endTime,
      permittedIncentiveLevels = mutableListOf(enhancedIncentiveLevelGroup),
    )

    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "3",
      startTime = startTime,
      endTime = endTime,
      permittedIncentiveLevels = mutableListOf(),
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.sessionTemplateReference).isEqualTo(sessionTemplate.reference)
    }
  }

  @Test
  fun `Migrated session match - select session template with closest category match`() {
    // Given

    val migrateVisitRequestDto = createMigrateVisitRequestDto(category = A_HIGH.code)

    val validFromDate = migrateVisitRequestDto.startTimestamp.toLocalDate().minusDays(1)
    val startTime = migrateVisitRequestDto.startTimestamp.toLocalTime()
    val endTime = migrateVisitRequestDto.endTimestamp.toLocalTime()
    val dayOfWeek = migrateVisitRequestDto.startTimestamp.dayOfWeek

    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "1",
      startTime = startTime,
      endTime = endTime,
    )

    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "3",
      startTime = startTime,
      endTime = endTime,
      permittedCategories = cratePermittedCategories("group A", migrateVisitRequestDto.prisonCode, A_HIGH, FEMALE_CLOSED),
    )

    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "4",
      startTime = startTime,
      endTime = endTime,
      permittedCategories = cratePermittedCategories("group B", migrateVisitRequestDto.prisonCode, A_PROVISIONAL),
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.sessionTemplateReference).isEqualTo(sessionTemplate.reference)
    }
  }

  @Test
  fun `Migrated session match - select session template with closest housing location`() {
    // Given

    val prisonCode = "BLI"
    val prisonerHousing = "$prisonCode-D-3-003"
    val migrateVisitRequestDto = createMigrateVisitRequestDto(prisonCode = prisonCode, housingLocations = prisonerHousing)
    val validFromDate = migrateVisitRequestDto.startTimestamp.toLocalDate().minusDays(1)
    val startTime = migrateVisitRequestDto.startTimestamp.toLocalTime()
    val endTime = migrateVisitRequestDto.endTimestamp.toLocalTime()
    val dayOfWeek = migrateVisitRequestDto.startTimestamp.dayOfWeek

    val locations1 = createSessionLocationGroup("$prisonCode-C-1-001")
    locations1.addAll(createSessionLocationGroup("$prisonCode-C-2-009"))
    locations1.addAll(createSessionLocationGroup("$prisonCode-C-3-001"))

    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "1",
      startTime = startTime,
      endTime = endTime,
      permittedLocationGroups = locations1,
    )

    val locations2 = createSessionLocationGroup("$prisonCode-D")
    locations2.addAll(createSessionLocationGroup("$prisonCode-F"))
    locations2.addAll(createSessionLocationGroup("$prisonCode-C-2-029"))

    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "2",
      startTime = startTime,
      endTime = endTime,
      permittedLocationGroups = locations2,
    )

    val locations3 = createSessionLocationGroup("$prisonCode-A-1-001")
    locations3.addAll(createSessionLocationGroup("$prisonCode-A-2-009"))
    locations3.addAll(createSessionLocationGroup("$prisonCode-A-3-001"))

    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "3",
      startTime = startTime,
      endTime = endTime,
      permittedLocationGroups = locations3,
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.sessionTemplateReference).isEqualTo(sessionTemplate.reference)
    }
  }

  @Test
  fun `Migrated session match - select session template with no housing locations when session templates dont have valid locations`() {
    // Given

    val prisonCode = "AWE"
    val prisonerHousing = "$prisonCode-B-1-2-3"
    val migrateVisitRequestDto = createMigrateVisitRequestDto(prisonCode = prisonCode, housingLocations = prisonerHousing)
    val validFromDate = migrateVisitRequestDto.startTimestamp.toLocalDate().minusDays(1)
    val startTime = migrateVisitRequestDto.startTimestamp.toLocalTime()
    val endTime = migrateVisitRequestDto.endTimestamp.toLocalTime()
    val dayOfWeek = migrateVisitRequestDto.startTimestamp.dayOfWeek

    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "1",
      startTime = startTime,
      endTime = endTime,
      permittedLocationGroups = createSessionLocationGroup("$prisonCode-A-1-2"),
    )

    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "2",
      startTime = startTime,
      endTime = endTime,
    )

    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "3",
      startTime = startTime,
      endTime = endTime,
      permittedLocationGroups = createSessionLocationGroup("$prisonCode-C-1-2"),
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.sessionTemplateReference).isEqualTo(sessionTemplate.reference)
    }
  }

  @Test
  fun `Migrated session match - select session template with closest housing location, category, proximity and enhanced match`() {
    // Given

    val prisonCode = "AWE"
    val prisonerHousing = "$prisonCode-B-1-2-3"
    val migrateVisitRequestDto = createMigrateVisitRequestDto(
      category = A_HIGH.code,
      prisonCode = prisonCode,
      housingLocations = prisonerHousing,
      incentiveLevelCode = "ENH",
    )

    val validFromDate = migrateVisitRequestDto.startTimestamp.toLocalDate().minusDays(1)
    val startTime = migrateVisitRequestDto.startTimestamp.toLocalTime()
    val endTime = migrateVisitRequestDto.endTimestamp.toLocalTime()
    val dayOfWeek = migrateVisitRequestDto.startTimestamp.dayOfWeek
    val enhancedIncentiveLevelGroup = sessionPrisonerIncentiveLevelHelper.create(enhancedIncentiveLevelGroupName, migrateVisitRequestDto.prisonCode, incentiveLevelList)

    // Enhanced - not enhanced
    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "1",
      startTime = startTime,
      endTime = endTime,
      permittedLocationGroups = createSessionLocationGroup("$prisonCode-B-1-2"),
      permittedCategories = cratePermittedCategories("group A", migrateVisitRequestDto.prisonCode, A_HIGH, FEMALE_CLOSED),
      permittedIncentiveLevels = mutableListOf(),
    )

    // Categories - wrong category
    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "2",
      startTime = startTime,
      endTime = endTime,
      permittedLocationGroups = createSessionLocationGroup("$prisonCode-B-1-2"),
      permittedCategories = cratePermittedCategories("group A", migrateVisitRequestDto.prisonCode, FEMALE_CLOSED),
      permittedIncentiveLevels = mutableListOf(enhancedIncentiveLevelGroup),
    )

    // Categories - no category
    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "3",
      startTime = startTime,
      endTime = endTime,
      permittedLocationGroups = createSessionLocationGroup("$prisonCode-B-1-2"),
      permittedIncentiveLevels = mutableListOf(enhancedIncentiveLevelGroup),
    )

    // Location - lower level location
    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "4",
      startTime = startTime,
      endTime = endTime,
      permittedLocationGroups = createSessionLocationGroup("$prisonCode-B"),
      permittedCategories = cratePermittedCategories("group A", migrateVisitRequestDto.prisonCode, A_HIGH, FEMALE_CLOSED),
      permittedIncentiveLevels = mutableListOf(enhancedIncentiveLevelGroup),
    )

    // Location - no location
    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "5",
      startTime = startTime,
      endTime = endTime,
      permittedCategories = cratePermittedCategories("group A", migrateVisitRequestDto.prisonCode, A_HIGH, FEMALE_CLOSED),
      permittedIncentiveLevels = mutableListOf(enhancedIncentiveLevelGroup),
    )

    // This is the session that should be selected - best match
    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "8",
      startTime = startTime,
      endTime = endTime,
      permittedLocationGroups = createSessionLocationGroup("$prisonCode-B-1-2"),
      permittedCategories = cratePermittedCategories("group A", migrateVisitRequestDto.prisonCode, A_HIGH, FEMALE_CLOSED),
      permittedIncentiveLevels = mutableListOf(enhancedIncentiveLevelGroup),
    )

    // Proximity - out
    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "6",
      startTime = startTime.plusMinutes(10),
      endTime = endTime.minusMinutes(10),
      permittedLocationGroups = createSessionLocationGroup("$prisonCode-B-1-2"),
      permittedCategories = cratePermittedCategories("group A", migrateVisitRequestDto.prisonCode, A_HIGH, FEMALE_CLOSED),
      permittedIncentiveLevels = mutableListOf(enhancedIncentiveLevelGroup),
    )

    // Proximity - in
    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "7",
      startTime = startTime.minusMinutes(10),
      endTime = endTime.plusMinutes(10),
      permittedLocationGroups = createSessionLocationGroup("$prisonCode-B-1-2"),
      permittedCategories = cratePermittedCategories("group A", migrateVisitRequestDto.prisonCode, A_HIGH, FEMALE_CLOSED),
      permittedIncentiveLevels = mutableListOf(enhancedIncentiveLevelGroup),
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.sessionTemplateReference).isEqualTo(sessionTemplate.reference)
    }
  }

  @Test
  fun `Migrated session match - select session template by room name when all other match elements are the same`() {
    // Given

    val prisonCode = "AWE"
    val prisonerHousing = "$prisonCode-B-1-2-3"
    val migrateVisitRequestDto = createMigrateVisitRequestDto(
      category = A_HIGH.code,
      prisonCode = prisonCode,
      housingLocations = prisonerHousing,
      incentiveLevelCode = "ENH",
    )

    val validFromDate = migrateVisitRequestDto.startTimestamp.toLocalDate().minusDays(1)
    val startTime = migrateVisitRequestDto.startTimestamp.toLocalTime()
    val endTime = migrateVisitRequestDto.endTimestamp.toLocalTime()
    val dayOfWeek = migrateVisitRequestDto.startTimestamp.dayOfWeek
    val enhancedIncentiveLevelGroup = sessionPrisonerIncentiveLevelHelper.create(enhancedIncentiveLevelGroupName, migrateVisitRequestDto.prisonCode, incentiveLevelList)

    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "1",
      startTime = startTime,
      endTime = endTime,
      permittedLocationGroups = createSessionLocationGroup("$prisonCode-B-1-2"),
      permittedCategories = cratePermittedCategories("group A", migrateVisitRequestDto.prisonCode, A_HIGH, FEMALE_CLOSED),
      permittedIncentiveLevels = mutableListOf(enhancedIncentiveLevelGroup),
    )

    // This is the session that should be selected - best match by room name
    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom,
      startTime = startTime,
      endTime = endTime,
      permittedLocationGroups = createSessionLocationGroup("$prisonCode-B-1-2"),
      permittedCategories = cratePermittedCategories("group A", migrateVisitRequestDto.prisonCode, A_HIGH, FEMALE_CLOSED),
      permittedIncentiveLevels = mutableListOf(enhancedIncentiveLevelGroup),

    )

    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom + "2",
      startTime = startTime,
      endTime = endTime,
      permittedLocationGroups = createSessionLocationGroup("$prisonCode-B-1-2"),
      permittedCategories = cratePermittedCategories("group A", migrateVisitRequestDto.prisonCode, A_HIGH, FEMALE_CLOSED),
      permittedIncentiveLevels = mutableListOf(enhancedIncentiveLevelGroup),
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.sessionTemplateReference).isEqualTo(sessionTemplate.reference)
    }
  }

  @Test
  fun `Migrated session match - has null sessionTemplateReference if visit is in the past`() {
    // Given

    val visitStartTimeAndDate = LocalDateTime.now().minusDays(95)

    val migrateVisitRequestDto = createMigrateVisitRequestDto(
      visitStartTimeAndDate = visitStartTimeAndDate,
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.sessionTemplateReference).isNull()
    }
  }

  @Test
  fun `Migrated session match - can only find sessionTemplate out side max proximity on start time exception is thrown`() {
    // Given

    val migrateVisitRequestDto = createMigrateVisitRequestDto(visitRoom = "theGreatHall")

    createSessionTemplateFrom(
      migrateVisitRequestDto,
      "theSmallHall",
      startTime = migrateVisitRequestDto.startTimestamp.toLocalTime().plusMinutes(DEFAULT_MAX_PROX_MINUTES.toLong() + 1),
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").isEqualTo("Migration failure: could not find matching session template")
      .jsonPath("$.developerMessage").value(startsWith("Could not find any SessionTemplate :"))
      .jsonPath("$.developerMessage").value(endsWith("Not a valid proximity!"))
  }

  @Test
  fun `Migrated session match - can only find sessionTemplate out side max proximity on end time exception is thrown`() {
    // Given

    val migrateVisitRequestDto = createMigrateVisitRequestDto(visitRoom = "theGreatHall")

    createSessionTemplateFrom(
      migrateVisitRequestDto,
      "theSmallHall",
      startTime = migrateVisitRequestDto.endTimestamp.toLocalTime().plusMinutes(DEFAULT_MAX_PROX_MINUTES.toLong() + 1),
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").isEqualTo("Migration failure: could not find matching session template")
      .jsonPath("$.developerMessage").value(startsWith("Could not find any SessionTemplate :"))
      .jsonPath("$.developerMessage").value(endsWith("Not a valid proximity!"))
  }

  @Test
  fun `Migrated session match - can only find sessionTemplate out side max proximity on start-end time exception is thrown`() {
    // Given

    val justOverMax = (DEFAULT_MAX_PROX_MINUTES.toLong() / 2) + 1
    val migrateVisitRequestDto = createMigrateVisitRequestDto(visitRoom = "theGreatHall")

    createSessionTemplateFrom(
      migrateVisitRequestDto,
      "theSmallHall",
      startTime = migrateVisitRequestDto.endTimestamp.toLocalTime().plusMinutes(justOverMax),
      endTime = migrateVisitRequestDto.startTimestamp.toLocalTime().plusMinutes(justOverMax),
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").isEqualTo("Migration failure: could not find matching session template")
      .jsonPath("$.developerMessage").value(startsWith("Could not find any SessionTemplate :"))
      .jsonPath("$.developerMessage").value(endsWith("Not a valid proximity!"))
  }

  @Test
  fun `Migrated session match - can not find sessionTemplate exception is thrown`() {
    // Given

    val migrateVisitRequestDto = createMigrateVisitRequestDto(visitRoom = "theGreatHall")

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").isEqualTo("Migration failure: could not find matching session template")
      .jsonPath("$.developerMessage").value(startsWith("Could not find any SessionTemplate :"))
      .jsonPath("$.developerMessage").value(endsWith("No session templates!"))
  }

  @Test
  fun `Migrated session match - When migrated visit is open and no open templates available exception is thrown`() {
    // Given

    val migrateVisitRequestDto = createMigrateVisitRequestDto(visitRestriction = OPEN)

    val validFromDate = migrateVisitRequestDto.startTimestamp.toLocalDate().minusDays(1)
    val startTime = migrateVisitRequestDto.startTimestamp.toLocalTime()
    val endTime = migrateVisitRequestDto.endTimestamp.toLocalTime()
    val dayOfWeek = migrateVisitRequestDto.startTimestamp.dayOfWeek

    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom,
      startTime = startTime,
      endTime = endTime,
      closedCapacity = 10,
      openCapacity = 0,
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").isEqualTo("Migration failure: could not find matching session template")
      .jsonPath("$.developerMessage").value(startsWith("Could not find any SessionTemplate :"))
      .jsonPath("$.developerMessage").value(endsWith("No session templates!"))
  }

  @Test
  fun `Migrated session match - When migrated visit is closed and no closed templates available exception is thrown`() {
    // Given

    val migrateVisitRequestDto = createMigrateVisitRequestDto(visitRestriction = CLOSED)

    val validFromDate = migrateVisitRequestDto.startTimestamp.toLocalDate().minusDays(1)
    val startTime = migrateVisitRequestDto.startTimestamp.toLocalTime()
    val endTime = migrateVisitRequestDto.endTimestamp.toLocalTime()
    val dayOfWeek = migrateVisitRequestDto.startTimestamp.dayOfWeek

    sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom,
      startTime = startTime,
      endTime = endTime,
      closedCapacity = 0,
      openCapacity = 10,
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").isEqualTo("Migration failure: could not find matching session template")
      .jsonPath("$.developerMessage").value(startsWith("Could not find any SessionTemplate :"))
      .jsonPath("$.developerMessage").value(endsWith("No session templates!"))
  }

  @Test
  fun `Migrated session match - When migrated visit is Unknown and no closed or open seesion then no error`() {
    // Given

    val migrateVisitRequestDto = createMigrateVisitRequestDto(visitRestriction = UNKNOWN)

    val validFromDate = migrateVisitRequestDto.startTimestamp.toLocalDate().minusDays(1)
    val startTime = migrateVisitRequestDto.startTimestamp.toLocalTime()
    val endTime = migrateVisitRequestDto.endTimestamp.toLocalTime()
    val dayOfWeek = migrateVisitRequestDto.startTimestamp.dayOfWeek

    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = validFromDate,
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom,
      startTime = startTime,
      endTime = endTime,
      closedCapacity = 0,
      openCapacity = 0,
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    val reference = getReference(responseSpec)

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.sessionTemplateReference).isEqualTo(sessionTemplate.reference)
    }
  }

  @Test
  fun `Migrated session match - Using validFromDate and ValidToDate on session template`() {
    // Given
    val migrateVisitRequestDto = createMigrateVisitRequestDto(
      visitRestriction = OPEN,
      visitStartTimeAndDate = LocalDateTime.now().plusDays(10).withHour(10).withMinute(30),
    )

    val startTime = migrateVisitRequestDto.startTimestamp.toLocalTime()
    val endTime = migrateVisitRequestDto.endTimestamp.toLocalTime()
    val dayOfWeek = migrateVisitRequestDto.startTimestamp.dayOfWeek

    sessionTemplateEntityHelper.create(
      validFromDate = LocalDate.now().minusDays(10),
      validToDate = LocalDate.now().minusDays(1),
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom,
      startTime = startTime,
      endTime = endTime,
    )

    sessionTemplateEntityHelper.create(
      validFromDate = LocalDate.now().plusDays(8),
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom,
      startTime = startTime,
      endTime = endTime,
    )

    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = LocalDate.now().plusDays(9),
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom,
      startTime = startTime,
      endTime = endTime,
    )

    sessionTemplateEntityHelper.create(
      validFromDate = LocalDate.now().plusDays(7),
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom,
      startTime = startTime,
      endTime = endTime,
    )

    sessionTemplateEntityHelper.create(
      validFromDate = LocalDate.now().plusDays(11),
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom,
      startTime = startTime,
      endTime = endTime,
    )

    sessionTemplateEntityHelper.create(
      validFromDate = LocalDate.now(),
      validToDate = LocalDate.now().plusDays(9),
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = migrateVisitRequestDto.visitRoom,
      startTime = startTime,
      endTime = endTime,
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.sessionTemplateReference).isEqualTo(sessionTemplate.reference)
    }
  }
}
