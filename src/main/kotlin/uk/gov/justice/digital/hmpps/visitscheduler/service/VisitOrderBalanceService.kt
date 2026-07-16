package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.client.VisitAllocationApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ConvictionStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visit.allocation.VisitOrderPrisonerBalanceDto

@Service
class VisitOrderBalanceService(
  private val visitAllocationApiClient: VisitAllocationApiClient,
) {
  companion object {
    private val logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getVOBalance(prisoner: PrisonerDto): VisitOrderPrisonerBalanceDto? {
    logger.info("Getting VO balance for prisoner {}", prisoner.prisonerId)

    return if (prisoner.convictedStatus == ConvictionStatus.CONVICTED.toString()) {
      visitAllocationApiClient.getPrisonerVOBalance(prisoner.prisonerId)
    } else {
      null
    }
  }
}
