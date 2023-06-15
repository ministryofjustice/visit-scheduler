package uk.gov.justice.digital.hmpps.visitscheduler.helper

import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_VISIT_BY_REFERENCE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_VISIT_HISTORY_CONTROLLER_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_BOOK
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_CANCEL
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_CHANGE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_RESERVED_SLOT_CHANGE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_RESERVE_SLOT
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.ADD_PRISON_EXCLUDE_DATE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.LOCATION_GROUP_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.PRISON
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.PRISON_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.PRISON_CATEGORY_GROUPS_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.PRISON_LOCATION_GROUPS_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.REFERENCE_CATEGORY_GROUP_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.REFERENCE_LOCATION_GROUP_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.REFERENCE_SESSION_TEMPLATE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.REMOVE_PRISON_EXCLUDE_DATE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.SESSION_TEMPLATE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.migration.MIGRATE_CANCEL
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ChangeVisitSlotRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ReserveVisitSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.CreateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.UpdateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.CreateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.UpdateLocationGroupDto
import java.time.LocalDate

fun callCancelVisit(
  webTestClient: WebTestClient,
  authHttpHeaders: (HttpHeaders) -> Unit,
  reference: String,
  cancelVisitDto: CancelVisitDto? = null,
): ResponseSpec {
  return callPut(
    cancelVisitDto,
    webTestClient,
    getCancelVisitUrl(reference),
    authHttpHeaders,
  )
}

fun getCancelVisitUrl(reference: String): String {
  return VISIT_CANCEL.replace("{reference}", reference)
}

fun callMigrateCancelVisit(
  webTestClient: WebTestClient,
  authHttpHeaders: (HttpHeaders) -> Unit,
  reference: String,
  cancelVisitDto: CancelVisitDto? = null,
): ResponseSpec {
  return callPut(
    cancelVisitDto,
    webTestClient,
    getMigrateCancelVisitUrl(reference),
    authHttpHeaders,
  )
}

fun getMigrateCancelVisitUrl(reference: String): String {
  return MIGRATE_CANCEL.replace("{reference}", reference)
}

fun callVisitReserveSlotChange(
  webTestClient: WebTestClient,
  authHttpHeaders: (HttpHeaders) -> Unit,
  dto: ChangeVisitSlotRequestDto? = null,
  applicationReference: String,
): ResponseSpec {
  return callPut(
    dto,
    webTestClient,
    getVisitReserveSlotChangeUrl(applicationReference),
    authHttpHeaders,
  )
}

fun getVisitReserveSlotChangeUrl(reference: String): String {
  return VISIT_RESERVED_SLOT_CHANGE.replace("{applicationReference}", reference)
}

fun callVisitReserveSlot(
  webTestClient: WebTestClient,
  authHttpHeaders: (HttpHeaders) -> Unit,
  dto: ReserveVisitSlotDto? = null,
): ResponseSpec {
  return callPost(
    dto,
    webTestClient,
    getVisitReserveSlotUrl(),
    authHttpHeaders,
  )
}

fun getVisitReserveSlotUrl(): String {
  return VISIT_RESERVE_SLOT
}

fun callVisitChange(
  webTestClient: WebTestClient,
  authHttpHeaders: (HttpHeaders) -> Unit,
  dto: ReserveVisitSlotDto? = null,
  reference: String,
): ResponseSpec {
  return callPut(
    dto,
    webTestClient,
    getVisitChangeUrl(reference),
    authHttpHeaders,
  )
}

fun getVisitChangeUrl(reference: String): String {
  return VISIT_CHANGE.replace("{reference}", reference)
}

fun callVisitBook(
  webTestClient: WebTestClient,
  authHttpHeaders: (HttpHeaders) -> Unit,
  applicationReference: String,
): ResponseSpec {
  return callPut(
    bodyValue = null,
    webTestClient,
    getVisitBookUrl(applicationReference),
    authHttpHeaders,
  )
}

fun getVisitBookUrl(applicationReference: String): String {
  return VISIT_BOOK.replace("{applicationReference}", applicationReference)
}

fun callVisitHistoryByReference(
  webTestClient: WebTestClient,
  reference: String,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callGet(webTestClient, getVisitHistoryByReferenceUrl(reference), authHttpHeaders)
}

fun callVisitByReference(
  webTestClient: WebTestClient,
  reference: String,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callGet(webTestClient, getVisitByReferenceUrl(reference), authHttpHeaders)
}

fun callCreateSessionGroup(
  webTestClient: WebTestClient,
  dto: CreateLocationGroupDto? = null,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callPost(
    dto,
    webTestClient,
    LOCATION_GROUP_ADMIN_PATH,
    authHttpHeaders,
  )
}

fun callCreateSessionTemplate(
  webTestClient: WebTestClient,
  dto: CreateSessionTemplateDto? = null,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callPost(
    dto,
    webTestClient,
    SESSION_TEMPLATE_PATH,
    authHttpHeaders,
  )
}

fun callUpdateSessionTemplateByReference(
  webTestClient: WebTestClient,
  reference: String,
  dto: UpdateSessionTemplateDto? = null,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callPut(
    dto,
    webTestClient,
    getSessionTemplateByReferenceUrl(reference),
    authHttpHeaders,
  )
}

fun callGetGroupsByPrisonId(
  webTestClient: WebTestClient,
  prisonCode: String,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callGet(
    webTestClient,
    getPrisonIdUrl(PRISON_LOCATION_GROUPS_ADMIN_PATH, prisonCode),
    authHttpHeaders,
  )
}

