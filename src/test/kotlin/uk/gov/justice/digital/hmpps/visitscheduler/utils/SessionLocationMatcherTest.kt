package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.PermittedSessionLocationDto
import uk.gov.justice.digital.hmpps.visitscheduler.utils.matchers.SessionLocationMatcher

class SessionLocationMatcherTest {
  private val sessionLocationMatcher = SessionLocationMatcher()

  @Test
  fun `when both sets are empty has all lower or equal match is true`() {
    // Given
    val oldSessionLocations = emptySet<PermittedSessionLocationDto>()
    val newSessionLocations = emptySet<PermittedSessionLocationDto>()

    // When
    val result = sessionLocationMatcher.hasAllLowerOrEqualMatch(oldSessionLocations, newSessionLocations)
    // Then
    assertThat(result).isTrue()
  }

  @Test
  fun `when scope is widened has all lower or equal match is true`() {
    // Given
    val oldSessionLocations = setOf(
      PermittedSessionLocationDto(levelOneCode = "A"),
    )
    val newSessionLocations = emptySet<PermittedSessionLocationDto>()

    // When
    val result = sessionLocationMatcher.hasAllLowerOrEqualMatch(oldSessionLocations, newSessionLocations)
    // Then
    assertThat(result).isTrue()
  }

  @Test
  fun `when scope is widened - 1 level - has all lower or equal match is true`() {
    // Given
    val oldSessionLocations = setOf(
      PermittedSessionLocationDto(levelOneCode = "A", levelTwoCode = "1"),
    )
    val newSessionLocations = setOf(
      PermittedSessionLocationDto(levelOneCode = "A"),
    )

    // When
    val result = sessionLocationMatcher.hasAllLowerOrEqualMatch(oldSessionLocations, newSessionLocations)
    // Then
    assertThat(result).isTrue()
  }

  @Test
  fun `when scope is widened - 2 levels - has all lower or equal match is true`() {
    // Given
    val oldSessionLocations = setOf(
      PermittedSessionLocationDto(levelOneCode = "A", levelTwoCode = "1", levelThreeCode = "100"),
    )
    val newSessionLocations = setOf(
      PermittedSessionLocationDto(levelOneCode = "A", levelTwoCode = "1"),
    )

    // When
    val result = sessionLocationMatcher.hasAllLowerOrEqualMatch(oldSessionLocations, newSessionLocations)
    // Then
    assertThat(result).isTrue()
  }

  @Test
  fun `when scope is widened - 3 levels - has all lower or equal match is true`() {
    // Given
    val oldSessionLocations = setOf(
      PermittedSessionLocationDto(levelOneCode = "A", levelTwoCode = "1", levelThreeCode = "100", levelFourCode = "1"),
    )
    val newSessionLocations = setOf(
      PermittedSessionLocationDto(levelOneCode = "A", levelTwoCode = "1", levelThreeCode = "100"),
    )

    // When
    val result = sessionLocationMatcher.hasAllLowerOrEqualMatch(oldSessionLocations, newSessionLocations)
    // Then
    assertThat(result).isTrue()
  }

  @Test
  fun `when scope is reduced has all lower or equal match is false`() {
    // Given
    val oldSessionLocations = emptySet<PermittedSessionLocationDto>()
    val newSessionLocations = setOf(
      PermittedSessionLocationDto(levelOneCode = "A"),
    )

    // When
    val result = sessionLocationMatcher.hasAllLowerOrEqualMatch(oldSessionLocations, newSessionLocations)
    // Then
    assertThat(result).isFalse()
  }

  @Test
  fun `when scope is reduced - 1 level - has all lower or equal match is false`() {
    // Given
    val oldSessionLocations = setOf(
      PermittedSessionLocationDto(levelOneCode = "A"),
    )
    val newSessionLocations = setOf(
      PermittedSessionLocationDto(levelOneCode = "A", levelTwoCode = "1"),
    )

    // When
    val result = sessionLocationMatcher.hasAllLowerOrEqualMatch(oldSessionLocations, newSessionLocations)
    // Then
    assertThat(result).isFalse()
  }

  @Test
  fun `when scope is reduced - 2 levels - has all lower or equal match is false`() {
    // Given
    val oldSessionLocations = setOf(
      PermittedSessionLocationDto(levelOneCode = "A", levelTwoCode = "1"),
    )
    val newSessionLocations = setOf(
      PermittedSessionLocationDto(levelOneCode = "A", levelTwoCode = "1", levelThreeCode = "100"),
    )

    // When
    val result = sessionLocationMatcher.hasAllLowerOrEqualMatch(oldSessionLocations, newSessionLocations)
    // Then
    assertThat(result).isFalse()
  }

  @Test
  fun `when scope is reduced - 3 levels - has all lower or equal match is false`() {
    // Given
    val oldSessionLocations = setOf(
      PermittedSessionLocationDto(levelOneCode = "A", levelTwoCode = "1", levelThreeCode = "100"),
    )
    val newSessionLocations = setOf(
      PermittedSessionLocationDto(levelOneCode = "A", levelTwoCode = "1", levelThreeCode = "100", levelFourCode = "1"),
    )

    // When
    val result = sessionLocationMatcher.hasAllLowerOrEqualMatch(oldSessionLocations, newSessionLocations)
    // Then
    assertThat(result).isFalse()
  }

  @Test
  fun `when scope is changed has all lower or equal match is false`() {
    // Given
    val oldSessionLocations = setOf(
      PermittedSessionLocationDto(levelOneCode = "A"),
    )
    val newSessionLocations = setOf(
      PermittedSessionLocationDto(levelOneCode = "B"),
    )

    // When
    val result = sessionLocationMatcher.hasAllLowerOrEqualMatch(oldSessionLocations, newSessionLocations)
    // Then
    assertThat(result).isFalse()
  }

  @Test
  fun `when scope is changed - 1 level - has all lower or equal match is false`() {
    // Given
    val oldSessionLocations = setOf(
      PermittedSessionLocationDto(levelOneCode = "A", levelTwoCode = "1"),
    )
    val newSessionLocations = setOf(
      PermittedSessionLocationDto(levelOneCode = "A", levelTwoCode = "2"),
    )

    // When
    val result = sessionLocationMatcher.hasAllLowerOrEqualMatch(oldSessionLocations, newSessionLocations)
    // Then
    assertThat(result).isFalse()
  }

  @Test
  fun `when scope is changed - 2 levels - has all lower or equal match is false`() {
    // Given
    val oldSessionLocations = setOf(
      PermittedSessionLocationDto(levelOneCode = "A", levelTwoCode = "1", levelThreeCode = "100"),
    )
    val newSessionLocations = setOf(
      PermittedSessionLocationDto(levelOneCode = "A", levelTwoCode = "1", levelThreeCode = "200"),
    )

    // When
    val result = sessionLocationMatcher.hasAllLowerOrEqualMatch(oldSessionLocations, newSessionLocations)
    // Then
    assertThat(result).isFalse()
  }

  @Test
  fun `when scope is changed - 3 levels - has all lower or equal match is false`() {
    // Given
    val oldSessionLocations = setOf(
      PermittedSessionLocationDto(levelOneCode = "A", levelTwoCode = "1", levelThreeCode = "100", levelFourCode = "1"),
    )
    val newSessionLocations = setOf(
      PermittedSessionLocationDto(levelOneCode = "A", levelTwoCode = "1", levelThreeCode = "100", levelFourCode = "2"),
    )

    // When
    val result = sessionLocationMatcher.hasAllLowerOrEqualMatch(oldSessionLocations, newSessionLocations)
    // Then
    assertThat(result).isFalse()
  }
}
