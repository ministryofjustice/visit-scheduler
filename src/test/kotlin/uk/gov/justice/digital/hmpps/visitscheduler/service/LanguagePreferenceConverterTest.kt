package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.notify.LanguagePreference
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.notify.LanguagePreferenceConverter

@DisplayName("Test for language preference converter")
class LanguagePreferenceConverterTest {

  private val languagePreferenceConverter = LanguagePreferenceConverter()

  @Test
  fun `when language preference is EN convert to database column returns lowercase en`() {
    val result = languagePreferenceConverter.convertToDatabaseColumn(LanguagePreference.EN)

    assertThat(result).isEqualTo("en")
  }

  @Test
  fun `when language preference is CY convert to database column returns lowercase cy`() {
    val result = languagePreferenceConverter.convertToDatabaseColumn(LanguagePreference.CY)

    assertThat(result).isEqualTo("cy")
  }

  @Test
  fun `when language preference is null convert to database column returns null`() {
    val result = languagePreferenceConverter.convertToDatabaseColumn(null)

    assertThat(result).isNull()
  }

  @Test
  fun `when database value is lowercase en convert to entity attribute returns EN`() {
    val result = languagePreferenceConverter.convertToEntityAttribute("en")

    Assertions.assertThat(result).isEqualTo(LanguagePreference.EN)
  }

  @Test
  fun `when database value is lowercase cy convert to entity attribute returns CY`() {
    val result = languagePreferenceConverter.convertToEntityAttribute("cy")

    Assertions.assertThat(result).isEqualTo(LanguagePreference.CY)
  }

  @Test
  fun `when database value is uppercase EN convert to entity attribute returns EN`() {
    val result = languagePreferenceConverter.convertToEntityAttribute("EN")

    Assertions.assertThat(result).isEqualTo(LanguagePreference.EN)
  }

  @Test
  fun `when database value is uppercase CY convert to entity attribute returns CY`() {
    val result = languagePreferenceConverter.convertToEntityAttribute("CY")

    Assertions.assertThat(result).isEqualTo(LanguagePreference.CY)
  }

  @Test
  fun `when database value is null convert to entity attribute returns null`() {
    val result = languagePreferenceConverter.convertToEntityAttribute(null)

    Assertions.assertThat(result).isNull()
  }

  @Test
  fun `when database value is invalid convert to entity attribute throws exception`() {
    val exception = assertThrows<IllegalArgumentException> {
      languagePreferenceConverter.convertToEntityAttribute("invalid")
    }

    assertThat(exception.message).isEqualTo("Invalid languagePreference: invalid")
  }
}
