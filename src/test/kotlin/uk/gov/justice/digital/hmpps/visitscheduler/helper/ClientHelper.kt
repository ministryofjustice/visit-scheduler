package uk.gov.justice.digital.hmpps.visitscheduler.helper

import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_VISIT_BY_REFERENCE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_BOOK
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_CANCEL
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_CHANGE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_RESERVED_SLOT_CHANGE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_RESERVE_SLOT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ChangeVisitSlotRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ReserveVisitSlotDto

fun callCancelVisit(
  webTestClient: WebTestClient,
  authHttpHeaders: (HttpHeaders) -> Unit,
  reference: String,
  outcome: OutcomeDto? = null
): ResponseSpec {

  return callPut(
    outcome,
    webTestClient,
    getCancelVisitUrl(reference),
    authHttpHeaders
  )
}

fun getCancelVisitUrl(reference: String): String {
  return VISIT_CANCEL.replace("{reference}", reference)
}

fun callVisitReserveSlotChange(
  webTestClient: WebTestClient,
  authHttpHeaders: (HttpHeaders) -> Unit,
  dto: ChangeVisitSlotRequestDto? = null,
  applicationReference: String
): ResponseSpec {

  return callPut(
    dto,
    webTestClient,
    getVisitReserveSlotChangeUrl(applicationReference),
    authHttpHeaders
  )
}

fun getVisitReserveSlotChangeUrl(reference: String): String {
  return VISIT_RESERVED_SLOT_CHANGE.replace("{applicationReference}", reference)
}

fun callVisitReserveSlot(
  webTestClient: WebTestClient,
  authHttpHeaders: (HttpHeaders) -> Unit,
  dto: ReserveVisitSlotDto? = null
): ResponseSpec {

  return callPost(
    dto,
    webTestClient,
    getVisitReserveSlotUrl(),
    authHttpHeaders
  )
}

fun getVisitReserveSlotUrl(): String {
  return VISIT_RESERVE_SLOT
}

fun callVisitChange(
  webTestClient: WebTestClient,
  authHttpHeaders: (HttpHeaders) -> Unit,
  dto: ReserveVisitSlotDto? = null,
  reference: String
): ResponseSpec {

  return callPut(
    dto,
    webTestClient,
    getVisitChangeUrl(reference),
    authHttpHeaders
  )
}

fun getVisitChangeUrl(reference: String): String {
  return VISIT_CHANGE.replace("{reference}", reference)
}

fun callVisitBook(
  webTestClient: WebTestClient,
  authHttpHeaders: (HttpHeaders) -> Unit,
  applicationReference: String
): ResponseSpec {

  return callPut(
    bodyValue = null,
    webTestClient,
    getVisitBookUrl(applicationReference),
    authHttpHeaders
  )
}

fun getVisitBookUrl(applicationReference: String): String {
  return VISIT_BOOK.replace("{applicationReference}", applicationReference)
}

fun callVisitByReference(
  webTestClient: WebTestClient,
  reference: String,
  authHttpHeaders: (HttpHeaders) -> Unit
): ResponseSpec {
  return webTestClient.get().uri(getVisitByReferenceUrl(reference))
    .headers(authHttpHeaders)
    .exchange()
}

fun getVisitByReferenceUrl(reference: String): String {
  return GET_VISIT_BY_REFERENCE.replace("{reference}", reference)
}

private fun callPut(
  bodyValue: Any? = null,
  webTestClient: WebTestClient,
  url: String,
  authHttpHeaders: (HttpHeaders) -> Unit
): ResponseSpec {
  return if (bodyValue == null) {
    webTestClient.put().uri(url)
      .headers(authHttpHeaders)
      .exchange()
  } else {
    webTestClient.put().uri(url)
      .headers(authHttpHeaders)
      .body(BodyInserters.fromValue(bodyValue))
      .exchange()
  }
}

private fun callPost(
  bodyValue: Any? = null,
  webTestClient: WebTestClient,
  url: String,
  authHttpHeaders: (HttpHeaders) -> Unit
): ResponseSpec {
  return if (bodyValue == null) {
    webTestClient.post().uri(url)
      .headers(authHttpHeaders)
      .exchange()
  } else {
    webTestClient.post().uri(url)
      .headers(authHttpHeaders)
      .body(BodyInserters.fromValue(bodyValue))
      .exchange()
  }
}
