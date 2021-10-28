package uk.gov.justice.digital.hmpps.visitscheduler.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@TestConfiguration
class TestClockConfiguration {

  /**
   * Override the primary clock resource for tests.
   *
   * @return clock
   */
  @Bean
  @Primary
  fun testClock(): Clock {
    return Clock.fixed(Instant.parse("2021-01-01T11:15:00.00Z"), ZoneId.systemDefault())
  }
}
