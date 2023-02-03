package uk.gov.justice.digital.hmpps.visitscheduler.helper

import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_VISIT_BY_REFERENCE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.LOCATION_GROUP_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.PRISON_LOCATION_GROUPS_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.REFERENCE_LOCATION_GROUP_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.REFERENCE_SESSION_TEMPLATE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.SESSION_TEMPLATE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_BOOK
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_CANCEL
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_CHANGE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_RESERVED_SLOT_CHANGE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_RESERVE_SLOT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ChangeVisitSlotRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ReserveVisitSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.CreateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.CreateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.UpdateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.UpdateSessionTemplateDto

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
  return callGet(webTestClient, getVisitByReferenceUrl(reference), authHttpHeaders)
}

fun callCreateSessionGroup(
  webTestClient: WebTestClient,
  dto: CreateLocationGroupDto? = null,
  authHttpHeaders: (HttpHeaders) -> Unit
): ResponseSpec {

  return callPost(
    dto,
    webTestClient,
    LOCATION_GROUP_ADMIN_PATH,
    authHttpHeaders
  )
}

fun callCreateSessionTemplate(
  webTestClient: WebTestClient,
  dto: CreateSessionTemplateDto? = null,
  authHttpHeaders: (HttpHeaders) -> Unit
): ResponseSpec {

  return callPost(
    dto,
    webTestClient,
    SESSION_TEMPLATE_PATH,
    authHttpHeaders
  )
}

fun callUpdateSessionTemplateByReference(
  webTestClient: WebTestClient,
  reference: String,
  dto: UpdateSessionTemplateDto? = null,
  authHttpHeaders: (HttpHeaders) -> Unit
): ResponseSpec {

  return callPut(
    dto,
    webTestClient,
    getSessionTemplateByReferenceUrl(reference),
    authHttpHeaders
  )
}

fun callGetGroupsByPrisonId(
  webTestClient: WebTestClient,
  prisonCode: String,
  authHttpHeaders: (HttpHeaders) -> Unit
): ResponseSpec {

  return callGet(
    webTestClient,
    getPrisonIdUrl(PRISON_LOCATION_GROUPS_ADMIN_PATH, prisonCode),
    authHttpHeaders
  )
}

fun callGetGroupByReference(
  webTestClient: WebTestClient,
  prisonCode: String,
  authHttpHeaders: (HttpHeaders) -> Unit
): ResponseSpec {

  return callGet(
    webTestClient,
    getReferenceUrl(REFERENCE_LOCATION_GROUP_ADMIN_PATH, prisonCode),
    authHttpHeaders
  )
}

fun callDeleteGroupByReference(
  webTestClient: WebTestClient,
  prisonCode: String,
  authHttpHeaders: (HttpHeaders) -> Unit
): ResponseSpec {

  return callDelete(
    webTestClient,
    getReferenceUrl(REFERENCE_LOCATION_GROUP_ADMIN_PATH, prisonCode),
    authHttpHeaders
  )
}

fun callDeleteSessionTemplateByReference(
  webTestClient: WebTestClient,
  prisonCode: String,
  authHttpHeaders: (HttpHeaders) -> Unit
): ResponseSpec {

  return callDelete(
    webTestClient,
    getReferenceUrl(REFERENCE_SESSION_TEMPLATE_PATH, prisonCode),
    authHttpHeaders
  )
}

fun callUpdateLocationSessionGroupByReference(
  webTestClient: WebTestClient,
  reference: String,
  dto: UpdateLocationGroupDto,
  authHttpHeaders: (HttpHeaders) -> Unit
): ResponseSpec {

  return callPut(
    dto,
    webTestClient,
    getSessionLocationGroupByReferenceUrl(reference),
    authHttpHeaders
  )
}

fun getPrisonIdUrl(url: String, prisonId: String): String {
  return url.replace("{prisonCode}", prisonId)
}

fun getReferenceUrl(url: String, reference: String): String {
  return url.replace("{reference}", reference)
}

fun getVisitByReferenceUrl(reference: String): String {
  return getReferenceUrl(GET_VISIT_BY_REFERENCE, reference)
}

fun getSessionTemplateByReferenceUrl(reference: String): String {
  return getReferenceUrl(REFERENCE_SESSION_TEMPLATE_PATH, reference)
}

fun getSessionLocationGroupByReferenceUrl(reference: String): String {
  return getReferenceUrl(REFERENCE_LOCATION_GROUP_ADMIN_PATH, reference)
}

fun callGet(
  webTestClient: WebTestClient,
  url: String,
  authHttpHeaders: (HttpHeaders) -> Unit
): ResponseSpec {
  return webTestClient.get().uri(url)
    .headers(authHttpHeaders)
    .exchange()
}

fun callDelete(
  webTestClient: WebTestClient,
  url: String,
  authHttpHeaders: (HttpHeaders) -> Unit
): ResponseSpec {
  return webTestClient.delete().uri(url)
    .headers(authHttpHeaders)
    .exchange()
}

fun callPut(
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

fun callPost(
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
