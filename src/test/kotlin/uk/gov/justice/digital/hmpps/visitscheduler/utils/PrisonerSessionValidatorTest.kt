package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevelDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLocationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.PermittedSessionLocation
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonerService
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class PrisonerSessionValidatorTest {
  private val prisonerLevelMatcher = PrisonerLevelMatcher()
  private val prisonerSessionValidator = PrisonerSessionValidator(prisonerLevelMatcher)
  private val prisonerService = PrisonerService(mock(), mock())

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

      // When
      val sessionTemplate = createSessionTemplate()

      // Then
      assertThat(prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerService.getLevelsMapForPrisoner(prisonerDetail3LevelPrison), sessionTemplate)).isTrue
    }

    @Test
    fun `session available to prisoner when permitted session location has matching level 1 - single locations permitted`() {
      // Given
      // allowed levels for the session are (level 1 = C)
      // and prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = null so session is available
      val allowed = AllowedSessionLocationHierarchy("C", null, null, null)

      val sessionTemplate = createSessionTemplate()

      val sessionLocationGroup = createSessionLocationGroup(levelsList = listOf(allowed), prison = sessionTemplate.prison)
      sessionTemplate.permittedSessionLocationGroups.add(sessionLocationGroup)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerService.getLevelsMapForPrisoner(prisonerDetail3LevelPrison), sessionTemplate)

      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session available to prisoner when permitted session location has matching level 1 - multiple locations permitted`() {
      // Given

      val sessionTemplate = createSessionTemplate()

      // allowed levels for the session are (level 1 = A), (level 1 =B), (level 1=C), (level 1=C)
      // and prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = null  so session is available
      val allowed1 = AllowedSessionLocationHierarchy("A", null, null, null)
      val allowed2 = AllowedSessionLocationHierarchy("B", null, null, null)
      val allowed3 = AllowedSessionLocationHierarchy("C", null, null, null)
      val allowed4 = AllowedSessionLocationHierarchy("D", null, null, null)
      val sessionLocationGroup = createSessionLocationGroup(levelsList = listOf(allowed1, allowed2, allowed3, allowed4), prison = sessionTemplate.prison)
      sessionTemplate.permittedSessionLocationGroups.add(sessionLocationGroup)

      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerService.getLevelsMapForPrisoner(prisonerDetail3LevelPrison), sessionTemplate)

      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session not available to prisoner when permitted session location does not have has matching level 1`() {
      // Given
      val sessionTemplate = createSessionTemplate()

      // allowed levels for the session are (level 1 = A), (level 1 =B), (level 1=D)
      // while prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = null so session isn't available
      val allowed1 = AllowedSessionLocationHierarchy("A", null, null, null)
      val allowed2 = AllowedSessionLocationHierarchy("B", null, null, null)
      val allowed3 = AllowedSessionLocationHierarchy("D", null, null, null)
      val allowed4 = AllowedSessionLocationHierarchy("E", null, null, null)
      val sessionLocationGroup = createSessionLocationGroup(levelsList = listOf(allowed1, allowed2, allowed3, allowed4), prison = sessionTemplate.prison)
      sessionTemplate.permittedSessionLocationGroups.add(sessionLocationGroup)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerService.getLevelsMapForPrisoner(prisonerDetail3LevelPrison), sessionTemplate)

      // Then
      assertThat(result).isFalse
    }

    @Test
    fun `session available to prisoner when permitted session location has matching level 1 and level 2 - single location permitted`() {
      // Given
      val sessionTemplate = createSessionTemplate()
      // allowed levels for the session are (level 1=C and level 2 = 1)
      // while prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = null so session isn't available
      val allowed = AllowedSessionLocationHierarchy("C", "1", null, null)
      val sessionLocationGroup = createSessionLocationGroup(levelsList = listOf(allowed), prison = sessionTemplate.prison)
      sessionTemplate.permittedSessionLocationGroups.add(sessionLocationGroup)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerService.getLevelsMapForPrisoner(prisonerDetail3LevelPrison), sessionTemplate)

      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session available to prisoner when permitted session location has matching level 1 and level 2`() {
      // Given
      val sessionTemplate = createSessionTemplate()

      // allowed levels for the session are (level 1 = A), (level 1 =B and level 2 = 1), (level 1=C and level 2 = 1 and level 1=C and level 2 = 2 and level 3 = "001")
      // and prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = null so session is available
      val allowed1 = AllowedSessionLocationHierarchy("A", null, null, null)
      val allowed2 = AllowedSessionLocationHierarchy("B", "1", null, null)
      val allowed3 = AllowedSessionLocationHierarchy("C", "1", null, null)
      val allowed4 = AllowedSessionLocationHierarchy("C", "2", "001", null)
      val sessionLocationGroup = createSessionLocationGroup(levelsList = listOf(allowed1, allowed2, allowed3, allowed4), prison = sessionTemplate.prison)
      sessionTemplate.permittedSessionLocationGroups.add(sessionLocationGroup)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerService.getLevelsMapForPrisoner(prisonerDetail3LevelPrison), sessionTemplate)

      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session not available to prisoner when permitted session location does not have matching level 2`() {
      // Given
      val sessionTemplate = createSessionTemplate()

      // allowed levels for the session are (level 1 = A), (level 1 =B and level 2 = 1), (level 1=C and level 2 = 1 and level 1=C and level 2 = 2 and level 3 = "001")
      // while prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = null so session isn't available
      val allowed1 = AllowedSessionLocationHierarchy("A", null, null, null)
      val allowed2 = AllowedSessionLocationHierarchy("B", "1", null, null)
      val allowed3 = AllowedSessionLocationHierarchy("C", "2", null, null)
      val allowed4 = AllowedSessionLocationHierarchy("C", "3", null, null)
      val sessionLocationGroup = createSessionLocationGroup(levelsList = listOf(allowed1, allowed2, allowed3, allowed4), prison = sessionTemplate.prison)
      sessionTemplate.permittedSessionLocationGroups.add(sessionLocationGroup)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerService.getLevelsMapForPrisoner(prisonerDetail3LevelPrison), sessionTemplate)

      // Then
      assertThat(result).isFalse
    }

    @Test
    fun `session available to prisoner when permitted session location has matching level 1 and level 2 and level 3`() {
      // Given
      val sessionTemplate = createSessionTemplate()

      // allowed values are level 1 = A, level 1 =B and level 2 = 1, level 3=,C, level 2 = 1 and level 3 = "001" to "007" are allowed
      // and prisoner is in C wing, level 2 = "1" and level 3 = "004" so session is available
      val allowed1 = AllowedSessionLocationHierarchy("A", null, null, null)
      val allowed2 = AllowedSessionLocationHierarchy("B", "1", null, null)
      val allowed3 = AllowedSessionLocationHierarchy("C", "1", "001", null)
      val allowed4 = AllowedSessionLocationHierarchy("C", "1", "002", null)
      val allowed5 = AllowedSessionLocationHierarchy("C", "1", "003", null)
      val allowed6 = AllowedSessionLocationHierarchy("C", "1", "004", null)
      val allowed7 = AllowedSessionLocationHierarchy("C", "1", "005", null)
      val allowed8 = AllowedSessionLocationHierarchy("C", "1", "006", null)
      val allowed9 = AllowedSessionLocationHierarchy("C", "1", "007", null)
      val sessionLocationGroup = createSessionLocationGroup(
        levelsList = listOf(
          allowed1, allowed2, allowed3, allowed4, allowed5, allowed6, allowed7, allowed8, allowed9,
        ),
        prison = sessionTemplate.prison,
      )

      sessionTemplate.permittedSessionLocationGroups.add(sessionLocationGroup)

      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerService.getLevelsMapForPrisoner(prisonerDetail3LevelPrison), sessionTemplate)
      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session not available to prisoner when permitted session location has matching level 1 and level 2 but not level 3`() {
      // Given
      val sessionTemplate = createSessionTemplate()

      // allowed levels for the session are (level 1 = A), (level 1 =B and level 2 = 2 and level 1=B and level 2 = 1 and level 3 = "004"), (level 1=C and level 2 = 1 and level 3 = "001" to "003")
      // while prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = null so session isn't available
      val allowed1 = AllowedSessionLocationHierarchy("A", null, null, null)
      val allowed2 = AllowedSessionLocationHierarchy("B", "1", null, null)
      val allowed3 = AllowedSessionLocationHierarchy("C", "1", "001", null)
      val allowed4 = AllowedSessionLocationHierarchy("C", "1", "002", null)
      val allowed5 = AllowedSessionLocationHierarchy("C", "1", "003", null)

      val sessionLocationGroup = createSessionLocationGroup(levelsList = listOf(allowed1, allowed2, allowed3, allowed4, allowed5), prison = sessionTemplate.prison)
      sessionTemplate.permittedSessionLocationGroups.add(sessionLocationGroup)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerService.getLevelsMapForPrisoner(prisonerDetail3LevelPrison), sessionTemplate)
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
      val sessionTemplate = createSessionTemplate()
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerService.getLevelsMapForPrisoner(prisonerDetail4LevelPrison), sessionTemplate)
      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session available to prisoner when permitted session location has matching level 1 - single locations permitted`() {
      // Given
      val sessionTemplate = createSessionTemplate()

      // allowed levels for the session are (level 1 = C)
      // and prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = "10000" so session is available
      val allowed = AllowedSessionLocationHierarchy("C", null, null, null)
      val sessionLocationGroup = createSessionLocationGroup(levelsList = listOf(allowed), prison = sessionTemplate.prison)
      sessionTemplate.permittedSessionLocationGroups.add(sessionLocationGroup)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerService.getLevelsMapForPrisoner(prisonerDetail4LevelPrison), sessionTemplate)
      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session available to prisoner when permitted session location has matching level 1 - multiple locations permitted`() {
      // Given
      val sessionTemplate = createSessionTemplate()

      // allowed levels for the session are (level 1 = A), (level 1 =B), (level 1=C), (level 1=C)
      // and prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = "10000"  so session is available
      val allowed1 = AllowedSessionLocationHierarchy("A", null, null, null)
      val allowed2 = AllowedSessionLocationHierarchy("B", null, null, null)
      val allowed3 = AllowedSessionLocationHierarchy("C", null, null, null)
      val allowed4 = AllowedSessionLocationHierarchy("D", null, null, null)
      val sessionLocationGroup = createSessionLocationGroup(levelsList = listOf(allowed1, allowed2, allowed3, allowed4), prison = sessionTemplate.prison)
      sessionTemplate.permittedSessionLocationGroups.add(sessionLocationGroup)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerService.getLevelsMapForPrisoner(prisonerDetail4LevelPrison), sessionTemplate)
      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session not available to prisoner when permitted session location does not have has matching level 1`() {
      // Given
      val sessionTemplate = createSessionTemplate()

      // allowed levels for the session are (level 1 = A), (level 1 =B), (level 1=D)
      // while prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = "10000" so session isn't available
      val allowed1 = AllowedSessionLocationHierarchy("A", null, null, null)
      val allowed2 = AllowedSessionLocationHierarchy("B", null, null, null)
      val allowed3 = AllowedSessionLocationHierarchy("D", null, null, null)
      val allowed4 = AllowedSessionLocationHierarchy("E", null, null, null)
      val sessionLocationGroup = createSessionLocationGroup(levelsList = listOf(allowed1, allowed2, allowed3, allowed4), prison = sessionTemplate.prison)
      sessionTemplate.permittedSessionLocationGroups.add(sessionLocationGroup)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerService.getLevelsMapForPrisoner(prisonerDetail4LevelPrison), sessionTemplate)
      // Then
      assertThat(result).isFalse
    }

    @Test
    fun `session available to prisoner when permitted session location has matching level 1 and level 2 - single location permitted`() {
      // Given
      val sessionTemplate = createSessionTemplate()

      // allowed levels for the session are (level 1=C and level 2 = 1)
      // while prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = "10000" so session isn't available
      val allowed = AllowedSessionLocationHierarchy("C", "1", null, null)
      val sessionLocationGroup = createSessionLocationGroup(levelsList = listOf(allowed), prison = sessionTemplate.prison)
      sessionTemplate.permittedSessionLocationGroups.add(sessionLocationGroup)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerService.getLevelsMapForPrisoner(prisonerDetail4LevelPrison), sessionTemplate)
      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session available to prisoner when permitted session location has matching level 1 and level 2`() {
      // Given

      val sessionTemplate = createSessionTemplate()

      // allowed levels for the session are (level 1 = A), (level 1 =B and level 2 = 1), (level 1=C and level 2 = 1 and level 1=C and level 2 = 2 and level 3 = "001")
      // and prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = "10000" so session is available
      val allowed1 = AllowedSessionLocationHierarchy("A", null, null, null)
      val allowed2 = AllowedSessionLocationHierarchy("B", "1", null, null)
      val allowed3 = AllowedSessionLocationHierarchy("C", "1", null, null)
      val allowed4 = AllowedSessionLocationHierarchy("C", "2", "001", null)
      val sessionLocationGroup = createSessionLocationGroup(levelsList = listOf(allowed1, allowed2, allowed3, allowed4), prison = sessionTemplate.prison)
      sessionTemplate.permittedSessionLocationGroups.add(sessionLocationGroup)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerService.getLevelsMapForPrisoner(prisonerDetail4LevelPrison), sessionTemplate)
      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session not available to prisoner when permitted session location does not have matching level 2`() {
      // Given

      val sessionTemplate = createSessionTemplate()

      // allowed levels for the session are (level 1 = A), (level 1 =B and level 2 = 1), (level 1=C and level 2 = 1 and level 1=C and level 2 = 2 and level 3 = "001")
      // while prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = "10000" so session isn't available
      val allowed1 = AllowedSessionLocationHierarchy("A", null, null, null)
      val allowed2 = AllowedSessionLocationHierarchy("B", "1", null, null)
      val allowed3 = AllowedSessionLocationHierarchy("C", "2", null, null)
      val allowed4 = AllowedSessionLocationHierarchy("C", "3", null, null)

      val sessionLocationGroup = createSessionLocationGroup(levelsList = listOf(allowed1, allowed2, allowed3, allowed4), prison = sessionTemplate.prison)
      sessionTemplate.permittedSessionLocationGroups.add(sessionLocationGroup)
      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerService.getLevelsMapForPrisoner(prisonerDetail4LevelPrison), sessionTemplate)
      // Then
      assertThat(result).isFalse
    }

    @Test
    fun `session available to prisoner when permitted session location has matching level 1 and level 2 and level 3`() {
      // Given

      val sessionTemplate = createSessionTemplate()

      // allowed values are level 1 = A, level 1 =B and level 2 = 1, level 3=,C, level 2 = 1 and level 3 = "001" to "007" are allowed
      // and prisoner is in C wing, level 2 = "1" and level 3 = "004" so session is available
      val allowed1 = AllowedSessionLocationHierarchy("A", null, null, null)
      val allowed2 = AllowedSessionLocationHierarchy("B", "1", null, null)
      val allowed3 = AllowedSessionLocationHierarchy("C", "1", "001", null)
      val allowed4 = AllowedSessionLocationHierarchy("C", "1", "002", null)
      val allowed5 = AllowedSessionLocationHierarchy("C", "1", "003", null)
      val allowed6 = AllowedSessionLocationHierarchy("C", "1", "004", null)
      val allowed7 = AllowedSessionLocationHierarchy("C", "1", "005", null)
      val allowed8 = AllowedSessionLocationHierarchy("C", "1", "006", null)
      val allowed9 = AllowedSessionLocationHierarchy("C", "1", "007", null)
      val sessionLocationGroup = createSessionLocationGroup(
        levelsList =
        listOf(
          allowed1, allowed2, allowed3, allowed4, allowed5, allowed6, allowed7, allowed8, allowed9,
        ),
        prison = sessionTemplate.prison,
      )

      sessionTemplate.permittedSessionLocationGroups.add(sessionLocationGroup)

      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerService.getLevelsMapForPrisoner(prisonerDetail4LevelPrison), sessionTemplate)
      // Then
      assertThat(result).isTrue
    }

    @Test
    fun `session not available to prisoner when permitted session location has matching level 1 and level 2 but not level 3`() {
      // Given
      // allowed levels for the session are (level 1 = A), (level 1 =B and level 2 = 2 and level 1=B and level 2 = 1 and level 3 = "004"), (level 1=C and level 2 = 1 and level 3 = "001" to "003")
      // while prisoner is in level 1 = "C", level 2 = "1" , level 3 = "004", level 4 = "10000" so session isn't available
      val allowed1 = AllowedSessionLocationHierarchy("A", null, null, null)
      val allowed2 = AllowedSessionLocationHierarchy("B", "2", null, null)
      val allowed3 = AllowedSessionLocationHierarchy("B", "1", "004", null)
      val allowed4 = AllowedSessionLocationHierarchy("C", "1", "001", null)
      val allowed5 = AllowedSessionLocationHierarchy("C", "1", "002", null)
      val allowed6 = AllowedSessionLocationHierarchy("C", "1", "003", null)

      val sessionTemplate = createSessionTemplate()

      val sessionLocationGroup = createSessionLocationGroup(
        levelsList = listOf(allowed1, allowed2, allowed3, allowed4, allowed5, allowed6),
        prison = sessionTemplate.prison,
      )
      sessionTemplate.permittedSessionLocationGroups.add(sessionLocationGroup)

      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerService.getLevelsMapForPrisoner(prisonerDetail4LevelPrison), sessionTemplate)
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
      val allowed1 = AllowedSessionLocationHierarchy("A", null, null, null)
      val allowed2 = AllowedSessionLocationHierarchy("B", "1", null, null)
      val allowed3 = AllowedSessionLocationHierarchy("C", "1", "001", "1000")
      val allowed4 = AllowedSessionLocationHierarchy("C", "1", "001", "4000")
      val allowed5 = AllowedSessionLocationHierarchy("C", "1", "001", "7000")
      val allowed6 = AllowedSessionLocationHierarchy("C", "1", "001", "10000")
      val allowed7 = AllowedSessionLocationHierarchy("C", "1", "002", null)
      val allowed8 = AllowedSessionLocationHierarchy("C", "1", "003", null)
      val allowed9 = AllowedSessionLocationHierarchy("C", "1", "004", null)
      val allowed10 = AllowedSessionLocationHierarchy("C", "1", "005", null)
      val allowed11 = AllowedSessionLocationHierarchy("C", "1", "006", null)
      val allowed12 = AllowedSessionLocationHierarchy("C", "1", "007", null)

      val sessionTemplate = createSessionTemplate()

      val sessionLocationGroup = createSessionLocationGroup(
        levelsList = listOf(allowed1, allowed2, allowed3, allowed4, allowed5, allowed6, allowed7, allowed8, allowed9, allowed10, allowed11, allowed12),
        prison = sessionTemplate.prison,
      )

      sessionTemplate.permittedSessionLocationGroups.add(sessionLocationGroup)

      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerService.getLevelsMapForPrisoner(prisonerDetail4LevelPrison), sessionTemplate)
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
      val allowed1 = AllowedSessionLocationHierarchy("A", null, null, null)
      val allowed2 = AllowedSessionLocationHierarchy("B", "1", null, null)
      val allowed3 = AllowedSessionLocationHierarchy("C", "1", "001", "1000")
      val allowed4 = AllowedSessionLocationHierarchy("C", "1", "001", "4000")
      val allowed5 = AllowedSessionLocationHierarchy("C", "1", "001", "7000")
      val allowed6 = AllowedSessionLocationHierarchy("C", "1", "002", null)
      val allowed7 = AllowedSessionLocationHierarchy("C", "1", "003", null)
      val allowed8 = AllowedSessionLocationHierarchy("C", "1", "004", null)
      val allowed9 = AllowedSessionLocationHierarchy("C", "1", "005", null)
      val allowed10 = AllowedSessionLocationHierarchy("C", "1", "006", null)
      val allowed11 = AllowedSessionLocationHierarchy("C", "1", "007", null)

      val sessionTemplate = createSessionTemplate()

      val sessionLocationGroup = createSessionLocationGroup(
        levelsList =
        listOf(
          allowed1, allowed2, allowed3, allowed4,
          allowed5, allowed6, allowed7, allowed8, allowed9, allowed10, allowed11,
        ),
        prison = sessionTemplate.prison,
      )

      sessionTemplate.permittedSessionLocationGroups.add(sessionLocationGroup)

      // When
      val result = prisonerSessionValidator.isSessionAvailableToPrisoner(prisonerService.getLevelsMapForPrisoner(prisonerDetail4LevelPrison), sessionTemplate)
      // Then
      assertThat(result).isTrue
    }
  }

  private fun createSessionTemplate(): SessionTemplate {
    return sessionTemplate(
      validFromDate = LocalDate.now(),
    )
  }

  private fun createSessionLocationGroup(groupName: String = "group 1", levelsList: List<AllowedSessionLocationHierarchy>, prison: Prison): SessionLocationGroup {
    val group = SessionLocationGroup(
      name = groupName,
      prisonId = prison.id,
      prison = prison,
    )

    val permittedSessionLocations = mutableListOf<PermittedSessionLocation>()
    for (level in levelsList) {
      permittedSessionLocations.add(createPermittedSessionLocation(level, group))
    }

    group.sessionLocations.addAll(permittedSessionLocations)
    return group
  }

  private fun createPermittedSessionLocation(
      allowedSessionLocationHierarchy: AllowedSessionLocationHierarchy,
      group: SessionLocationGroup,
  ): PermittedSessionLocation {
    return PermittedSessionLocation(
      groupId = group.id,
      sessionLocationGroup = group,
      levelOneCode = allowedSessionLocationHierarchy.levelOneCode,
      levelTwoCode = allowedSessionLocationHierarchy.levelTwoCode,
      levelThreeCode = allowedSessionLocationHierarchy.levelThreeCode,
      levelFourCode = allowedSessionLocationHierarchy.levelFourCode,
    )
  }

  companion object {
    const val prisonCode = "XYZ"
  }
}
