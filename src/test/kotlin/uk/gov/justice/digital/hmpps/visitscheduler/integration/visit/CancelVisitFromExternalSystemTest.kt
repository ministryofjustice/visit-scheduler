package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import tools.jackson.databind.node.ObjectNode
import uk.gov.justice.digital.hmpps.visitscheduler.controller.CANCEL_VISIT_FROM_EXTERNAL_SYSTEM
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitFromExternalSystemDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.SnsService
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryClientService
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitNotificationTransactionalService
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("PUT $CANCEL_VISIT_FROM_EXTERNAL_SYSTEM")
class CancelVisitFromExternalSystemTest : IntegrationTestBase() {
  @Autowired
  private lateinit var testVisitRepository: TestVisitRepository

  @MockitoSpyBean
  private lateinit var snsService: SnsService

  @MockitoSpyBean
  private lateinit var telemetryClientService: TelemetryClientService

  @MockitoSpyBean
  private lateinit var visitNotificationTransactionalService: VisitNotificationTransactionalService

  private val request = CancelVisitFromExternalSystemDto(
    clientName = "MDI",
    cancelOutcome = OutcomeDto(OutcomeStatus.PRISONER_CANCELLED, "No longer required"),
    actionedBy = "external-staff",
    userType = UserType.STAFF,
    applicationMethodType = ApplicationMethodType.PHONE,
  )

  @ParameterizedTest
  @EnumSource(value = VisitType::class, names = ["SOCIAL", "SOCIAL_VIDEO"])
  fun `Cancel an external visit when clientName matches the owner`(visitType: VisitType) {
    val visit = createVisit(visitType = visitType)

    val response = cancel(visit.reference).expectStatus().isOk.expectBody().returnResult()
    val cancelledVisit = objectMapper.readValue(response.responseBody, VisitDto::class.java)
    assertThat(cancelledVisit.visitStatus).isEqualTo(VisitStatus.CANCELLED)
    assertThat(cancelledVisit.visitType).isEqualTo(visitType)
    assertThat(cancelledVisit.visitExternalSystemDetails?.clientName).isEqualTo("MDI")
    assertThat(cancelledVisit.visitExternalSystemDetails?.clientVisitReference).isEqualTo("external-reference")
    assertThat(testVisitRepository.findByReference(visit.reference).outcomeStatus).isEqualTo(OutcomeStatus.PRISONER_CANCELLED)
    assertHelper.assertVisitCancellation(cancelledVisit, OutcomeStatus.PRISONER_CANCELLED, request.actionedBy, request.applicationMethodType)
    verify(snsService).sendVisitCancelledEvent(any())
    verify(telemetryClientService).trackCancelBookingEvent(any(), any(), any())
  }

  @Test
  fun `Cancel an external visit twice creates only one audit and event`() {
    val visit = createVisit()
    cancel(visit.reference).expectStatus().isOk
    cancel(visit.reference).expectStatus().isOk

    assertThat(eventAuditRepository.findAllByBookingReference(visit.reference)).hasSize(1)
    verify(snsService, times(1)).sendVisitCancelledEvent(any())
    verify(telemetryClientService, times(1)).trackCancelBookingEvent(any(), any(), any())
  }

  @ParameterizedTest
  @EnumSource(value = UserType::class, names = ["STAFF", "PRISONER"])
  fun `Cancel an external visit returns 400 when actionedBy exceeds 60 characters`(userType: UserType) {
    val visit = createVisit()
    cancel(visit.reference, request.copy(userType = userType, actionedBy = "s".repeat(61)))
      .expectStatus().isBadRequest
    assertVisitNotCancelled(visit)
  }

  @ParameterizedTest
  @EnumSource(value = UserType::class, names = ["STAFF", "PRISONER"])
  fun `Cancel an external visit accepts actionedBy with 60 characters`(userType: UserType) {
    val visit = createVisit()
    val cancellation = request.copy(userType = userType, actionedBy = "s".repeat(60))

    cancel(visit.reference, cancellation).expectStatus().isOk

    assertThat(testVisitRepository.findByReference(visit.reference).visitStatus).isEqualTo(VisitStatus.CANCELLED)
    assertThat(eventAuditRepository.findAllByBookingReference(visit.reference)).hasSize(1)
    verify(snsService).sendVisitCancelledEvent(any())
  }

  @ParameterizedTest
  @EnumSource(value = UserType::class, names = ["PUBLIC", "SYSTEM"])
  fun `Cancel an external visit accepts actionedBy exceeding 60 characters for public or system users`(userType: UserType) {
    val visit = createVisit()
    cancel(visit.reference, request.copy(userType = userType, actionedBy = "s".repeat(61))).expectStatus().isOk

    assertThat(testVisitRepository.findByReference(visit.reference).visitStatus).isEqualTo(VisitStatus.CANCELLED)
    assertThat(eventAuditRepository.findAllByBookingReference(visit.reference)).hasSize(1)
    verify(snsService).sendVisitCancelledEvent(any())
  }

