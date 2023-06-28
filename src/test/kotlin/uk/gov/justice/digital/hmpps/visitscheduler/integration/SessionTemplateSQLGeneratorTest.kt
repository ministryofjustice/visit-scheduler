package uk.gov.justice.digital.hmpps.visitscheduler.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.GroupType.LOCATION
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.GroupType.PRISONER_CATEGORY
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.GroupType.PRISONER_INCENTIVE_LEVEL
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.LocationGroupsColumns
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.PrisonerCategoryGroupsColumns
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.PrisonerIncentiveLevelGroupsColumns
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionItem
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionLocationItem
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionPrisonerCategoryItem
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionPrisonerIncentiveItem
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Session Template SQL Generator Test")
class SessionTemplateSQLGeneratorTest {
  private lateinit var sessionTemplateSQLGenerator: SessionTemplateSQLGenerator

  @BeforeEach
  fun beforeTest() {
    sessionTemplateSQLGenerator = SessionTemplateSQLGenerator()
  }

  @Test
  fun `Data parsed - session template is parsed correctly`() {
    // Given
    val path = "src/test/resources/session-template-data/"
    val sessionDataFile = File(path, "session-data_count_4.csv")

    // When
    val sessionRecords = sessionTemplateSQLGenerator.getSessionRecords(sessionDataFile)

    // Then
    assertThat(sessionRecords.size).isEqualTo(4)
    with(sessionRecords[0]) {
      assertThat(prisonCode).isEqualTo("BL1")
      assertThat(visitRoom).isEqualTo("Room 1")
      assertThat(type).isEqualTo(SOCIAL)
      assertThat(open).isEqualTo(10)
      assertThat(closed).isEqualTo(1)
      assertThat(startTime).isEqualTo(LocalTime.parse("06:00"))
      assertThat(endTime).isEqualTo(LocalTime.parse("12:00"))
      assertThat(startDate).isEqualTo(LocalDate.parse("2022-11-16"))
      assertThat(endDate).isNull()
      assertThat(dayOfWeek).isEqualTo(DayOfWeek.TUESDAY)
      assertThat(weeklyFrequency).isEqualTo(1)
      assertThat(locationKeys).isEqualTo("BLI_G1")
      assertThat(categoryKeys).isEqualTo("INC_CAT")
      assertThat(incentiveLevelKeys).isEqualTo("INCENTIVE_LEVEL_1")
      assertThat(active).isTrue
    }
    with(sessionRecords[1]) {
      assertThat(prisonCode).isEqualTo("BL2")
      assertThat(visitRoom).isEqualTo("Room 2")
      assertThat(type).isEqualTo(SOCIAL)
      assertThat(open).isEqualTo(20)
      assertThat(closed).isEqualTo(2)
      assertThat(startTime).isEqualTo(LocalTime.parse("07:00"))
      assertThat(endTime).isEqualTo(LocalTime.parse("13:00"))
      assertThat(startDate).isEqualTo(LocalDate.parse("2022-11-17"))
      assertThat(endDate).isEqualTo(LocalDate.parse("2022-12-17"))
      assertThat(dayOfWeek).isEqualTo(DayOfWeek.WEDNESDAY)
      assertThat(weeklyFrequency).isEqualTo(11)
      assertThat(locationKeys).isEqualTo("BLI_G2")
      assertThat(categoryKeys).isEqualTo("INC_CAT")
      assertThat(incentiveLevelKeys).isNull()
      assertThat(active).isFalse
    }
  }

  @Test
  fun `Data parsed - session template - weekly frequency is invalid`() {
    // Given
    val path = "src/test/resources/session-template-data/"
    val sessionDataFile = File(path, "session-data_invalid_weekly_frequency.csv")

    // When
    val exception = assertThrows(IllegalArgumentException::class.java) {
      sessionTemplateSQLGenerator.getSessionRecords(sessionDataFile)
    }

    // Then
    assertThat(exception.message).startsWith("Session Template : weeklyFrequency(0) should be equal to or more than 1")
  }

