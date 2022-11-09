package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository

@Service
@Transactional
class ConfigService(
  private val prisonRepository: PrisonRepository,
) {

  fun getSupportedPrisons(): List<String> {
    return prisonRepository.getSupportedPrisons()
  }
}
