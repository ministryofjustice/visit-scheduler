package uk.gov.justice.digital.hmpps.visitscheduler.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.GroupType.LOCATION
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.GroupType.PRISONER_CATEGORY
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.LocationGroupsColumns
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.PrisonerCategoryGroupsColumns
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionItem
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionLocationItem
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionPrisonerCategoryItem
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
      assertThat(enhanced).isFalse
      assertThat(startTime).isEqualTo(LocalTime.parse("06:00"))
      assertThat(endTime).isEqualTo(LocalTime.parse("12:00"))
      assertThat(startDate).isEqualTo(LocalDate.parse("2022-11-16"))
      assertThat(endDate).isNull()
      assertThat(dayOfWeek).isEqualTo(DayOfWeek.TUESDAY)
      assertThat(biWeekly).isFalse
      assertThat(locationKeys).isEqualTo("BLI_G1")
      assertThat(categoryKeys).isEqualTo("INC_CAT")
    }
    with(sessionRecords[1]) {
      assertThat(prisonCode).isEqualTo("BL2")
      assertThat(visitRoom).isEqualTo("Room 2")
      assertThat(type).isEqualTo(SOCIAL)
      assertThat(open).isEqualTo(20)
      assertThat(closed).isEqualTo(2)
      assertThat(enhanced).isTrue
      assertThat(startTime).isEqualTo(LocalTime.parse("07:00"))
      assertThat(endTime).isEqualTo(LocalTime.parse("13:00"))
      assertThat(startDate).isEqualTo(LocalDate.parse("2022-11-17"))
      assertThat(endDate).isEqualTo(LocalDate.parse("2022-12-17"))
      assertThat(dayOfWeek).isEqualTo(DayOfWeek.WEDNESDAY)
      assertThat(biWeekly).isTrue
      assertThat(locationKeys).isEqualTo("BLI_G2")
      assertThat(categoryKeys).isEqualTo("INC_CAT")
    }
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
      assertThat(enhanced).isFalse
      assertThat(dayOfWeek).isEqualTo(DayOfWeek.TUESDAY)
      assertThat(locationKeys).isEqualTo("BLI_G1")
      assertThat(biWeekly).isFalse
      assertThat(categoryKeys).isEqualTo("INC_CAT")
    }
  }

  @Test
  fun `Data parsed - session template levelOne validated correctly`() {
    // Given
    val sessionLocationList = mutableListOf<LocationGroupsColumns>()

    val locationGroupsColumns = mock(LocationGroupsColumns::class.java)
    Mockito.`when`(locationGroupsColumns.levelOne).thenReturn(listOf())
    Mockito.`when`(locationGroupsColumns.prisonCode).thenReturn("prison1")
    Mockito.`when`(locationGroupsColumns.key).thenReturn("key1")
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

    val locationGroupsColumns = mock(LocationGroupsColumns::class.java)
    Mockito.`when`(locationGroupsColumns.levelOne).thenReturn(listOf("one", "one"))
    Mockito.`when`(locationGroupsColumns.levelTwo).thenReturn(listOf("child"))
    Mockito.`when`(locationGroupsColumns.prisonCode).thenReturn("prison1")
    Mockito.`when`(locationGroupsColumns.key).thenReturn("key1")
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

    val locationGroupsColumns = mock(LocationGroupsColumns::class.java)
    Mockito.`when`(locationGroupsColumns.levelOne).thenReturn(listOf("one"))
    Mockito.`when`(locationGroupsColumns.levelTwo).thenReturn(listOf("two", "two"))
    Mockito.`when`(locationGroupsColumns.levelThree).thenReturn(listOf("three"))
    Mockito.`when`(locationGroupsColumns.prisonCode).thenReturn("prison1")
    Mockito.`when`(locationGroupsColumns.key).thenReturn("key1")
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

    val locationGroupsColumns = mock(LocationGroupsColumns::class.java)
    Mockito.`when`(locationGroupsColumns.levelOne).thenReturn(listOf("one"))
    Mockito.`when`(locationGroupsColumns.levelTwo).thenReturn(listOf("two"))
    Mockito.`when`(locationGroupsColumns.levelThree).thenReturn(listOf("three", "three"))
    Mockito.`when`(locationGroupsColumns.levelFour).thenReturn(listOf("four"))
    Mockito.`when`(locationGroupsColumns.prisonCode).thenReturn("prison1")
    Mockito.`when`(locationGroupsColumns.key).thenReturn("key1")
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

    val locationGroupsColumns = mock(LocationGroupsColumns::class.java)
    Mockito.`when`(locationGroupsColumns.levelOne).thenReturn(listOf("one"))
    Mockito.`when`(locationGroupsColumns.levelTwo).thenReturn(listOf())
    Mockito.`when`(locationGroupsColumns.levelThree).thenReturn(listOf("three"))
    Mockito.`when`(locationGroupsColumns.prisonCode).thenReturn("prison1")
    Mockito.`when`(locationGroupsColumns.key).thenReturn("key1")
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

    val locationGroupsColumns = mock(LocationGroupsColumns::class.java)
    Mockito.`when`(locationGroupsColumns.levelOne).thenReturn(listOf("one"))
    Mockito.`when`(locationGroupsColumns.levelTwo).thenReturn(listOf("two"))
    Mockito.`when`(locationGroupsColumns.levelThree).thenReturn(listOf())
    Mockito.`when`(locationGroupsColumns.levelFour).thenReturn(listOf("four"))
    Mockito.`when`(locationGroupsColumns.prisonCode).thenReturn("prison1")
    Mockito.`when`(locationGroupsColumns.key).thenReturn("key1")
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

    val categoryGroupColumns = mock(PrisonerCategoryGroupsColumns::class.java)
    // invalid prisoner category - TEST
    Mockito.`when`(categoryGroupColumns.categoryCodes).thenReturn(listOf("TEST"))
    Mockito.`when`(categoryGroupColumns.prisonCode).thenReturn("prison1")
    Mockito.`when`(categoryGroupColumns.key).thenReturn("key1")
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

    val categoryGroupColumns = mock(PrisonerCategoryGroupsColumns::class.java)
    // no categories
    Mockito.`when`(categoryGroupColumns.categoryCodes).thenReturn(listOf())
    Mockito.`when`(categoryGroupColumns.prisonCode).thenReturn("prison1")
    Mockito.`when`(categoryGroupColumns.key).thenReturn("key1")
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
}