  @Test
  fun `Data parsed - session template - open capacity is greater than max capacity`() {
    // Given
    val path = "src/test/resources/session-template-data/"
    val sessionDataFile = File(path, "session-data_invalid_open_capacity.csv")

    // When
    val exception = assertThrows(IllegalArgumentException::class.java) {
      sessionTemplateSQLGenerator.getSessionRecords(sessionDataFile)
    }

    // Then
    assertThat(exception.message).startsWith("Session Template : open(201) or close(1) capacity seems a little high for (prison:BL1 ")
  }

  @Test
  fun `Data parsed - session template - closed capacity is greater than max capacity`() {
    // Given
    val path = "src/test/resources/session-template-data/"
    val sessionDataFile = File(path, "session-data_invalid_closed_capacity.csv")

    // When
    val exception = assertThrows(IllegalArgumentException::class.java) {
      sessionTemplateSQLGenerator.getSessionRecords(sessionDataFile)
    }

    // Then
    assertThat(exception.message).startsWith("Session Template : open(1) or close(201) capacity seems a little high for (prison:BL1 ")
  }

  @Test
  fun `Data parsed - session template - open capacity is less than 0`() {
    // Given
    val path = "src/test/resources/session-template-data/"
    val sessionDataFile = File(path, "session-data_less_than_0_open_capacity.csv")

    // When
    val exception = assertThrows(IllegalArgumentException::class.java) {
      sessionTemplateSQLGenerator.getSessionRecords(sessionDataFile)
    }

    // Then
    assertThat(exception.message).startsWith("Session Template : open(-3) or close(1) capacity be cant be less than zero for (prison:BL1 ")
  }

  @Test
  fun `Data parsed - session template - closed capacity is less than 0`() {
    // Given
    val path = "src/test/resources/session-template-data/"
    val sessionDataFile = File(path, "session-data_less_than_0_closed_capacity.csv")

    // When
    val exception = assertThrows(IllegalArgumentException::class.java) {
      sessionTemplateSQLGenerator.getSessionRecords(sessionDataFile)
    }

    // Then
    assertThat(exception.message).startsWith("Session Template : open(25) or close(-1) capacity be cant be less than zero for (prison:BL1 ")
  }

  @Test
  fun `Data parsed - session location is parsed correctly`() {
    // Given
    val path = "src/test/resources/session-template-data/"
    val sessionLocationDataFile = File(path, "session-location-data_count_12.csv")

    // When

    val prisonTemplateRecords = sessionTemplateSQLGenerator.getSessionGroupColumns(LOCATION, sessionLocationDataFile)
    val sessionLocationItems = sessionTemplateSQLGenerator.getSessionItems(prisonTemplateRecords)

    // Then
    assertThat(prisonTemplateRecords.size).isEqualTo(8)
    assertThat(prisonTemplateRecords[0].key).isEqualTo("BLI_G1")
    assertThat(prisonTemplateRecords[0].prisonCode).isEqualTo("BL1")
    assertThat(prisonTemplateRecords[0].name).isEqualTo("Test G 1")
    assertThat(prisonTemplateRecords[1].key).isEqualTo("BLI_G2")
    assertThat(prisonTemplateRecords[1].prisonCode).isEqualTo("BL2")
    assertThat(prisonTemplateRecords[1].name).isEqualTo("Test G 2")
    assertThat(prisonTemplateRecords[2].key).isEqualTo("BLI_G3")
    assertThat(prisonTemplateRecords[2].prisonCode).isEqualTo("BL3")
    assertThat(prisonTemplateRecords[2].name).isEqualTo("Test G 3")
    assertThat(prisonTemplateRecords[3].key).isEqualTo("BLI_G4")
    assertThat(prisonTemplateRecords[3].prisonCode).isEqualTo("BL4")
    assertThat(prisonTemplateRecords[3].name).isEqualTo("Test G 4")
    assertThat(prisonTemplateRecords[4].key).isEqualTo("BLI_G5")
    assertThat(prisonTemplateRecords[4].prisonCode).isEqualTo("BL5")
    assertThat(prisonTemplateRecords[4].name).isEqualTo("Test G 5")
    assertThat(prisonTemplateRecords[5].key).isEqualTo("BLI_G6")
    assertThat(prisonTemplateRecords[5].prisonCode).isEqualTo("BL6")
    assertThat(prisonTemplateRecords[5].name).isEqualTo("Test G 6")
    assertThat(prisonTemplateRecords[6].key).isEqualTo("BLI_G7")
    assertThat(prisonTemplateRecords[6].prisonCode).isEqualTo("BL7")
    assertThat(prisonTemplateRecords[6].name).isEqualTo("Test G 7")
    assertThat(prisonTemplateRecords[7].key).isEqualTo("BLI_G8")
    assertThat(prisonTemplateRecords[7].prisonCode).isEqualTo("BL8")
    assertThat(prisonTemplateRecords[7].name).isEqualTo("Test G 8")

    assertThat(sessionLocationItems.size).isEqualTo(12)
    assertSessionLocation(sessionLocationItems[0], "BL1", "BLI_G1", "A")
    assertSessionLocation(sessionLocationItems[1], "BL2", "BLI_G2", "C", "1")
    assertSessionLocation(sessionLocationItems[2], "BL3", "BLI_G3", "C", "1", "001")
    assertSessionLocation(sessionLocationItems[3], "BL4", "BLI_G4", "S", "C", "2", "002")
    assertSessionLocation(sessionLocationItems[4], "BL5", "BLI_G5", "B")
    assertSessionLocation(sessionLocationItems[5], "BL5", "BLI_G5", "G")
    assertSessionLocation(sessionLocationItems[6], "BL6", "BLI_G6", "C", "1")
    assertSessionLocation(sessionLocationItems[7], "BL6", "BLI_G6", "C", "2")
    assertSessionLocation(sessionLocationItems[8], "BL7", "BLI_G7", "C", "1", "003")
    assertSessionLocation(sessionLocationItems[9], "BL7", "BLI_G7", "C", "1", "004")
    assertSessionLocation(sessionLocationItems[10], "BL8", "BLI_G8", "S", "C", "2", "005")
    assertSessionLocation(sessionLocationItems[11], "BL8", "BLI_G8", "S", "C", "2", "006")
  }

