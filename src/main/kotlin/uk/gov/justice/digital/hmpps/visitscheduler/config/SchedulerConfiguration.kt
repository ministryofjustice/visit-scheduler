package uk.gov.justice.digital.hmpps.visitscheduler.config

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling
import javax.sql.DataSource

@Configuration
@EnableScheduling
@Profile("!test") // prevent scheduler running during integration tests
@EnableSchedulerLock(
  defaultLockAtLeastFor = "PT10M",
  defaultLockAtMostFor = "PT10M"
)
class SchedulerConfiguration {
  @Bean
  fun lockProvider(dataSource: DataSource): LockProvider {
    return JdbcTemplateLockProvider(dataSource)
  }
}
