package uk.gov.justice.digital.hmpps.visitscheduler.helper

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_VISIT_BY_REFERENCE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_VISIT_HISTORY_CONTROLLER_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_BOOK
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_CANCEL
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_CHANGE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_NON_ASSOCIATION_CHANGE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_PRISONER_RELEASED_CHANGE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_PRISONER_RESTRICTION_CHANGE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_TYPES
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_RESERVED_SLOT_CHANGE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_RESERVE_SLOT
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.ACTIVATE_SESSION_TEMPLATE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.ADD_PRISON_EXCLUDE_DATE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.CATEGORY_GROUP_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.DEACTIVATE_SESSION_TEMPLATE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.FIND_MATCHING_SESSION_TEMPLATES_ON_CREATE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.FIND_MATCHING_SESSION_TEMPLATES_ON_UPDATE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.INCENTIVE_GROUP_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.LOCATION_GROUP_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.MOVE_VISITS
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.PRISON
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.PRISON_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.PRISON_CATEGORY_GROUPS_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.PRISON_INCENTIVE_GROUPS_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.PRISON_LOCATION_GROUPS_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.REFERENCE_CATEGORY_GROUP_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.REFERENCE_INCENTIVE_GROUP_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.REFERENCE_LOCATION_GROUP_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.REFERENCE_SESSION_TEMPLATE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.REMOVE_PRISON_EXCLUDE_DATE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.SESSION_TEMPLATE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.SESSION_TEMPLATE_VISIT_STATS
import uk.gov.justice.digital.hmpps.visitscheduler.controller.migration.MIGRATE_CANCEL
import uk.gov.justice.digital.hmpps.visitscheduler.dto.BookingRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ChangeApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigratedCancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonExcludeDateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdatePrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.CreateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.MoveVisitsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.RequestSessionTemplateVisitStatsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.UpdateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category.CreateCategoryGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category.UpdateCategoryGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.CreateIncentiveGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.UpdateIncentiveGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.CreateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.UpdateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NonAssociationChangedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerReleasedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerRestrictionChangeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethodType.PHONE
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
  cancelVisitDto: MigratedCancelVisitDto? = null,
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
  dto: ChangeApplicationDto? = null,
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
  dto: CreateApplicationDto? = null,
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