  @Test
  fun `Data parsed - session location lower case data is converted to correct case when required`() {
    // Given
    val path = "src/test/resources/session-template-data/"
    val sessionLocationDataFile = File(path, "session-location-data_lower_case.csv")

    // When
    val prisonTemplateRecords = sessionTemplateSQLGenerator.getSessionGroupColumns(LOCATION, sessionLocationDataFile)
    val sessionLocationItems = sessionTemplateSQLGenerator.getSessionItems(prisonTemplateRecords)

    // Then
    assertThat(sessionLocationItems.size).isEqualTo(1)
    assertSessionLocation(sessionLocationItems[0], "BL8", "BLI_G8", "S", "C", "2", "005")
  }

  @Test
  fun `Data parsed - session template lower case data is converted to correct case when required`() {
    // Given
    val path = "src/test/resources/session-template-data/"
    val sessionDataFile = File(path, "session-data_lower_case.csv")

    // When
    val sessionRecords = sessionTemplateSQLGenerator.getSessionRecords(sessionDataFile)

    // Then
    assertThat(sessionRecords.size).isEqualTo(1)
    with(sessionRecords[0]) {
      assertThat(prisonCode).isEqualTo("BL1")
      assertThat(visitRoom).isEqualTo("room 1")
      assertThat(type).isEqualTo(SOCIAL)
      assertThat(dayOfWeek).isEqualTo(DayOfWeek.TUESDAY)
      assertThat(locationKeys).isEqualTo("BLI_G1")
      assertThat(weeklyFrequency).isEqualTo(1)
      assertThat(categoryKeys).isEqualTo("INC_CAT")
      assertThat(active).isFalse
    }
  }

  @Test
  fun `Data parsed - session template levelOne validated correctly`() {
    // Given
    val sessionLocationList = mutableListOf<LocationGroupsColumns>()

    val locationGroupsColumns = LocationGroupsColumns(prisonCode = "prison1", key = "key1", levelOne = listOf())
    sessionLocationList.add(locationGroupsColumns)

    // When
    val exception = assertThrows(IllegalArgumentException::class.java) {
      sessionTemplateSQLGenerator.validateGroupColumns(sessionLocationList)
    }

    // Then
    assertThat(exception.message).isEqualTo("Location : must have at least one level one element (prison:prison1 key:key1)!")
  }

