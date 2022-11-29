package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import reactor.util.function.Tuple2
import reactor.util.function.Tuples
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevelDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLocationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedPrisonHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.PermittedSessionLocation
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class PrisonerSessionValidatorTest {
  private val prisonerLevelMatcher = PrisonerLevelMatcher()
  private val prisonerSessionValidator = PrisonerSessionValidator(prisonerLevelMatcher)
  @Nested
  @DisplayName("Tests when a prisoner exists in a prison which has 3 levels")
  inner class Level3PrisonTest {
    val prison: Prison = Prison(code = "BLI", active = true)

    // prisoner details are as follows
    // level 1 - "C", level 2 - "1", level 3 - "004" and no level 4
    private val level1 = PrisonerHousingLevelDto(level = 1, code = "C", description = "level 1")
    private val level2 = PrisonerHousingLevelDto(level = 2, code = "1", description = "level 2")
    private val level3 = PrisonerHousingLevelDto(level = 3, code = "004", description = "level 3")
    private val prisonerDetail3LevelPrison = PrisonerHousingLocationsDto(listOf(level1, level2, level3))

    @Test
    fun `session available to all prisoners when permitted session location is empty`() {
      // Given
      val permittedSessionLocations = mutableListOf<PermittedSessionLocation>()

      // When
      val sessionTemplate = createSessionTemplate(permittedSessionLocations)

      // Then
      assertThat(prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerDetail3LevelPrison.getLevels(), sessionTemplate)).isTrue
    }

    @Test
    fun `session available to prisoner when permitted session location has matching level 1 - single locations permitted`() {
      // Given
      // allowed levels for the session are (level 1 = C)
      // and prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = null so session is available
      val allowed = Tuples.of(AllowedPrisonHierarchy("C", null, null, null), prison)
      val permittedSessionLocationList = createPermittedSessionLocationList(listOf(allowed))
      val sessionTemplate = createSessionTemplate(permittedSessionLocations = permittedSessionLocationList)

      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerDetail3LevelPrison.getLevels(), sessionTemplate)

      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session available to prisoner when permitted session location has matching level 1 - multiple locations permitted`() {
      // Given
      // allowed levels for the session are (level 1 = A), (level 1 =B), (level 1=C), (level 1=C)
      // and prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = null  so session is available
      val allowed1 = Tuples.of(AllowedPrisonHierarchy("A", null, null, null), prison)
      val allowed2 = Tuples.of(AllowedPrisonHierarchy("B", null, null, null), prison)
      val allowed3 = Tuples.of(AllowedPrisonHierarchy("C", null, null, null), prison)
      val allowed4 = Tuples.of(AllowedPrisonHierarchy("D", null, null, null), prison)
      val permittedSessionLocationList = createPermittedSessionLocationList(listOf(allowed1, allowed2, allowed3, allowed4))
      val sessionTemplate = createSessionTemplate(permittedSessionLocations = permittedSessionLocationList)

      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerDetail3LevelPrison.getLevels(), sessionTemplate)

      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session not available to prisoner when permitted session location does not have has matching level 1`() {
      // Given
      // allowed levels for the session are (level 1 = A), (level 1 =B), (level 1=D)
      // while prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = null so session isn't available
      val allowed1 = Tuples.of(AllowedPrisonHierarchy("A", null, null, null), prison)
      val allowed2 = Tuples.of(AllowedPrisonHierarchy("B", null, null, null), prison)
      val allowed3 = Tuples.of(AllowedPrisonHierarchy("D", null, null, null), prison)
      val allowed4 = Tuples.of(AllowedPrisonHierarchy("E", null, null, null), prison)
      val permittedSessionLocationList = createPermittedSessionLocationList(listOf(allowed1, allowed2, allowed3, allowed4))
      val sessionTemplate = createSessionTemplate(permittedSessionLocations = permittedSessionLocationList)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerDetail3LevelPrison.getLevels(), sessionTemplate)

      // Then
      assertThat(result).isFalse
    }

    @Test
    fun `session available to prisoner when permitted session location has matching level 1 and level 2 - single location permitted`() {
      // Given
      // allowed levels for the session are (level 1=C and level 2 = 1)
      // while prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = null so session isn't available
      val allowed = Tuples.of(AllowedPrisonHierarchy("C", "1", null, null), prison)
      val permittedSessionLocationList = createPermittedSessionLocationList(listOf(allowed))
      val sessionTemplate = createSessionTemplate(permittedSessionLocations = permittedSessionLocationList)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerDetail3LevelPrison.getLevels(), sessionTemplate)

      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session available to prisoner when permitted session location has matching level 1 and level 2`() {
      // Given
      // allowed levels for the session are (level 1 = A), (level 1 =B and level 2 = 1), (level 1=C and level 2 = 1 and level 1=C and level 2 = 2 and level 3 = "001")
      // and prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = null so session is available
      val allowed1 = Tuples.of(AllowedPrisonHierarchy("A", null, null, null), prison)
      val allowed2 = Tuples.of(AllowedPrisonHierarchy("B", "1", null, null), prison)
      val allowed3 = Tuples.of(AllowedPrisonHierarchy("C", "1", null, null), prison)
      val allowed4 = Tuples.of(AllowedPrisonHierarchy("C", "2", "001", null), prison)
      val permittedSessionLocationList = createPermittedSessionLocationList(listOf(allowed1, allowed2, allowed3, allowed4))
      val sessionTemplate = createSessionTemplate(permittedSessionLocations = permittedSessionLocationList)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerDetail3LevelPrison.getLevels(), sessionTemplate)

      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session not available to prisoner when permitted session location does not have matching level 2`() {
      // Given
      // allowed levels for the session are (level 1 = A), (level 1 =B and level 2 = 1), (level 1=C and level 2 = 1 and level 1=C and level 2 = 2 and level 3 = "001")
      // while prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = null so session isn't available
      val allowed1 = Tuples.of(AllowedPrisonHierarchy("A", null, null, null), prison)
      val allowed2 = Tuples.of(AllowedPrisonHierarchy("B", "1", null, null), prison)
      val allowed3 = Tuples.of(AllowedPrisonHierarchy("C", "2", null, null), prison)
      val allowed4 = Tuples.of(AllowedPrisonHierarchy("C", "3", null, null), prison)
      val permittedSessionLocationList = createPermittedSessionLocationList(listOf(allowed1, allowed2, allowed3, allowed4))
      val sessionTemplate = createSessionTemplate(permittedSessionLocations = permittedSessionLocationList)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerDetail3LevelPrison.getLevels(), sessionTemplate)

      // Then
      assertThat(result).isFalse
    }

    @Test
    fun `session available to prisoner when permitted session location has matching level 1 and level 2 and level 3`() {
      // Given
      // allowed values are level 1 = A, level 1 =B and level 2 = 1, level 3=,C, level 2 = 1 and level 3 = "001" to "007" are allowed
      // and prisoner is in C wing, level 2 = "1" and level 3 = "004" so session is available
      val allowed1 = Tuples.of(AllowedPrisonHierarchy("A", null, null, null), prison)
      val allowed2 = Tuples.of(AllowedPrisonHierarchy("B", "1", null, null), prison)
      val allowed3 = Tuples.of(AllowedPrisonHierarchy("C", "1", "001", null), prison)
      val allowed4 = Tuples.of(AllowedPrisonHierarchy("C", "1", "002", null), prison)
      val allowed5 = Tuples.of(AllowedPrisonHierarchy("C", "1", "003", null), prison)
      val allowed6 = Tuples.of(AllowedPrisonHierarchy("C", "1", "004", null), prison)
      val allowed7 = Tuples.of(AllowedPrisonHierarchy("C", "1", "005", null), prison)
      val allowed8 = Tuples.of(AllowedPrisonHierarchy("C", "1", "006", null), prison)
      val allowed9 = Tuples.of(AllowedPrisonHierarchy("C", "1", "007", null), prison)
      val permittedSessionLocationList = createPermittedSessionLocationList(
        listOf(
          allowed1, allowed2, allowed3, allowed4, allowed5, allowed6, allowed7, allowed8, allowed9
        )
      )
      val sessionTemplate = createSessionTemplate(permittedSessionLocations = permittedSessionLocationList)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerDetail3LevelPrison.getLevels(), sessionTemplate)
      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session not available to prisoner when permitted session location has matching level 1 and level 2 but not level 3`() {
      // Given
      // allowed levels for the session are (level 1 = A), (level 1 =B and level 2 = 2 and level 1=B and level 2 = 1 and level 3 = "004"), (level 1=C and level 2 = 1 and level 3 = "001" to "003")
      // while prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = null so session isn't available
      val allowed1 = Tuples.of(AllowedPrisonHierarchy("A", null, null, null), prison)
      val allowed2 = Tuples.of(AllowedPrisonHierarchy("B", "1", null, null), prison)
      val allowed3 = Tuples.of(AllowedPrisonHierarchy("C", "1", "001", null), prison)
      val allowed4 = Tuples.of(AllowedPrisonHierarchy("C", "1", "002", null), prison)
      val allowed5 = Tuples.of(AllowedPrisonHierarchy("C", "1", "003", null), prison)

      val permittedSessionLocationList = createPermittedSessionLocationList(listOf(allowed1, allowed2, allowed3, allowed4, allowed5))
      val sessionTemplate = createSessionTemplate(permittedSessionLocations = permittedSessionLocationList)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerDetail3LevelPrison.getLevels(), sessionTemplate)
      // Then
      assertThat(result).isFalse
    }
  }

  @Nested
  @DisplayName("Tests when a prisoner exists in a prison which has 4 levels")
  inner class Level4PrisonTest {
    // prisoner details are as follows
    // level 1 - "C", level 2 - "1", level 3 - "004" and level 4 = "10000"
    val prison: Prison = Prison(code = "BLI", active = true)

    private val level1 = PrisonerHousingLevelDto(level = 1, code = "C", description = "level 1")
    private val level2 = PrisonerHousingLevelDto(level = 2, code = "1", description = "level 2")
    private val level3 = PrisonerHousingLevelDto(level = 3, code = "004", description = "level 3")
    private val prisonerDetail4LevelPrison = PrisonerHousingLocationsDto(listOf(level1, level2, level3))
    @Test
    fun `session available to all prisoners when permitted session location is empty`() {
      // Given
      val sessionTemplate = createSessionTemplate(permittedSessionLocations = mutableListOf())
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerDetail4LevelPrison.getLevels(), sessionTemplate)
      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session available to prisoner when permitted session location has matching level 1 - single locations permitted`() {
      // Given
      // allowed levels for the session are (level 1 = C)
      // and prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = "10000" so session is available
      val allowed = Tuples.of(AllowedPrisonHierarchy("C", null, null, null), prison)
      val permittedSessionLocationList = createPermittedSessionLocationList(listOf(allowed))
      val sessionTemplate = createSessionTemplate(permittedSessionLocations = permittedSessionLocationList)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerDetail4LevelPrison.getLevels(), sessionTemplate)
      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session available to prisoner when permitted session location has matching level 1 - multiple locations permitted`() {
      // Given
      // allowed levels for the session are (level 1 = A), (level 1 =B), (level 1=C), (level 1=C)
      // and prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = "10000"  so session is available
      val allowed1 = Tuples.of(AllowedPrisonHierarchy("A", null, null, null), prison)
      val allowed2 = Tuples.of(AllowedPrisonHierarchy("B", null, null, null), prison)
      val allowed3 = Tuples.of(AllowedPrisonHierarchy("C", null, null, null), prison)
      val allowed4 = Tuples.of(AllowedPrisonHierarchy("D", null, null, null), prison)
      val permittedSessionLocationList = createPermittedSessionLocationList(listOf(allowed1, allowed2, allowed3, allowed4))
      val sessionTemplate = createSessionTemplate(permittedSessionLocations = permittedSessionLocationList)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerDetail4LevelPrison.getLevels(), sessionTemplate)
      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session not available to prisoner when permitted session location does not have has matching level 1`() {
      // Given
      // allowed levels for the session are (level 1 = A), (level 1 =B), (level 1=D)
      // while prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = "10000" so session isn't available
      val allowed1 = Tuples.of(AllowedPrisonHierarchy("A", null, null, null), prison)
      val allowed2 = Tuples.of(AllowedPrisonHierarchy("B", null, null, null), prison)
      val allowed3 = Tuples.of(AllowedPrisonHierarchy("D", null, null, null), prison)
      val allowed4 = Tuples.of(AllowedPrisonHierarchy("E", null, null, null), prison)
      val permittedSessionLocationList = createPermittedSessionLocationList(listOf(allowed1, allowed2, allowed3, allowed4))
      val sessionTemplate = createSessionTemplate(permittedSessionLocations = permittedSessionLocationList)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerDetail4LevelPrison.getLevels(), sessionTemplate)
      // Then
      assertThat(result).isFalse
    }

    @Test
    fun `session available to prisoner when permitted session location has matching level 1 and level 2 - single location permitted`() {
      // Given
      // allowed levels for the session are (level 1=C and level 2 = 1)
      // while prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = "10000" so session isn't available
      val allowed = Tuples.of(AllowedPrisonHierarchy("C", "1", null, null), prison)
      val permittedSessionLocationList = createPermittedSessionLocationList(listOf(allowed))
      val sessionTemplate = createSessionTemplate(permittedSessionLocations = permittedSessionLocationList)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerDetail4LevelPrison.getLevels(), sessionTemplate)
      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session available to prisoner when permitted session location has matching level 1 and level 2`() {
      // Given
      // allowed levels for the session are (level 1 = A), (level 1 =B and level 2 = 1), (level 1=C and level 2 = 1 and level 1=C and level 2 = 2 and level 3 = "001")
      // and prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = "10000" so session is available
      val allowed1 = Tuples.of(AllowedPrisonHierarchy("A", null, null, null), prison)
      val allowed2 = Tuples.of(AllowedPrisonHierarchy("B", "1", null, null), prison)
      val allowed3 = Tuples.of(AllowedPrisonHierarchy("C", "1", null, null), prison)
      val allowed4 = Tuples.of(AllowedPrisonHierarchy("C", "2", "001", null), prison)
      val permittedSessionLocationList = createPermittedSessionLocationList(listOf(allowed1, allowed2, allowed3, allowed4))
      val sessionTemplate = createSessionTemplate(permittedSessionLocations = permittedSessionLocationList)

      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerDetail4LevelPrison.getLevels(), sessionTemplate)
      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session not available to prisoner when permitted session location does not have matching level 2`() {
      // Given
      // allowed levels for the session are (level 1 = A), (level 1 =B and level 2 = 1), (level 1=C and level 2 = 1 and level 1=C and level 2 = 2 and level 3 = "001")
      // while prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = "10000" so session isn't available
      val allowed1 = Tuples.of(AllowedPrisonHierarchy("A", null, null, null), prison)
      val allowed2 = Tuples.of(AllowedPrisonHierarchy("B", "1", null, null), prison)
      val allowed3 = Tuples.of(AllowedPrisonHierarchy("C", "2", null, null), prison)
      val allowed4 = Tuples.of(AllowedPrisonHierarchy("C", "3", null, null), prison)
      val permittedSessionLocationList = createPermittedSessionLocationList(listOf(allowed1, allowed2, allowed3, allowed4))
      val sessionTemplate = createSessionTemplate(permittedSessionLocations = permittedSessionLocationList)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerDetail4LevelPrison.getLevels(), sessionTemplate)
      // Then
      assertThat(result).isFalse
    }

    @Test
    fun `session available to prisoner when permitted session location has matching level 1 and level 2 and level 3`() {
      // Given
      // allowed values are level 1 = A, level 1 =B and level 2 = 1, level 3=,C, level 2 = 1 and level 3 = "001" to "007" are allowed
      // and prisoner is in C wing, level 2 = "1" and level 3 = "004" so session is available
      val allowed1 = Tuples.of(AllowedPrisonHierarchy("A", null, null, null), prison)
      val allowed2 = Tuples.of(AllowedPrisonHierarchy("B", "1", null, null), prison)
      val allowed3 = Tuples.of(AllowedPrisonHierarchy("C", "1", "001", null), prison)
      val allowed4 = Tuples.of(AllowedPrisonHierarchy("C", "1", "002", null), prison)
      val allowed5 = Tuples.of(AllowedPrisonHierarchy("C", "1", "003", null), prison)
      val allowed6 = Tuples.of(AllowedPrisonHierarchy("C", "1", "004", null), prison)
      val allowed7 = Tuples.of(AllowedPrisonHierarchy("C", "1", "005", null), prison)
      val allowed8 = Tuples.of(AllowedPrisonHierarchy("C", "1", "006", null), prison)
      val allowed9 = Tuples.of(AllowedPrisonHierarchy("C", "1", "007", null), prison)
      val permittedSessionLocationList = createPermittedSessionLocationList(
        listOf(
          allowed1, allowed2, allowed3, allowed4, allowed5, allowed6, allowed7, allowed8, allowed9
        )
      )
      val sessionTemplate = createSessionTemplate(permittedSessionLocations = permittedSessionLocationList)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerDetail4LevelPrison.getLevels(), sessionTemplate)
      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session not available to prisoner when permitted session location has matching level 1 and level 2 but not level 3`() {
      // Given
      // allowed levels for the session are (level 1 = A), (level 1 =B and level 2 = 2 and level 1=B and level 2 = 1 and level 3 = "004"), (level 1=C and level 2 = 1 and level 3 = "001" to "003")
      // while prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = "10000" so session isn't available
      val allowed1 = Tuples.of(AllowedPrisonHierarchy("A", null, null, null), prison)
      val allowed2 = Tuples.of(AllowedPrisonHierarchy("B", "2", null, null), prison)
      val allowed3 = Tuples.of(AllowedPrisonHierarchy("B", "1", "004", null), prison)
      val allowed4 = Tuples.of(AllowedPrisonHierarchy("C", "1", "001", null), prison)
      val allowed5 = Tuples.of(AllowedPrisonHierarchy("C", "1", "002", null), prison)
      val allowed6 = Tuples.of(AllowedPrisonHierarchy("C", "1", "003", null), prison)

      val permittedSessionLocationList = createPermittedSessionLocationList(
        listOf(
          allowed1, allowed2, allowed3, allowed4, allowed5, allowed6
        )
      )
      val sessionTemplate = createSessionTemplate(permittedSessionLocations = permittedSessionLocationList)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerDetail4LevelPrison.getLevels(), sessionTemplate)
      // Then
      assertThat(result).isFalse
    }

    @Test
    fun `session available to prisoner when permitted session location has matching level 1 2 3 and 4`() {
      // Given
      // allowed values are level 1 = A, level 1 =B and level 2 = 1,
      // level 3=,C, level 2 = 1 and level 3 = "002" to "007" are allowed
      // level 3=,C, level 2 = 1 and level 3 = "001" and level 4 - "1000", "4000", "7000"and  "10000" are allowed
      // and prisoner is in C wing, level 2 = "1" and level 3 = "004" so session is available
      val allowed1 = Tuples.of(AllowedPrisonHierarchy("A", null, null, null), prison)
      val allowed2 = Tuples.of(AllowedPrisonHierarchy("B", "1", null, null), prison)
      val allowed3 = Tuples.of(AllowedPrisonHierarchy("C", "1", "001", "1000"), prison)
      val allowed4 = Tuples.of(AllowedPrisonHierarchy("C", "1", "001", "4000"), prison)
      val allowed5 = Tuples.of(AllowedPrisonHierarchy("C", "1", "001", "7000"), prison)
      val allowed6 = Tuples.of(AllowedPrisonHierarchy("C", "1", "001", "10000"), prison)
      val allowed7 = Tuples.of(AllowedPrisonHierarchy("C", "1", "002", null), prison)
      val allowed8 = Tuples.of(AllowedPrisonHierarchy("C", "1", "003", null), prison)
      val allowed9 = Tuples.of(AllowedPrisonHierarchy("C", "1", "004", null), prison)
      val allowed10 = Tuples.of(AllowedPrisonHierarchy("C", "1", "005", null), prison)
      val allowed11 = Tuples.of(AllowedPrisonHierarchy("C", "1", "006", null), prison)
      val allowed12 = Tuples.of(AllowedPrisonHierarchy("C", "1", "007", null), prison)
      val permittedSessionLocationList = createPermittedSessionLocationList(
        listOf(
          allowed1, allowed2, allowed3, allowed4,
          allowed5, allowed6, allowed7, allowed8, allowed9, allowed10, allowed11, allowed12
        )
      )
      val sessionTemplate = createSessionTemplate(permittedSessionLocations = permittedSessionLocationList)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerDetail4LevelPrison.getLevels(), sessionTemplate)
      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session available to prisoner when permitted session location has matching level 1 2 3 but not leve 4`() {
      // Given
      // allowed values are level 1 = A, level 1 =B and level 2 = 1,
      // level 3=,C, level 2 = 1 and level 3 = "002" to "007" are allowed
      // level 3=,C, level 2 = 1 and level 3 = "001" and level 4 - "1000", "4000", "7000" are allowed
      // and prisoner is in C wing, level 2 = "1" and level 3 = "004" so session is available
      val allowed1 = Tuples.of(AllowedPrisonHierarchy("A", null, null, null), prison)
      val allowed2 = Tuples.of(AllowedPrisonHierarchy("B", "1", null, null), prison)
      val allowed3 = Tuples.of(AllowedPrisonHierarchy("C", "1", "001", "1000"), prison)
      val allowed4 = Tuples.of(AllowedPrisonHierarchy("C", "1", "001", "4000"), prison)
      val allowed5 = Tuples.of(AllowedPrisonHierarchy("C", "1", "001", "7000"), prison)
      val allowed6 = Tuples.of(AllowedPrisonHierarchy("C", "1", "002", null), prison)
      val allowed7 = Tuples.of(AllowedPrisonHierarchy("C", "1", "003", null), prison)
      val allowed8 = Tuples.of(AllowedPrisonHierarchy("C", "1", "004", null), prison)
      val allowed9 = Tuples.of(AllowedPrisonHierarchy("C", "1", "005", null), prison)
      val allowed10 = Tuples.of(AllowedPrisonHierarchy("C", "1", "006", null), prison)
      val allowed11 = Tuples.of(AllowedPrisonHierarchy("C", "1", "007", null), prison)
      val permittedSessionLocationList = createPermittedSessionLocationList(
        listOf(
          allowed1, allowed2, allowed3, allowed4,
          allowed5, allowed6, allowed7, allowed8, allowed9, allowed10, allowed11
        )
      )
      val sessionTemplate = createSessionTemplate(permittedSessionLocations = permittedSessionLocationList)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerDetail4LevelPrison.getLevels(), sessionTemplate)
      // Then
      assertThat(result).isTrue
    }
  }

  private fun createSessionTemplate(
    permittedSessionLocations: MutableList<PermittedSessionLocation>
  ): SessionTemplate {
    return sessionTemplate(
      validFromDate = LocalDate.now(),
      permittedSessionLocations = permittedSessionLocations
    )
  }

  private fun createPermittedSessionLocationList(levelsList: List<Tuple2<AllowedPrisonHierarchy, Prison>>): MutableList<PermittedSessionLocation> {
    val permittedSessionLocations = mutableListOf<PermittedSessionLocation>()
    for (level in levelsList) {
      permittedSessionLocations.add(createPermittedSessionLocation(level.t1, level.t2))
    }
    return permittedSessionLocations
  }

  private fun createPermittedSessionLocation(
    allowedPrisonHierarchy: AllowedPrisonHierarchy,
    prison: Prison
  ): PermittedSessionLocation {
    return PermittedSessionLocation(
      levelOneCode = allowedPrisonHierarchy.levelOneCode,
      levelTwoCode = allowedPrisonHierarchy.levelTwoCode,
      levelThreeCode = allowedPrisonHierarchy.levelThreeCode,
      levelFourCode = allowedPrisonHierarchy.levelFourCode,
      prisonId = prison.id,
      prison = prison
    )
  }

  companion object {
    const val prisonCode = "XYZ"
  }
}
