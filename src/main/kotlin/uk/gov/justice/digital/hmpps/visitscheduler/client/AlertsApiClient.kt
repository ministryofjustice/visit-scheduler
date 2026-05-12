package uk.gov.justice.digital.hmpps.visitscheduler.client

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.visitscheduler.dto.alerts.AlertDto
import java.time.Duration

@Component
class AlertsApiClient(
  @param:Qualifier("alertsApiWebClient") private val webClient: WebClient,
  @param:Value("\${alerts.api.timeout:10s}") private val apiTimeout: Duration,
) {

  companion object {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val TYPE_FOR_ALERT_INSTANCE = object : ParameterizedTypeReference<AlertDto>() {}
  }

  fun getAlertByUuid(alertUuid: String): AlertDto? = webClient.get()
    .uri("/alerts/$alertUuid")
    .retrieve()
    .bodyToMono(TYPE_FOR_ALERT_INSTANCE)
    .onErrorResume { e ->
      if (!isNotFoundError(e)) {
        logger.error("getAlertByUuid Failed get request for alert Uuid $alertUuid with error: ${e.message}")
        return@onErrorResume Mono.justOrEmpty(null)
      } else {
        logger.info("getAlertByUuid Not Found get request for alert Uuid $alertUuid")
        return@onErrorResume Mono.justOrEmpty(null)
      }
    }
    .block(apiTimeout)
}
