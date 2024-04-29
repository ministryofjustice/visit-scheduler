package uk.gov.justice.digital.hmpps.visitscheduler.health

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.info.BuildProperties
import java.util.*

class HealthInfoTest {
  @Test
  fun `should include version info`() {
    // Given
    val properties = Properties()
    properties.setProperty("version", "somever")

    // When
    val health = HealthInfo(BuildProperties(properties)).health()

    // Then
    Assertions.assertThat(health.details)
      .isEqualTo(mapOf("version" to "somever"))
  }
}
