package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class QuotableEncoderTest {

  private val encoderDefault = QuotableEncoder()

  @Nested
  @DisplayName("Default Encoder")
  inner class DefaultEncoder {

    @Test
    fun `Default encoder produces known encoding`() {
      // Given
      val input = 123456L
      val knownEncoding = "jn-bg"

      // When
      val encoded = encoderDefault.encode(input)

      // Then
      assertThat(input).isNotEqualTo(encoded)
      assertThat(encoded).isEqualTo(knownEncoding)
    }

    @Test
    fun `Default encoder produces known decoding`() {
      // Given
      val input = "jn-bg"
      val knownDecoding = 123456L

      // When
      val decoded = encoderDefault.decode(input)

      // Then
      assertThat(input).isNotEqualTo(decoded)
      assertThat(decoded).isEqualTo(knownDecoding)
    }

    @Test
    fun `encoded values are decoded`() {
      // Given
      val input = 654321L

      // When
      val encoded = encoderDefault.encode(input)
      val decoded = encoderDefault.decode(encoded)

      // Then
      assertThat(input).isNotEqualTo(encoded)
      assertThat(encoded).isNotEqualTo(decoded)
      assertThat(input).isEqualTo(decoded)
    }
  }

  @Nested
  @DisplayName("Encoder Parameters")
  inner class EncoderParameters {

    @Test
    fun `empty delimiters are permitted`() {
      // Given
      val encoder = QuotableEncoder(delimiter = "", minLength = 2, chunkSize = 1)

      val input = 1L
      // When
      val encoded = encoder.encode(input)

      // Then
      assertThat(encoded.length).isEqualTo(2)
    }

    @Test
    fun `encoded length is at least one chunk size`() {
      // Given
      val encoder = QuotableEncoder(minLength = 3, chunkSize = 10)

      val input = 1L

      // When
      val encoded = encoder.encode(input)

      // Then
      assertThat(encoded.length).isEqualTo(10)
    }

    @Test
    fun `encoded length is multiple of chunk size`() {
      // Given
      val encoder = QuotableEncoder(minLength = 5, chunkSize = 2)

      val input = 1L

      // When
      val encoded = encoder.encode(input)

      // Then
      println(encoded)
      assertThat(encoded.length % 2).isEqualTo(0)
    }

    @Test
    fun `minimum length must be greater than zero throws IllegalArgumentException`() {
      assertThrows<IllegalArgumentException> {
        QuotableEncoder(minLength = 0)
      }
    }

    @Test
    fun `minimum chunk size must be greater than zero throws IllegalArgumentException`() {
      assertThrows<IllegalArgumentException> {
        QuotableEncoder(chunkSize = 0)
      }
    }

    @Test
    fun `alpha delimiter throws IllegalArgumentException`() {
      assertThrows<IllegalArgumentException> {
        QuotableEncoder(delimiter = "a")
      }
    }

    @Test
    fun `numeric delimiter throws IllegalArgumentException`() {
      assertThrows<IllegalArgumentException> {
        QuotableEncoder(delimiter = "1")
      }
    }

    @Test
    fun `delimiter length is too long throws IllegalArgumentException`() {
      assertThrows<IllegalArgumentException> {
        QuotableEncoder(delimiter = "##")
      }
    }
  }

  @Nested
  @DisplayName("Encoder Collisions")
  inner class EncoderCollisions {

    @Test
    @Ignore
    fun `lots of hashes has no collisions`() {
      // Given
      // Not designed for testing extremely large values.
      val encoder = QuotableEncoder(minLength = 8)

      var count = 0
      for (chunkLoop in 0..1000) {
        val hashes = mutableListOf<String>()
        for (n in 0..10000) {
          // When
          hashes.add(encoder.encode(count.toLong()))
          count++
        }

        // Then
        val collisions = hashes.groupingBy { it }.eachCount().filter { it.value > 1 }
        assert(collisions.isEmpty())
      }
    }
  }
}
