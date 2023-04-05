package uk.gov.justice.digital.hmpps.visitscheduler.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TransitionalLocationTypesTest {

  @Test
  fun `when given type is lower case string compares correctly`() {
    // Given
    val type = TransitionalLocationTypes.RECP.name.lowercase()

    // When
    val result = TransitionalLocationTypes.contains(type)

    // Then
    assertThat(result).isTrue
  }

  @Test
  fun `when given type is null string compares correctly`() {
    // Given
    val type: String ? = null

    // When
    val result = TransitionalLocationTypes.contains(type)

    // Then
    assertThat(result).isFalse
  }

  @Test
  fun `when given type not temporary string compares correctly`() {
    // Given
    val type = "C"

    // When
    val result = TransitionalLocationTypes.contains(type)

    // Then
    assertThat(result).isFalse
  }
}
