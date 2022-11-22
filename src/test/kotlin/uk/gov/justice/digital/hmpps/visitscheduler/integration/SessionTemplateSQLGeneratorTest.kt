package uk.gov.justice.digital.hmpps.visitscheduler.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateSQLGenerator.SessionLocationItem
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Session Template SQL Generator Test")
class SessionTemplateSQLGeneratorTest() {

  @Test
  fun `Data parsed - session template is parsed correctly`() {

    // Given
    val path = "src/test/resources/session-template-data/"
    val sessionDataFile = File(path, "session-data_count_4.csv")

    val sessionTemplateSQLGenerator = SessionTemplateSQLGenerator()

    // When
    val sessionRecords = sessionTemplateSQLGenerator.getSessionRecordsRecords(sessionDataFile)

    // Then
    assertThat(sessionRecords.size).isEqualTo(4)
    with(sessionRecords[0]) {
      assertThat(prison).isEqualTo("BL1")
      assertThat(room).isEqualTo("Room 1")
      assertThat(type).isEqualTo(SOCIAL)
      assertThat(open).isEqualTo(10)
      assertThat(closed).isEqualTo(1)
      assertThat(startTime).isEqualTo(LocalTime.parse("06:00"))
      assertThat(endTime).isEqualTo(LocalTime.parse("12:00"))
      assertThat(startDate).isEqualTo(LocalDate.parse("2022-11-16"))
      assertThat(endDate).isNull()
      assertThat(dayOfWeek).isEqualTo(DayOfWeek.TUESDAY)
      assertThat(locationKeys).isEqualTo("BLI_G1")
    }
    with(sessionRecords[1]) {
      assertThat(prison).isEqualTo("BL2")
      assertThat(room).isEqualTo("Room 2")
      assertThat(type).isEqualTo(SOCIAL)
      assertThat(open).isEqualTo(20)
      assertThat(closed).isEqualTo(2)
      assertThat(startTime).isEqualTo(LocalTime.parse("07:00"))
      assertThat(endTime).isEqualTo(LocalTime.parse("13:00"))
      assertThat(startDate).isEqualTo(LocalDate.parse("2022-11-17"))
      assertThat(endDate).isEqualTo(LocalDate.parse("2022-12-17"))
      assertThat(dayOfWeek).isEqualTo(DayOfWeek.WEDNESDAY)
      assertThat(locationKeys).isEqualTo("BLI_G2")
    }
  }

  @Test
  fun `Data parsed - session location is parsed correctly`() {

    // Given
    val path = "src/test/resources/session-template-data/"
    val sessionLocationDataFile = File(path, "session-location-data_count_12.csv")

    val sessionTemplateSQLGenerator = SessionTemplateSQLGenerator()

    // When
    val sessionLocationItems = sessionTemplateSQLGenerator.getSessionLocationItems(sessionLocationDataFile)

    // Then
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

    val sessionTemplateSQLGenerator = SessionTemplateSQLGenerator()

    // When
    val sessionLocationItems = sessionTemplateSQLGenerator.getSessionLocationItems(sessionLocationDataFile)

    // Then
    assertThat(sessionLocationItems.size).isEqualTo(1)
    assertSessionLocation(sessionLocationItems[0], "BL8", "BLI_G8", "S", "C", "2", "005")
  }

  @Test
  fun `Data parsed - session template lower case data is converted to correct case when required`() {

    // Given
    // Given
    val path = "src/test/resources/session-template-data/"
    val sessionDataFile = File(path, "session-data_lower_case.csv")

    val sessionTemplateSQLGenerator = SessionTemplateSQLGenerator()

    // When
    val sessionRecords = sessionTemplateSQLGenerator.getSessionRecordsRecords(sessionDataFile)

    // Then
    assertThat(sessionRecords.size).isEqualTo(1)
    with(sessionRecords[0]) {
      assertThat(prison).isEqualTo("BL1")
      assertThat(room).isEqualTo("room 1")
      assertThat(type).isEqualTo(SOCIAL)
      assertThat(dayOfWeek).isEqualTo(DayOfWeek.TUESDAY)
      assertThat(locationKeys).isEqualTo("BLI_G1")
    }
  }

  private fun assertSessionLocation(
    sessionLocationItem: SessionLocationItem,
    prison: String,
    key: String,
    level1: String,
    level2: String? = null,
    level3: String? = null,
    level4: String? = null,
  ) {
    with(sessionLocationItem) {
      assertThat(prison).isEqualTo(prison)
      assertThat(key).isEqualTo(key)
      assertThat(levelOne).isEqualTo(level1)
      assertThat(levelTwo).isEqualTo(level2)
      assertThat(levelThree).isEqualTo(level3)
      assertThat(levelFour).isEqualTo(level4)
    }
  }
}