  @Test
  fun `Data parsed - session template levelTwo cant have two parents validated correctly`() {
    // Given
    val sessionLocationList = mutableListOf<LocationGroupsColumns>()

    val locationGroupsColumns = LocationGroupsColumns(
      key = "key1",
      prisonCode = "prison1",
      levelOne = listOf("one", "one"),
      levelTwo = listOf("child"),
    )
    sessionLocationList.add(locationGroupsColumns)

    // When
    val exception = assertThrows(IllegalArgumentException::class.java) {
      sessionTemplateSQLGenerator.validateGroupColumns(sessionLocationList)
    }

    // Then
    assertThat(exception.message).isEqualTo("Location : Child can't have more than one parent (prison:prison1 key:key1)!")
  }

  @Test
  fun `Data parsed - session template levelThree cant have to parents validated correctly`() {
    // Given
    val sessionLocationList = mutableListOf<LocationGroupsColumns>()

    val locationGroupsColumns = LocationGroupsColumns(
      key = "key1",
      prisonCode = "prison1",
      levelOne = listOf("one"),
      levelTwo = listOf("two", "two"),
      levelThree = listOf("three"),
    )
    sessionLocationList.add(locationGroupsColumns)

    // When
    val exception = assertThrows(IllegalArgumentException::class.java) {
      sessionTemplateSQLGenerator.validateGroupColumns(sessionLocationList)
    }

    // Then
    assertThat(exception.message).isEqualTo("Location : Child can't have more than one parent (prison:prison1 key:key1)!")
  }

  @Test
  fun `Data parsed - session template levelFour cant have to parents validated correctly`() {
    // Given
    val sessionLocationList = mutableListOf<LocationGroupsColumns>()

    val locationGroupsColumns = LocationGroupsColumns(
      key = "key1",
      prisonCode = "prison1",
      levelOne = listOf("one"),
      levelTwo = listOf("two"),
      levelThree = listOf("three", "three"),
      levelFour = listOf("four"),
    )
    sessionLocationList.add(locationGroupsColumns)

    // When
    val exception = assertThrows(IllegalArgumentException::class.java) {
      sessionTemplateSQLGenerator.validateGroupColumns(sessionLocationList)
    }

    // Then
    assertThat(exception.message).isEqualTo("Location : Child can't have more than one parent (prison:prison1 key:key1)!")
  }

  @Test
  fun `Data parsed - session template levelThree cant have empty parent validated correctly`() {
    // Given
    val sessionLocationList = mutableListOf<LocationGroupsColumns>()

    val locationGroupsColumns = LocationGroupsColumns(
      key = "key1",
      prisonCode = "prison1",
      levelOne = listOf("one"),
      levelTwo = listOf(),
      levelThree = listOf("three"),
    )
    sessionLocationList.add(locationGroupsColumns)

    // When
    val exception = assertThrows(IllegalArgumentException::class.java) {
      sessionTemplateSQLGenerator.validateGroupColumns(sessionLocationList)
    }

    // Then
    assertThat(exception.message).isEqualTo("Location : Child can't have empty parent (prison:prison1 key:key1)!")
  }

  @Test
  fun `Data parsed - session template levelFour cant have empty parent validated correctly`() {
    // Given
    val sessionLocationList = mutableListOf<LocationGroupsColumns>()

    val locationGroupsColumns = LocationGroupsColumns(
      key = "key1",
      prisonCode = "prison1",
      levelOne = listOf("one"),
      levelTwo = listOf("two"),
      levelThree = listOf(),
      levelFour = listOf("four"),
    )
    sessionLocationList.add(locationGroupsColumns)

    // When
    val exception = assertThrows(IllegalArgumentException::class.java) {
      sessionTemplateSQLGenerator.validateGroupColumns(sessionLocationList)
    }

    // Then
    assertThat(exception.message).isEqualTo("Location : Child can't have empty parent (prison:prison1 key:key1)!")
  }

