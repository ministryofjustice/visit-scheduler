package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_VISIT_SESSION
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.PrisonEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Get $GET_VISIT_SESSION")
class GetSessionTest : IntegrationTestBase() {

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  @Test
  fun `get session from provided information`() {
    // Given
    val prisonCode = "MDI"
    val sessionDate = LocalDate.of(2023, 1, 26)

    val sessionTemplate = sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = sessionDate,
      validToDate = sessionDate,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = sessionDate.dayOfWeek,
    )

    val prison = PrisonEntityHelper.createPrison(prisonCode)

    testSessionSlotRepository.save(SessionSlot(sessionTemplate.reference, prison.id, sessionDate, sessionDate.atTime(9, 0), sessionDate.atTime(10, 0)))

    visitEntityHelper.create(visitStatus = BOOKED, slotDate = sessionDate, sessionTemplate = sessionTemplate, visitContact = ContactDto("Jane Doe", "01111111111"), visitRestriction = VisitRestriction.OPEN)

    // When
    val responseSpec = callGetVisitSession(prisonCode, sessionDate, sessionTemplate.reference)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val visitSession = getResults(returnResult)

    Assertions.assertThat(visitSession.sessionTemplateReference).isEqualTo(sessionTemplate.reference)
    Assertions.assertThat(visitSession.openVisitCapacity).isEqualTo(sessionTemplate.openCapacity)
    Assertions.assertThat(visitSession.openVisitBookedCount).isEqualTo(1)
  }

  @Test
  fun `get session failed when incorrect information provided`() {
    // Given
    val prisonCode = "MDI"
    val sessionDate = LocalDate.of(2023, 1, 26)
    val wrongSessionDate = LocalDate.of(2022, 2, 3)


    val sessionTemplate = sessionTemplateEntityHelper.create(
      prisonCode = prisonCode,
      validFromDate = sessionDate,
      validToDate = sessionDate,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = sessionDate.dayOfWeek,
    )

    val prison = PrisonEntityHelper.createPrison(prisonCode)

    testSessionSlotRepository.save(SessionSlot(sessionTemplate.reference, prison.id, sessionDate, sessionDate.atTime(9, 0), sessionDate.atTime(10, 0)))

    visitEntityHelper.create(visitStatus = BOOKED, slotDate = sessionDate, sessionTemplate = sessionTemplate, visitContact = ContactDto("Jane Doe", "01111111111"), visitRestriction = VisitRestriction.OPEN)

    // When
    val responseSpec = callGetVisitSession(prisonCode, wrongSessionDate, sessionTemplate.reference)

    // Then
    responseSpec.expectStatus().isNotFound
  }

  private fun callGetVisitSession(
    prisonCode: String,
    sessionDate: LocalDate,
    sessionTemplateReference: String,
  ): ResponseSpec {
    return webTestClient.get().uri("$GET_VISIT_SESSION?prisonCode=$prisonCode&sessionDate=$sessionDate&sessionTemplateReference=$sessionTemplateReference")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()
  }

  private fun getResults(returnResult: BodyContentSpec): VisitSessionDto {
    return objectMapper.readValue(returnResult.returnResult().responseBody, VisitSessionDto::class.java)
  }
}
