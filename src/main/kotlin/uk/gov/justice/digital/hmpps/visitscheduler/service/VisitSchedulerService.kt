package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.resource.VisitDto
import javax.transaction.Transactional

@Service
@Transactional
class VisitSchedulerService(
  private val visitRepository: VisitRepository,
) {

  fun findVisits(prisonerId: String): List<VisitDto> {
    return visitRepository.findByPrisonerId(prisonerId).map { VisitDto(it) }
  }


}
