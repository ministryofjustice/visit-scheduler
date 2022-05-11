package uk.gov.justice.digital.hmpps.visitscheduler.validation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class NullableNotEmptyValidatorTest {

  private val validator = NullableNotEmptyValidator()

  @Test
  fun `should find no validation error, if value is null`() {
    assertTrue(validator.isValid(value = null, context = null))
  }

  @Test
  fun `should return validation error, if value is empty`() {
    assertFalse(validator.isValid(value = listOf(), context = null))
  }

  @Test
  fun `should find no validation error, if value contains at least one element`(){
    assertTrue(validator.isValid(value = listOf(1,2,3), context = null))
  }

}