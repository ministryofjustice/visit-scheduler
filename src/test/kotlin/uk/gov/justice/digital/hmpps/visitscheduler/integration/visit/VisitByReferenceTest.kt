package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_VISIT_BY_REFERENCE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitByReference
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import java.time.LocalDate
import java.time.LocalDateTime

@DisplayName("GET $GET_VISIT_BY_REFERENCE")
class VisitByReferenceTest : IntegrationTestBase() {

  private val visitTime: LocalDateTime = LocalDateTime.of(LocalDate.now().year + 1, 11, 1, 12, 30, 44)

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
    sessionTemplateDefault = sessionTemplateEntityHelper.create(startTime = visitTime.toLocalTime(), endTime = visitTime.plusHours(1).toLocalTime())
  }

  @Test
  fun `Booked visit by reference`() {
    // Given

    val slotDate = sessionDatesUtil.getFirstBookableSessionDay(sessionTemplateDefault)
    val createdVisit = visitEntityHelper.create(prisonerId = "FF0000AA", visitStatus = BOOKED, slotDate = slotDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111"))

    val reference = createdVisit.reference

    // When
    val responseSpec = callVisitByReference(webTestClient, reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.reference").isEqualTo(reference)
  }

  @Test
  fun `when booked visit has no contact telephone get visit by reference returns contact phone as null`() {
    // Given

    val slotDate = sessionDatesUtil.getFirstBookableSessionDay(sessionTemplateDefault)
    val createdVisit = visitEntityHelper.create(prisonerId = "FF0000AA", visitStatus = BOOKED, slotDate = slotDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Test User", null))

    val reference = createdVisit.reference

    // When
    val responseSpec = callVisitByReference(webTestClient, reference, roleVisitSchedulerHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
    val visit = getVisitDto(returnResult)
    assertThat(visit.visitContact).isNotNull()
    assertThat(visit.visitContact.name).isEqualTo("Test User")
    assertThat(visit.visitContact.telephone).isNull()
  }

  @Test
  fun `Canceled visit by reference`() {
    // Given
    val slotDate = sessionDatesUtil.getFirstBookableSessionDay(sessionTemplateDefault)
    val createdVisit = visitEntityHelper.create(prisonerId = "FF0000AA", visitStatus = CANCELLED, slotDate = slotDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111"))

    val reference = createdVisit.reference

    // When
    val responseSpec = callVisitByReference(webTestClient, reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.reference").isEqualTo(reference)
  }

  @Test
  fun `Visit by reference - not found`() {
    // Given
    val reference = "12345"

    // When
    val responseSpec = callVisitByReference(webTestClient, reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isNotFound
  }
}
