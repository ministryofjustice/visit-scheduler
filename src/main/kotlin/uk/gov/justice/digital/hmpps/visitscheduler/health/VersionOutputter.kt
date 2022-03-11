package uk.gov.justice.digital.hmpps.visitscheduler.health

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener

@Configuration
class VersionOutputter(buildProperties: BuildProperties) {
  private val version = buildProperties.version

  @EventListener(ApplicationReadyEvent::class)
  fun logVersionOnStartup() {
    log.info("Version {} started", version)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