  @Test
  fun `Data parsed - session prisoner category data is parsed correctly`() {
    // Given
    val path = "src/test/resources/session-template-data/"
    val sessionCategoryDataFile = File(path, "session-prisoner-category-data_count_12.csv")

    // When
    val prisonerCategoryGroupsColumns =
      sessionTemplateSQLGenerator.getSessionGroupColumns(
        PRISONER_CATEGORY,
        sessionCategoryDataFile,
      )

    val sessionCategoryItems = sessionTemplateSQLGenerator.getSessionItems(prisonerCategoryGroupsColumns)

    // Then
    assertThat(prisonerCategoryGroupsColumns.size).isEqualTo(4)
    assertThat(prisonerCategoryGroupsColumns[0].key).isEqualTo("BLI_CAT1")
    assertThat(prisonerCategoryGroupsColumns[0].prisonCode).isEqualTo("BL1")
    assertThat(prisonerCategoryGroupsColumns[0].name).isEqualTo("Test CAT 1")
    assertThat(prisonerCategoryGroupsColumns[1].key).isEqualTo("BLI_CAT2")
    assertThat(prisonerCategoryGroupsColumns[1].prisonCode).isEqualTo("BL2")
    assertThat(prisonerCategoryGroupsColumns[1].name).isEqualTo("Test CAT 2")
    assertThat(prisonerCategoryGroupsColumns[2].key).isEqualTo("BLI_CAT3")
    assertThat(prisonerCategoryGroupsColumns[2].prisonCode).isEqualTo("BL3")
    assertThat(prisonerCategoryGroupsColumns[2].name).isEqualTo("Test CAT 3")
    assertThat(prisonerCategoryGroupsColumns[3].key).isEqualTo("BLI_CAT4")
    assertThat(prisonerCategoryGroupsColumns[3].prisonCode).isEqualTo("BL4")
    assertThat(prisonerCategoryGroupsColumns[3].name).isEqualTo("Test CAT 4")

    assertThat(sessionCategoryItems.size).isEqualTo(9)
    assertSessionPrisonerCategory(sessionCategoryItems[0], "BLI_CAT1", PrisonerCategoryType.B)
    assertSessionPrisonerCategory(sessionCategoryItems[1], "BLI_CAT2", PrisonerCategoryType.A_EXCEPTIONAL)
    assertSessionPrisonerCategory(sessionCategoryItems[2], "BLI_CAT3", PrisonerCategoryType.A_EXCEPTIONAL)
    assertSessionPrisonerCategory(sessionCategoryItems[3], "BLI_CAT3", PrisonerCategoryType.A_HIGH)
    assertSessionPrisonerCategory(sessionCategoryItems[4], "BLI_CAT3", PrisonerCategoryType.A_STANDARD)
    assertSessionPrisonerCategory(sessionCategoryItems[5], "BLI_CAT3", PrisonerCategoryType.A_PROVISIONAL)
    assertSessionPrisonerCategory(sessionCategoryItems[6], "BLI_CAT4", PrisonerCategoryType.B)
    assertSessionPrisonerCategory(sessionCategoryItems[7], "BLI_CAT4", PrisonerCategoryType.C)
    assertSessionPrisonerCategory(sessionCategoryItems[8], "BLI_CAT4", PrisonerCategoryType.D)
  }

  @Test
  fun `Data parsed - session prisoner category lower case data is converted to correct case when required`() {
    // Given
    val path = "src/test/resources/session-template-data/"
    val sessionCategoryDataFile = File(path, "session-prisoner-category-data_lower_case.csv")

    // When
    val prisonerCategoryGroupsColumns = sessionTemplateSQLGenerator.getSessionGroupColumns(PRISONER_CATEGORY, sessionCategoryDataFile)
    val sessionCategoryItems = sessionTemplateSQLGenerator.getSessionItems(prisonerCategoryGroupsColumns)

    // Then
    assertThat(sessionCategoryItems.size).isEqualTo(4)
    assertSessionPrisonerCategory(sessionCategoryItems[0], "BLI_CAT1", PrisonerCategoryType.A_EXCEPTIONAL)
    assertSessionPrisonerCategory(sessionCategoryItems[1], "BLI_CAT2", PrisonerCategoryType.B)
    assertSessionPrisonerCategory(sessionCategoryItems[2], "BLI_CAT2", PrisonerCategoryType.C)
    assertSessionPrisonerCategory(sessionCategoryItems[3], "BLI_CAT2", PrisonerCategoryType.D)
  }

