package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.data.SupportTypeDto
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.SupportTypeRepository

@Service
class SupportService(
  private val supportTypeRepository: SupportTypeRepository,
) {

  fun getSupportTypes(): List<SupportTypeDto> {
    // Revisit externalising support types and content management
    return supportTypeRepository.findAll().sortedBy { it.code }.map { SupportTypeDto(it) }
  }
}
