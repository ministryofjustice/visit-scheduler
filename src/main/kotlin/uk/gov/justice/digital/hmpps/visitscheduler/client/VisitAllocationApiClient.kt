package uk.gov.justice.digital.hmpps.visitscheduler.client

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visit.allocation.VisitOrderPrisonerBalanceDto
import java.time.Duration

@Component
class VisitAllocationApiClient(
  @param:Qualifier("visitAllocationApiWebClient") private val webClient: WebClient,
  @param:Value("\${visit-allocation.api.timeout:10s}") private val apiTimeout: Duration,
) {
  companion object {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)
    const val VO_BALANCE_ENDPOINT = "/visits/allocation/prisoner/{prisonerId}/balance"
    private val VISIT_ORDER_BALANCE_DTO = object : ParameterizedTypeReference<VisitOrderPrisonerBalanceDto>() {}
  }

  fun getPrisonerVOBalance(prisonerId: String): VisitOrderPrisonerBalanceDto? {
    val uri = VO_BALANCE_ENDPOINT.replace("{prisonerId}", prisonerId)
    return webClient.get()
      .uri(uri)
      .retrieve()
      .bodyToMono(VISIT_ORDER_BALANCE_DTO)
      .onErrorResume { e ->
        if (!isNotFoundError(e)) {
          logger.error("getPrisonerVOBalance Failed for prisoner Id - $prisonerId")
          Mono.error(e)
        } else {
          logger.debug("getPrisonerVOBalance Not Found for prisoner Id - $prisonerId")
          return@onErrorResume Mono.justOrEmpty(null)
        }
      }
      .block(apiTimeout)
  }
}