  @ParameterizedTest
  @ValueSource(strings = ["another-client", "mdi", " MDI ", "external-reference"])
  fun `Cancel an external visit returns 403 when clientName does not match the owner`(clientName: String) {
    val visit = createVisit()
    cancel(visit.reference, request.copy(clientName = clientName)).expectStatus().isForbidden
    assertVisitNotCancelled(visit)
  }

  @Test
  fun `Cancel an already cancelled external visit still checks clientName`() {
    val visit = createVisit(visitStatus = VisitStatus.CANCELLED)
    cancel(visit.reference, request.copy(clientName = "another-client")).expectStatus().isForbidden
    assertVisitNotCancelled(visit)
  }

  @Test
  fun `Cancel an external visit returns 403 when the visit has no external client`() {
    val visit = createVisit(external = false)
    cancel(visit.reference).expectStatus().isForbidden
    assertVisitNotCancelled(visit)
  }

  @ParameterizedTest
  @ValueSource(strings = ["missing", "null", "", " ", "\t"])
  fun `Cancel an external visit returns 400 when clientName is missing or blank`(clientName: String) {
    val visit = createVisit()
    val json = objectMapper.valueToTree(request) as ObjectNode
    when (clientName) {
      "missing" -> json.remove("clientName")
      "null" -> json.putNull("clientName")
      else -> json.put("clientName", clientName)
    }

    cancelJson(visit.reference, objectMapper.writeValueAsString(json)).expectStatus().isBadRequest
    assertVisitNotCancelled(visit)
  }

  @Test
  fun `Cancel an external visit returns 400 when the outcome is invalid`() {
    val visit = createVisit()
    val json = objectMapper.valueToTree(request) as ObjectNode
    json.putObject("cancelOutcome").put("outcomeStatus", "INVALID")
    cancelJson(visit.reference, objectMapper.writeValueAsString(json)).expectStatus().isBadRequest
    assertVisitNotCancelled(visit)
  }

  @Test
  fun `Cancel an external visit returns 404 when the visit does not exist`() {
    cancel("missing-reference").expectStatus().isNotFound
    verify(snsService, never()).sendVisitCancelledEvent(any())
  }

  @Test
  fun `Cancel an external visit returns 403 without the required role`() {
    val visit = createVisit()
    webTestClient.put().uri(url(visit.reference))
      .headers(setAuthorisation(roles = emptyList()))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(request)
      .exchange()
      .expectStatus().isForbidden
    assertVisitNotCancelled(visit)
  }

  @Test
  fun `Cancel an external visit returns 401 without a token`() {
    val visit = createVisit()
    webTestClient.put().uri(url(visit.reference))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(request)
      .exchange()
      .expectStatus().isUnauthorized
    assertVisitNotCancelled(visit)
  }

  private fun cancel(reference: String, body: CancelVisitFromExternalSystemDto = request) = cancelJson(reference, objectMapper.writeValueAsString(body))

  private fun cancelJson(reference: String, json: String) = webTestClient.put().uri(url(reference))
    .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(json)
    .exchange()

  private fun url(reference: String) = CANCEL_VISIT_FROM_EXTERNAL_SYSTEM.replace("{reference}", reference)

  private fun assertVisitNotCancelled(visit: Visit) {
    assertThat(testVisitRepository.findByReference(visit.reference).visitStatus).isEqualTo(visit.visitStatus)
    assertThat(eventAuditRepository.findAllByBookingReference(visit.reference)).isEmpty()
    verify(snsService, never()).sendVisitCancelledEvent(any())
    verify(telemetryClientService, never()).trackCancelBookingEvent(any(), any(), any())
    verify(visitNotificationTransactionalService, never()).deleteVisitAndPairedNotificationEvents(any(), any(), any())
  }

  private fun createVisit(
    visitType: VisitType = VisitType.SOCIAL,
    visitStatus: VisitStatus = VisitStatus.BOOKED,
    external: Boolean = true,
  ): Visit {
    val visit = visitEntityHelper.create(
      prisonCode = "MKI",
      visitRoom = "A1",
      slotDate = LocalDate.now().plusDays(2),
      visitStart = LocalTime.of(9, 0),
      visitEnd = LocalTime.of(10, 0),
      visitType = visitType,
      visitStatus = visitStatus,
      visitSubStatus = if (visitStatus == VisitStatus.CANCELLED) VisitSubStatus.CANCELLED else VisitSubStatus.AUTO_APPROVED,
      activePrison = true,
      createContact = true,
      createApplication = false,
    )
    if (external) {
      visitEntityHelper.createVisitExternalSystemClientReference(visit, "external-reference")
    }
    return visitEntityHelper.save(visit)
  }
}
