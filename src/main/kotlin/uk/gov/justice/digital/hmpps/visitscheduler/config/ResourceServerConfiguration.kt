package uk.gov.justice.digital.hmpps.visitscheduler.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.hmpps.kotlin.auth.dsl.ResourceServerConfigurationCustomizer

@Configuration
class ResourceServerConfiguration {
  @Bean
  fun resourceServerCustomizer() = ResourceServerConfigurationCustomizer {
    // Defaults are provided by uk.gov.justice.hmpps.kotlin.auth.HmppsResourceServerConfiguration
  }
}