  @Test
  fun `Data parsed - session prisoner category invalid category validated correctly`() {
    // Given
    val sessionCategoryList = mutableListOf<PrisonerCategoryGroupsColumns>()

    // invalid prisoner category - TEST
    val categoryGroupColumns = PrisonerCategoryGroupsColumns(categoryCodes = listOf("TEST"), key = "key1", prisonCode = "prison1")
    sessionCategoryList.add(categoryGroupColumns)

    // When
    val exception = assertThrows(IllegalArgumentException::class.java) {
      sessionTemplateSQLGenerator.validateGroupColumns(sessionCategoryList)
    }

    // Then
    assertThat(exception.message).startsWith("Category : Invalid category code - TEST - allowed values are - ")
    assertThat(exception.message).endsWith("(prison:prison1 key:key1)!")
  }

  @Test
  fun `Data parsed - session prisoner category no category codes validated correctly`() {
    // Given
    val sessionCategoryList = mutableListOf<PrisonerCategoryGroupsColumns>()

    val categoryGroupColumns = PrisonerCategoryGroupsColumns(
      key = "key1",
      prisonCode = "prison1",
      // no categories
      categoryCodes = listOf(),
    )
    sessionCategoryList.add(categoryGroupColumns)

    // When
    val exception = assertThrows(IllegalArgumentException::class.java) {
      sessionTemplateSQLGenerator.validateGroupColumns(sessionCategoryList)
    }

    // Then
    assertThat(exception.message).isEqualTo("Category : must have at least one category code (prison:prison1 key:key1)!")
  }

  @Test
  fun `Data parsed - session prisoner category no data is parsed correctly`() {
    // Given
    val path = "src/test/resources/session-template-data/"
    val sessionCategoryDataFile = File(path, "session-prisoner-category-data_no_data.csv")

    // When
    val prisonerCategoryGroupsColumns = sessionTemplateSQLGenerator.getSessionGroupColumns(PRISONER_CATEGORY, sessionCategoryDataFile)
    val sessionCategoryItems = sessionTemplateSQLGenerator.getSessionItems(prisonerCategoryGroupsColumns)

    // Then
    assertThat(sessionCategoryItems.size).isEqualTo(0)
  }

  @Test
  fun `Data parsed - session prisoner category missing columns validated correctly`() {
    // Given
    val path = "src/test/resources/session-template-data/"
    val fileName = "session-prisoner-category-data_missing_columns.csv"
    val sessionCategoryDataFile = File(path, fileName)

    // When
    val exception = assertThrows(IllegalArgumentException::class.java) {
      sessionTemplateSQLGenerator.getSessionGroupColumns(PRISONER_CATEGORY, sessionCategoryDataFile)
    }

    // Then
    assertThat(exception.message).isEqualTo("Some prisoner-category columns are missing $fileName line number: 1, expected 4 but got 3")
  }

