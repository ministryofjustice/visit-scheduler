package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.SupportTypeDto
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SupportTypeRepository

@Service
class SupportService(
  private val supportTypeRepository: SupportTypeRepository,
) {

  @Transactional(readOnly = true)
  fun getSupportTypes(): List<SupportTypeDto> {
    // Revisit externalising support types and content management
    return supportTypeRepository.findAll().sortedBy { it.code }.map { SupportTypeDto(it) }
  }
}
