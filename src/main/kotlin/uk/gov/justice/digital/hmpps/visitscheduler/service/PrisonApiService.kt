package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OffenderNonAssociationDetailDto

@Service
class PrisonApiService(
  private val prisonApiClient: PrisonApiClient
) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getOffenderNonAssociationList(prisonerId: String): List<OffenderNonAssociationDetailDto> {
    try {
      val offenderNonAssociationList = prisonApiClient.getOffenderNonAssociation(prisonerId)?.nonAssociations ?: emptyList()
      SessionService.LOG.debug("sessionHasNonAssociation prisonerId : $prisonerId has ${offenderNonAssociationList.size} non associations!")
      return offenderNonAssociationList
    } catch (e: WebClientResponseException) {
      if (e.statusCode != HttpStatus.NOT_FOUND) {
        LOG.error("Exception thrown from call to prison API : $e")
        throw e
      }
    }

    return emptyList()
  }
}
