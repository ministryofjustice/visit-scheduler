package uk.gov.justice.digital.hmpps.visitscheduler.task

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Scheduled task to evict cache to ensure cache is refreshed at regular intervals.
 */
@Component
class ScheduledCacheEvictTask {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Scheduled(cron = "\${cache.evict.support-types.cron:0 0 */12 * * ?}")
  @CacheEvict(value = ["support-types"], allEntries = true)
  fun evictSupportTypesCache() {
    LOG.debug("Evicting support types cache.")
  }

  @Scheduled(cron = "\${cache.evict.supported-prisons.cron:0 0 */3 * * ?}")
  @CacheEvict(value = ["supported-prisons"], allEntries = true)
  fun evictSupportedPrisonsCache() {
    LOG.debug("Evicting supported prisons cache.")
  }
}
