package uk.gov.justice.digital.hmpps.visitscheduler.client

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.visitscheduler.dto.activities.ActivitiesAppointmentInstanceDetailsDto
import java.time.Duration

@Component
class ActivitiesApiClient(
  @param:Qualifier("activitiesApiWebClient") private val webClient: WebClient,
  @param:Value("\${activities.api.timeout:10s}") private val apiTimeout: Duration,
) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    private val TYPE_FOR_APPOINTMENT_INSTANCE = object : ParameterizedTypeReference<ActivitiesAppointmentInstanceDetailsDto>() {}
  }

  fun getAppointmentInstanceDetails(appointmentInstanceId: String): ActivitiesAppointmentInstanceDetailsDto? {
    LOG.debug("Entered getAppointmentInstanceDetails for appointment instance id $appointmentInstanceId")

    return webClient.get()
      .uri("/appointment-instances/$appointmentInstanceId")
      .retrieve()
      .bodyToMono(TYPE_FOR_APPOINTMENT_INSTANCE)
      .onErrorResume { e ->
        if (!isNotFoundError(e)) {
          LOG.error("getAppointmentInstanceDetails Failed get request for appointment instance id $appointmentInstanceId")
          Mono.error(e)
        } else {
          LOG.debug("getAppointmentInstanceDetails Not Found for appointment instance id $appointmentInstanceId")
          return@onErrorResume Mono.justOrEmpty(null)
        }
      }
      .block(apiTimeout)
  }
}