  @Test
  fun `Data parsed - session prisoner incentive level data is parsed correctly`() {
    // Given
    val path = "src/test/resources/session-template-data/"
    val sessionIncentiveLevelDataFile = File(path, "session-prisoner-incentive-level-data_count_12.csv")

    // When
    val prisonerIncentiveLevelGroupsColumns =
      sessionTemplateSQLGenerator.getSessionGroupColumns(
        PRISONER_INCENTIVE_LEVEL,
        sessionIncentiveLevelDataFile,
      )

    val sessionIncentiveLevelItems = sessionTemplateSQLGenerator.getSessionItems(prisonerIncentiveLevelGroupsColumns)

    // Then
    assertThat(prisonerIncentiveLevelGroupsColumns.size).isEqualTo(4)
    assertThat(prisonerIncentiveLevelGroupsColumns[0].key).isEqualTo("CFI_INC_LEVEL1")
    assertThat(prisonerIncentiveLevelGroupsColumns[0].prisonCode).isEqualTo("CF1")
    assertThat(prisonerIncentiveLevelGroupsColumns[0].name).isEqualTo("Test INC_LEVEL 1")
    assertThat(prisonerIncentiveLevelGroupsColumns[1].key).isEqualTo("CFI_INC_LEVEL2")
    assertThat(prisonerIncentiveLevelGroupsColumns[1].prisonCode).isEqualTo("CF2")
    assertThat(prisonerIncentiveLevelGroupsColumns[1].name).isEqualTo("Test INC_LEVEL 2")
    assertThat(prisonerIncentiveLevelGroupsColumns[2].key).isEqualTo("CFI_INC_LEVEL3")
    assertThat(prisonerIncentiveLevelGroupsColumns[2].prisonCode).isEqualTo("CF3")
    assertThat(prisonerIncentiveLevelGroupsColumns[2].name).isEqualTo("Test INC_LEVEL 3")
    assertThat(prisonerIncentiveLevelGroupsColumns[3].key).isEqualTo("CFI_INC_LEVEL4")
    assertThat(prisonerIncentiveLevelGroupsColumns[3].prisonCode).isEqualTo("CF4")
    assertThat(prisonerIncentiveLevelGroupsColumns[3].name).isEqualTo("Test INC_LEVEL 4")

    assertThat(sessionIncentiveLevelItems.size).isEqualTo(7)
    assertSessionPrisonerIncentiveLevel(sessionIncentiveLevelItems[0], "CFI_INC_LEVEL1", IncentiveLevel.BASIC)
    assertSessionPrisonerIncentiveLevel(sessionIncentiveLevelItems[1], "CFI_INC_LEVEL2", IncentiveLevel.STANDARD)
    assertSessionPrisonerIncentiveLevel(sessionIncentiveLevelItems[2], "CFI_INC_LEVEL3", IncentiveLevel.ENHANCED_3)
    assertSessionPrisonerIncentiveLevel(sessionIncentiveLevelItems[3], "CFI_INC_LEVEL3", IncentiveLevel.BASIC)
    assertSessionPrisonerIncentiveLevel(sessionIncentiveLevelItems[4], "CFI_INC_LEVEL3", IncentiveLevel.STANDARD)
    assertSessionPrisonerIncentiveLevel(sessionIncentiveLevelItems[5], "CFI_INC_LEVEL4", IncentiveLevel.ENHANCED)
    assertSessionPrisonerIncentiveLevel(sessionIncentiveLevelItems[6], "CFI_INC_LEVEL4", IncentiveLevel.ENHANCED_2)
  }

  @Test
  fun `Data parsed - session prisoner incentive level lower case data is converted to correct case when required`() {
    // Given
    val path = "src/test/resources/session-template-data/"
    val sessionIncentiveLevelDataFile = File(path, "session-prisoner-incentive-level-data_lower_case.csv")

    // When
    val prisonerIncentiveLevelGroupsColumns = sessionTemplateSQLGenerator.getSessionGroupColumns(PRISONER_INCENTIVE_LEVEL, sessionIncentiveLevelDataFile)
    val sessionIncentiveLevelItems = sessionTemplateSQLGenerator.getSessionItems(prisonerIncentiveLevelGroupsColumns)

    // Then
    assertThat(sessionIncentiveLevelItems.size).isEqualTo(4)
    assertSessionPrisonerIncentiveLevel(sessionIncentiveLevelItems[0], "CFI_INC_LEVEL1", IncentiveLevel.BASIC)
    assertSessionPrisonerIncentiveLevel(sessionIncentiveLevelItems[1], "CFI_INC_LEVEL2", IncentiveLevel.ENHANCED_3)
    assertSessionPrisonerIncentiveLevel(sessionIncentiveLevelItems[2], "CFI_INC_LEVEL2", IncentiveLevel.STANDARD)
    assertSessionPrisonerIncentiveLevel(sessionIncentiveLevelItems[3], "CFI_INC_LEVEL2", IncentiveLevel.ENHANCED)
  }

  @Test
  fun `Data parsed - session prisoner  incentive level invalid incentive level validated correctly`() {
    // Given
    val sessionIncentiveLevelList = mutableListOf<PrisonerIncentiveLevelGroupsColumns>()

    val incentiveLevelGroupColumns = PrisonerIncentiveLevelGroupsColumns(incentiveLevels = listOf("TEST"), key = "key1", prisonCode = "prison1")
    // invalid prisoner incentiveLevel - TEST
    sessionIncentiveLevelList.add(incentiveLevelGroupColumns)

    // When
    val exception = assertThrows(IllegalArgumentException::class.java) {
      sessionTemplateSQLGenerator.validateGroupColumns(sessionIncentiveLevelList)
    }

    // Then
    assertThat(exception.message).startsWith("IncentiveLevel : Invalid incentive level - TEST - allowed values are - ")
    assertThat(exception.message).endsWith("(prison:prison1 key:key1)!")
  }

