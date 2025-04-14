package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_VISIT_REFERENCE_BY_CLIENT_REFERENCE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitByClientReference
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase

@DisplayName("GET $GET_VISIT_REFERENCE_BY_CLIENT_REFERENCE")
class VisitByClientReferenceTest : IntegrationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `Get visit by client reference`() {
    // Given

    val slotDate = sessionDatesUtil.getFirstBookableSessionDay(sessionTemplateDefault)
    val createdVisit = visitEntityHelper.create(prisonerId = "FF0000AA", visitStatus = BOOKED, slotDate = slotDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    val clientReference = "TESTCLIENTREF1"

    visitEntityHelper.createVisitExternalSystemClientReference(createdVisit, clientReference)
    visitEntityHelper.save(createdVisit)

    val reference = createdVisit.reference

    // When
    val responseSpec = callVisitByClientReference(webTestClient, clientReference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
      .expectBody().json("[$reference]")
  }

  @Test
  fun `Get visit by client reference with multiple visits against clientRef`() {
    // Given

    val slotDate = sessionDatesUtil.getFirstBookableSessionDay(sessionTemplateDefault)
    val createdVisit = visitEntityHelper.create(prisonerId = "FF0000AA", visitStatus = BOOKED, slotDate = slotDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    val createdVisit1 = visitEntityHelper.create(prisonerId = "FF0000AB", visitStatus = BOOKED, slotDate = slotDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("John Doe", "01111111111", "email1@example.com"))

    val clientReference = "TESTCLIENTREF2"

    visitEntityHelper.createVisitExternalSystemClientReference(createdVisit, clientReference)
    visitEntityHelper.createVisitExternalSystemClientReference(createdVisit1, clientReference)
    visitEntityHelper.save(createdVisit)
    visitEntityHelper.save(createdVisit1)
    val reference = createdVisit.reference
    val reference1 = createdVisit1.reference

    // When
    val responseSpec = callVisitByClientReference(webTestClient, clientReference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
      .expectBody().json("[$reference, $reference1]")
  }

  @Test
  fun `Get 404 when no visit associated with client reference found`() {
    // Given

    val slotDate = sessionDatesUtil.getFirstBookableSessionDay(sessionTemplateDefault)
    val createdVisit = visitEntityHelper.create(prisonerId = "FF0000AA", visitStatus = BOOKED, slotDate = slotDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    val clientReference = "TESTCLIENTREF2"

    visitEntityHelper.save(createdVisit)

    // When
    val responseSpec = callVisitByClientReference(webTestClient, clientReference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isNotFound
  }
}
