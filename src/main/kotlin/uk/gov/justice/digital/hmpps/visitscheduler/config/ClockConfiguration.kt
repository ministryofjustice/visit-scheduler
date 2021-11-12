package uk.gov.justice.digital.hmpps.visitscheduler.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class ClockConfiguration {

  /**
   * Provide a Clock instance. This is an external source of time, so effectively a read-only repository.
   *
   * @return clock
   */
  @Bean
  fun clock(): Clock {
    return Clock.systemDefaultZone()
  }
}
