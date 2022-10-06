package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateSessionTemplateRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.TestClockConfiguration
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import java.time.DayOfWeek.MONDAY
import java.time.LocalDate
import java.time.LocalTime

@Import(TestClockConfiguration::class)
@DisplayName("POST /visit-session-templates")
class CreateSessionTemplate(@Autowired private val objectMapper: ObjectMapper) : IntegrationTestBase() {

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @AfterEach
  internal fun deleteAllSessionTemplates() = sessionTemplateEntityHelper.deleteAll()

  private val createSessionTemplateRequest = CreateSessionTemplateRequestDto(
    prisonId = "LEI",
    startTime = LocalTime.of(14, 30),
    endTime = LocalTime.of(16, 30),
    validFromDate = LocalDate.of(2021, 1, 1),
    validToDate = LocalDate.of(2021, 4, 1),
    visitRoom = "A1",
    visitType = VisitType.SOCIAL,
    openCapacity = 5,
    closedCapacity = 2,
    dayOfWeek = MONDAY
  )

  @Test
  fun `create session template`() {

    // Given
    val bodyInsert = BodyInserters.fromValue(
      createSessionTemplateRequest
    )
    val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

    // When
    val responseSpec = webTestClient.post().uri("/visit-session-templates")
      .headers(setAuthorisation(roles = requiredRole))
      .body(bodyInsert)
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isCreated
      .expectBody()
      .jsonPath("$.sessionTemplateId").isNumber
      .jsonPath("$.prisonId").isEqualTo("LEI")
      .jsonPath("$.startTime").isEqualTo("14:30:00")
      .jsonPath("$.endTime").isEqualTo("16:30:00")
      .jsonPath("$.openCapacity").isEqualTo(5)
      .jsonPath("$.closedCapacity").isEqualTo(2)
      .jsonPath("$.visitRoom").isEqualTo("A1")
      .jsonPath("$.visitType").isEqualTo(VisitType.SOCIAL.name)
      .jsonPath("$.sessionTemplateId").isNumber
      .jsonPath("$.dayOfWeek").isEqualTo(MONDAY.name)
      .returnResult()

    // And
    val template = objectMapper.readValue(returnResult.responseBody, SessionTemplateDto::class.java)
    verify(telemetryClient).trackEvent(
      eq("session-template-created"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["id"]).isEqualTo(template.sessionTemplateId.toString())
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("session-template-created"), any(), isNull())
  }

  @Test
  fun `access forbidden when no role`() {

    // Given
    val bodyInsert = BodyInserters.fromValue(
      createSessionTemplateRequest
    )
    val emptyRoles: List<String> = emptyList()

    // When

    val responseSpec = webTestClient.post().uri("/visit-session-templates")
      .headers(setAuthorisation(roles = emptyRoles))
      .body(
        bodyInsert
      )
      .exchange()

    // Then
    responseSpec.expectStatus().isForbidden
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `unauthorised when no token`() {
    // Given
    val bodyInsert = BodyInserters.fromValue(
      createSessionTemplateRequest
    )

    // When
    val responseSpec = webTestClient.post().uri("/visit-session-templates")
      .body(bodyInsert)
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  @Test
  fun `create visit - invalid request`() {
    // Given
    val bodyInsert = BodyInserters.fromValue(
      mapOf("wrongProperty" to "wrongValue")
    )

    val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

    // When
    val responseSpec = webTestClient.post().uri("/visit-session-templates")
      .headers(setAuthorisation(roles = requiredRole))
      .body(bodyInsert)
      .exchange()

    // Then
    responseSpec.expectStatus().isBadRequest
    verify(telemetryClient, times(1)).trackEvent(eq("visit-bad-request-error"), any(), isNull())
  }

  @Test
  fun `create visit bad_request when blank required field`() {

    // Given

    val dtoWithBlankPrisonId = CreateSessionTemplateRequestDto(
      prisonId = "",
      startTime = LocalTime.of(14, 30),
      endTime = LocalTime.of(16, 30),
      validFromDate = LocalDate.of(2021, 1, 1),
      validToDate = LocalDate.of(2021, 4, 1),
      visitRoom = "A1",
      visitType = VisitType.SOCIAL,
      openCapacity = 5,
      closedCapacity = 2,
      dayOfWeek = MONDAY
    )

    val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

    // When
    val responseSpec = webTestClient.post().uri("/visit-session-templates")
      .headers(setAuthorisation(roles = requiredRole))
      .contentType(MediaType.APPLICATION_JSON)
      .body(
        BodyInserters.fromValue(
          dtoWithBlankPrisonId
        )
      )
      .exchange()

    // Then
    responseSpec.expectStatus().isBadRequest
    verify(telemetryClient, times(1)).trackEvent(eq("visit-bad-request-error"), any(), isNull())
  }
}