fun callApplicationForVisitChange(
  webTestClient: WebTestClient,
  authHttpHeaders: (HttpHeaders) -> Unit,
  dto: CreateApplicationDto? = null,
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
  applicationMethodType: ApplicationMethodType = PHONE,
  bookingRequestDto: BookingRequestDto = BookingRequestDto("booking_guy", applicationMethodType),

): ResponseSpec {
  return callPut(
    bodyValue = bookingRequestDto,
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
fun callCreateSessionGroup(
  webTestClient: WebTestClient,
  dto: String,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callPostByJsonString(
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

fun callCheckingMatchingTemplatesOnCreate(
  webTestClient: WebTestClient,
  dto: CreateSessionTemplateDto? = null,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callPost(
    dto,
    webTestClient,
    FIND_MATCHING_SESSION_TEMPLATES_ON_CREATE,
    authHttpHeaders,
  )
}

fun callMoveVisits(
  webTestClient: WebTestClient,
  dto: MoveVisitsDto,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callPost(
    dto,
    webTestClient,
    MOVE_VISITS,
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

fun callCheckingMatchingTemplatesOnUpdate(
  webTestClient: WebTestClient,
  reference: String,
  dto: UpdateSessionTemplateDto? = null,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callPost(
    dto,
    webTestClient,
    getCheckMatchingTemplatesByReferenceUrl(reference),
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

fun callCreateCategorySessionGroupByReference(
  webTestClient: WebTestClient,
  dto: CreateCategoryGroupDto,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callPost(
    dto,
    webTestClient,
    CATEGORY_GROUP_ADMIN_PATH,
    authHttpHeaders,
  )
}

fun callUpdateCategoryGroupByReference(
  webTestClient: WebTestClient,
  reference: String,
  dto: UpdateCategoryGroupDto,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callPut(
    dto,
    webTestClient,
    getReferenceUrl(REFERENCE_CATEGORY_GROUP_ADMIN_PATH, reference),
    authHttpHeaders,
  )
}

fun callGetIncentiveGroupsByPrisonId(webTestClient: WebTestClient, prisonCode: String, authHttpHeaders: (HttpHeaders) -> Unit): ResponseSpec {
  return callGet(
    webTestClient,
    getPrisonIdUrl(PRISON_INCENTIVE_GROUPS_ADMIN_PATH, prisonCode),
    authHttpHeaders,
  )
}

fun callGetIncentiveGroupByReference(
  webTestClient: WebTestClient,
  reference: String,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callGet(
    webTestClient,
    getReferenceUrl(REFERENCE_INCENTIVE_GROUP_ADMIN_PATH, reference),
    authHttpHeaders,
  )
}

fun callDeleteIncentiveGroupByReference(
  webTestClient: WebTestClient,
  prisonCode: String,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callDelete(
    webTestClient,
    getReferenceUrl(REFERENCE_INCENTIVE_GROUP_ADMIN_PATH, prisonCode),
    authHttpHeaders,
  )
}

fun callCreateIncentiveSessionGroupByReference(
  webTestClient: WebTestClient,
  dto: CreateIncentiveGroupDto,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callPost(
    dto,
    webTestClient,
    INCENTIVE_GROUP_ADMIN_PATH,
    authHttpHeaders,
  )
}

fun callUpdateIncentiveGroupByReference(
  webTestClient: WebTestClient,
  reference: String,
  dto: UpdateIncentiveGroupDto,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callPut(
    dto,
    webTestClient,
    getReferenceUrl(REFERENCE_INCENTIVE_GROUP_ADMIN_PATH, reference),
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

fun callGetVisitStats(
  webTestClient: WebTestClient,
  sessionTemplateReference: String,
  requestSessionTemplateVisitStatsDto: RequestSessionTemplateVisitStatsDto,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callPost(
    requestSessionTemplateVisitStatsDto,
    webTestClient,
    getReferenceUrl(SESSION_TEMPLATE_VISIT_STATS, sessionTemplateReference),
    authHttpHeaders,
  )
}

fun callActivateSessionTemplate(
  webTestClient: WebTestClient,
  sessionTemplateReference: String,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callPut(
    null,
    webTestClient,
    getReferenceUrl(ACTIVATE_SESSION_TEMPLATE, sessionTemplateReference),
    authHttpHeaders,
  )
}

fun callDeActivateSessionTemplate(
  webTestClient: WebTestClient,
  sessionTemplateReference: String,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callPut(
    null,
    webTestClient,
    getReferenceUrl(DEACTIVATE_SESSION_TEMPLATE, sessionTemplateReference),
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

fun getCheckMatchingTemplatesByReferenceUrl(reference: String): String {
  return getReferenceUrl(FIND_MATCHING_SESSION_TEMPLATES_ON_UPDATE, reference)
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
fun getAddPrisonExcludeDateUrl(prisonCode: String): String {
  return getPrisonIdUrl(ADD_PRISON_EXCLUDE_DATE, prisonCode)
}

fun getRemovePrisonExcludeDateUrl(prisonCode: String): String {
  return getPrisonIdUrl(REMOVE_PRISON_EXCLUDE_DATE, prisonCode)
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

fun callUpdatePrison(
  webTestClient: WebTestClient,
  authHttpHeaders: (HttpHeaders) -> Unit,
  prisonCode: String,
  dto: UpdatePrisonDto? = null,
): ResponseSpec {
  return callPut(
    dto,
    webTestClient,
    getGetPrisonUrl(prisonCode),
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
    PrisonExcludeDateDto(excludeDate),
    webTestClient,
    getAddPrisonExcludeDateUrl(prisonCode),
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
    PrisonExcludeDateDto(excludeDate),
    webTestClient,
    getRemovePrisonExcludeDateUrl(prisonCode),
    authHttpHeaders,
  )
}

fun callNotifyVSiPThatNonAssociationHasChanged(
  webTestClient: WebTestClient,
  authHttpHeaders: (HttpHeaders) -> Unit,
  dto: NonAssociationChangedNotificationDto? = null,
): ResponseSpec {
  return callNotifyVSiPOfAEvent(
    webTestClient,
    authHttpHeaders,
    VISIT_NOTIFICATION_NON_ASSOCIATION_CHANGE_PATH,
    dto,
  )
}

fun callCountVisitNotification(
  webTestClient: WebTestClient,
  url: String,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callGet(
    webTestClient,
    url,
    authHttpHeaders,
  )
}

fun callGetVisitNotificationTypes(
  webTestClient: WebTestClient,
  bookingReference: String,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return callGet(
    webTestClient,
    VISIT_NOTIFICATION_TYPES.replace("{reference}", bookingReference),
    authHttpHeaders,
  )
}

fun callNotifyVSiPThatPrisonerHadBeenReleased(
  webTestClient: WebTestClient,
  authHttpHeaders: (HttpHeaders) -> Unit,
  dto: PrisonerReleasedNotificationDto? = null,
): ResponseSpec {
  return callNotifyVSiPOfAEvent(
    webTestClient,
    authHttpHeaders,
    VISIT_NOTIFICATION_PRISONER_RELEASED_CHANGE_PATH,
    dto,
  )
}

fun callNotifyVSiPThatPrisonerRestrictionHasChanged(
  webTestClient: WebTestClient,
  authHttpHeaders: (HttpHeaders) -> Unit,
  dto: PrisonerRestrictionChangeNotificationDto? = null,
): ResponseSpec {
  return callNotifyVSiPOfAEvent(
    webTestClient,
    authHttpHeaders,
    VISIT_NOTIFICATION_PRISONER_RESTRICTION_CHANGE_PATH,
    dto,
  )
}

fun callNotifyVSiPOfAEvent(
  webTestClient: WebTestClient,
  authHttpHeaders: (HttpHeaders) -> Unit,
  path: String,
  anyDto: Any? = null,
): ResponseSpec {
  return callPost(anyDto, webTestClient, path, authHttpHeaders)
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

fun callPostByJsonString(
  jsonString: String,
  webTestClient: WebTestClient,
  url: String,
  authHttpHeaders: (HttpHeaders) -> Unit,
): ResponseSpec {
  return webTestClient.post().uri(url)
    .headers(authHttpHeaders)
    .contentType(MediaType.APPLICATION_JSON)
    .body(
      BodyInserters.fromValue(
        jsonString,
      ),
    )
    .exchange()
}