  @Test
  fun `Data parsed - session prisoner incentive level no incentive levels validated correctly`() {
    // Given
    val sessionIncentiveLevelList = mutableListOf<PrisonerIncentiveLevelGroupsColumns>()

    // no categories
    val incentiveLevelGroupColumns = PrisonerIncentiveLevelGroupsColumns(incentiveLevels = listOf(), key = "key1", prisonCode = "prison1")
    sessionIncentiveLevelList.add(incentiveLevelGroupColumns)

    // When
    val exception = assertThrows(IllegalArgumentException::class.java) {
      sessionTemplateSQLGenerator.validateGroupColumns(sessionIncentiveLevelList)
    }

    // Then
    assertThat(exception.message).isEqualTo("Incentive Level : must have at least one incentive level (prison:prison1 key:key1)!")
  }

  @Test
  fun `Data parsed - session prisoner incentive level no data is parsed correctly`() {
    // Given
    val path = "src/test/resources/session-template-data/"
    val sessionIncentiveLevelDataFile = File(path, "session-prisoner-incentive-level-data_no_data.csv")

    // When
    val prisonerIncentiveLevelGroupsColumns = sessionTemplateSQLGenerator.getSessionGroupColumns(PRISONER_INCENTIVE_LEVEL, sessionIncentiveLevelDataFile)
    val sessionIncentiveLevelItems = sessionTemplateSQLGenerator.getSessionItems(prisonerIncentiveLevelGroupsColumns)

    // Then
    assertThat(sessionIncentiveLevelItems.size).isEqualTo(0)
  }

  @Test
  fun `Data parsed - session prisoner incentive level missing columns validated correctly`() {
    // Given
    val path = "src/test/resources/session-template-data/"
    val fileName = "session-prisoner-incentive-level-data_missing_columns.csv"
    val sessionIncentiveLevelDataFile = File(path, fileName)

    // When
    val exception = assertThrows(IllegalArgumentException::class.java) {
      sessionTemplateSQLGenerator.getSessionGroupColumns(PRISONER_INCENTIVE_LEVEL, sessionIncentiveLevelDataFile)
    }

    // Then
    assertThat(exception.message).isEqualTo("Some prisoner-incentive-level columns are missing $fileName line number: 1, expected 4 but got 3")
  }

  private fun assertSessionLocation(
    sessionItem: SessionItem,
    prison: String,
    key: String,
    level1: String,
    level2: String? = null,
    level3: String? = null,
    level4: String? = null,
  ) {
    val sessionLocationItem = sessionItem as SessionLocationItem
    with(sessionLocationItem) {
      assertThat(prison).isEqualTo(prison)
      assertThat(groupKey).isEqualTo(key)
      assertThat(levelOne).isEqualTo(level1)
      assertThat(levelTwo).isEqualTo(level2)
      assertThat(levelThree).isEqualTo(level3)
      assertThat(levelFour).isEqualTo(level4)
    }
  }

  private fun assertSessionPrisonerCategory(
    sessionItem: SessionItem,
    key: String,
    prisonerCategory: PrisonerCategoryType,
  ) {
    val sessionPrisonerCategoryItem = sessionItem as SessionPrisonerCategoryItem
    assertThat(sessionPrisonerCategoryItem.groupKey).isEqualTo(key)
    assertThat(sessionPrisonerCategoryItem.prisonerCategoryType).isEqualTo(prisonerCategory)
  }

  private fun assertSessionPrisonerIncentiveLevel(
    sessionItem: SessionItem,
    key: String,
    incentiveLevel: IncentiveLevel,
  ) {
    val sessionPrisonerIncentiveLevelItem = sessionItem as SessionPrisonerIncentiveItem
    assertThat(sessionPrisonerIncentiveLevelItem.groupKey).isEqualTo(key)
    assertThat(sessionPrisonerIncentiveLevelItem.incentiveLevel).isEqualTo(incentiveLevel)
  }
}
