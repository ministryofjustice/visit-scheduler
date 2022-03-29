package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.times
import java.util.regex.Pattern

@ExtendWith(MockitoExtension::class)
class ReferenceServiceTest {

  companion object {
    const val PATTERN_FOR_REFERENCE = "\\w{2}-\\w{2}-\\w{2}-\\w{2}"
  }

  private lateinit var referenceService: ReferenceService

  @BeforeEach
  fun setUp() {
    referenceService = ReferenceService()
  }

  @Test
  fun `a visit reference will be in the correct format`() {

    // Arrange
    val id = 1L

    // Act
    var reference = referenceService.createReference(id)

    // Assert
    assertThat(reference).matches(Pattern.compile(PATTERN_FOR_REFERENCE))
  }
}