fun callGetGroupByReference(
  webTestClient: WebTestClient,
  prisonCode: String,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callGet(
    webTestClient,
    getReferenceUrl(REFERENCE_LOCATION_GROUP_ADMIN_PATH, prisonCode),
    authHttpHeaders,
  )
}

fun callDeleteGroupByReference(
  webTestClient: WebTestClient,
  prisonCode: String,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callDelete(
    webTestClient,
    getReferenceUrl(REFERENCE_LOCATION_GROUP_ADMIN_PATH, prisonCode),
    authHttpHeaders,
  )
}

fun callGetCategoryGroupsByPrisonId(
  webTestClient: WebTestClient,
  prisonCode: String,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callGet(
    webTestClient,
    getPrisonIdUrl(PRISON_CATEGORY_GROUPS_ADMIN_PATH, prisonCode),
    authHttpHeaders,
  )
}

fun callGetCategoryGroupByReference(
  webTestClient: WebTestClient,
  reference: String,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callGet(
    webTestClient,
    getReferenceUrl(REFERENCE_CATEGORY_GROUP_ADMIN_PATH, reference),
    authHttpHeaders,
  )
}

fun callDeleteCategoryGroupByReference(
  webTestClient: WebTestClient,
  prisonCode: String,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callDelete(
    webTestClient,
    getReferenceUrl(REFERENCE_CATEGORY_GROUP_ADMIN_PATH, prisonCode),
    authHttpHeaders,
  )
}

fun callDeleteSessionTemplateByReference(
  webTestClient: WebTestClient,
  prisonCode: String,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callDelete(
    webTestClient,
    getReferenceUrl(REFERENCE_SESSION_TEMPLATE_PATH, prisonCode),
    authHttpHeaders,
  )
}

fun callUpdateLocationSessionGroupByReference(
  webTestClient: WebTestClient,
  reference: String,
  dto: UpdateLocationGroupDto,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callPut(
    dto,
    webTestClient,
    getSessionLocationGroupByReferenceUrl(reference),
    authHttpHeaders,
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

fun getVisitHistoryByReferenceUrl(reference: String): String {
  return getReferenceUrl(GET_VISIT_HISTORY_CONTROLLER_PATH, reference)
}

fun getSessionTemplateByReferenceUrl(reference: String): String {
  return getReferenceUrl(REFERENCE_SESSION_TEMPLATE_PATH, reference)
}

fun getSessionLocationGroupByReferenceUrl(reference: String): String {
  return getReferenceUrl(REFERENCE_LOCATION_GROUP_ADMIN_PATH, reference)
}

fun getCreatePrisonUrl(): String {
  return PRISON_ADMIN_PATH
}

fun getGetPrisonUrl(prisonCode: String): String {
  return getPrisonIdUrl(PRISON, prisonCode)
}
fun getAddPrisonExcludeDateUrl(prisonCode: String, excludeDate: LocalDate): String {
  return getPrisonIdUrl(ADD_PRISON_EXCLUDE_DATE, prisonCode).replace("{excludeDate}", excludeDate.toString())
}

fun getRemovePrisonExcludeDateUrl(prisonCode: String, excludeDate: LocalDate): String {
  return getPrisonIdUrl(REMOVE_PRISON_EXCLUDE_DATE, prisonCode).replace("{excludeDate}", excludeDate.toString())
}

fun callCreatePrison(
  webTestClient: WebTestClient,
  authHttpHeaders: (HttpHeaders) -> Unit,
  prisonDto: PrisonDto? = null,
): ResponseSpec {
  return callPost(
    prisonDto,
    webTestClient,
    getCreatePrisonUrl(),
    authHttpHeaders,
  )
}

fun callGetPrison(
  webTestClient: WebTestClient,
  authHttpHeaders: (HttpHeaders) -> Unit,
  prisonCode: String,
): ResponseSpec {
  return callGet(
    webTestClient,
    getGetPrisonUrl(prisonCode),
    authHttpHeaders,
  )
}

fun callAddPrisonExcludeDate(
  webTestClient: WebTestClient,
  authHttpHeaders: (HttpHeaders) -> Unit,
  prisonCode: String,
  excludeDate: LocalDate,
): ResponseSpec {
  return callPut(
    null,
    webTestClient,
    getAddPrisonExcludeDateUrl(prisonCode, excludeDate),
    authHttpHeaders,
  )
}

fun callRemovePrisonExcludeDate(
  webTestClient: WebTestClient,
  authHttpHeaders: (HttpHeaders) -> Unit,
  prisonCode: String,
  excludeDate: LocalDate,
): ResponseSpec {
  return callPut(
    null,
    webTestClient,
    getRemovePrisonExcludeDateUrl(prisonCode, excludeDate),
    authHttpHeaders,
  )
}

fun callGet(
  webTestClient: WebTestClient,
  url: String,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return webTestClient.get().uri(url)
    .headers(authHttpHeaders)
    .exchange()
}

fun callDelete(
  webTestClient: WebTestClient,
  url: String,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return webTestClient.delete().uri(url)
    .headers(authHttpHeaders)
    .exchange()
}

fun callPut(
  bodyValue: Any? = null,
  webTestClient: WebTestClient,
  url: String,
  authHttpHeaders: (HttpHeaders) -> Unit,
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
  authHttpHeaders: (HttpHeaders) -> Unit,
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
