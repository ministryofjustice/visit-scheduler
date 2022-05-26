package uk.gov.justice.digital.hmpps.visitscheduler.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
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
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplateCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplateDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.model.SessionFrequency.WEEKLY
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import java.time.LocalDate
import java.time.LocalTime

@Import(TestClockConfiguration::class)
class VisitSessionTemplateControllerTest(@Autowired private val objectMapper: ObjectMapper) : IntegrationTestBase() {

  @Autowired
  private lateinit var sessionTemplateRepository: SessionTemplateRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @AfterEach
  internal fun deleteAllSessionTemplates() = sessionTemplateDeleter(sessionTemplateRepository)

  @DisplayName("POST /visit-session-templates")
  @Nested
  inner class CreateSessionTemplate {
    private val createSessionTemplateRequest = CreateSessionTemplateRequestDto(
      prisonId = "LEI",
      startTime = LocalTime.of(14, 30),
      endTime = LocalTime.of(16, 30),
      startDate = LocalDate.of(2021, 1, 1),
      expiryDate = LocalDate.of(2021, 4, 1),
      visitRoom = "A1",
      visitType = VisitType.SOCIAL,
      frequency = WEEKLY,
      openCapacity = 5,
      closedCapacity = 2
    )

    @Test
    fun `create session template`() {

      // When
      val responseSpec = webTestClient.post().uri("/visit-session-templates")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .body(
          BodyInserters.fromValue(
            createSessionTemplateRequest
          )
        )
        .exchange()

      // Then
      val returnResult = responseSpec.expectStatus().isCreated
        .expectBody()
        .jsonPath("$.sessionTemplateId").isNumber
        .jsonPath("$.prisonId").isEqualTo("LEI")
        .jsonPath("$.startTime").isEqualTo("14:30:00")
        .jsonPath("$.endTime").isEqualTo("16:30:00")
        .jsonPath("$.frequency").isEqualTo(WEEKLY.name)
        .jsonPath("$.openCapacity").isEqualTo(5)
        .jsonPath("$.closedCapacity").isEqualTo(2)
        .jsonPath("$.visitRoom").isEqualTo("A1")
        .jsonPath("$.visitType").isEqualTo(VisitType.SOCIAL.name)
        .jsonPath("$.sessionTemplateId").isNumber
        .returnResult()

      // And
      val template = objectMapper.readValue(returnResult.responseBody, SessionTemplateDto::class.java)
      verify(telemetryClient).trackEvent(
        eq("visit-scheduler-prison-session-template-created"),
        org.mockito.kotlin.check {
          Assertions.assertThat(it["id"]).isEqualTo(template.sessionTemplateId.toString())
        },
        isNull()
      )
      verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-session-template-created"), any(), isNull())
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.post().uri("/visit-session-templates")
        .headers(setAuthorisation(roles = listOf()))
        .body(
          BodyInserters.fromValue(
            createSessionTemplateRequest
          )
        )
        .exchange()
        .expectStatus().isForbidden

      verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-access-denied-error"), any(), isNull())
    }

    @Test
    fun `unauthorised when no token`() {
      webTestClient.post().uri("/visit-session-templates")
        .body(
          BodyInserters.fromValue(
            createSessionTemplateRequest
          )
        )
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `create visit - invalid request`() {
      webTestClient.post().uri("/visit-session-templates")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .body(
          BodyInserters.fromValue(
            mapOf("wrongProperty" to "wrongValue")
          )
        )
        .exchange()
        .expectStatus().isBadRequest

      verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-bad-request-error"), any(), isNull())
    }

    @Test
    fun `create visit bad_request when blank required field`() {
      val jsonString = """{
        "prisonId":"",
        "startTime":"14:30:00",
        "endTime":"16:30:00",
        "startDate":"2021-01-01",
        "expiryDate":"2021-04-01",
        "visitType":"SOCIAL",
        "visitRoom":"A1",
        "frequency": "WEEKLY",
        "closedCapacity":2,
        "openCapacity":5
      }"""

      webTestClient.post().uri("/visit-session-templates")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .contentType(MediaType.APPLICATION_JSON)
        .body(
          BodyInserters.fromValue(
            jsonString
          )
        )
        .exchange()
        .expectStatus().isBadRequest

      verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-bad-request-error"), any(), isNull())
    }
  }

  @DisplayName("DELETE /visit-session-templates/{sessionTemplateId}")
  @Nested
  inner class DeleteSessionTemplateById {
    @Test
    fun `delete session template by id`() {
      val sessionTemplate = sessionTemplateCreator(sessionTemplateRepository)
        .withStartTime(LocalTime.of(10, 0))
        .withEndTime(LocalTime.of(12, 0))
        .save()

      webTestClient.delete().uri("/visit-session-templates/${sessionTemplate.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk

      webTestClient.get().uri("/visit-session-templates/${sessionTemplate.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isNotFound

      verify(telemetryClient).trackEvent(
        eq("visit-scheduler-prison-session-template-deleted"),
        org.mockito.kotlin.check {
          Assertions.assertThat(it["id"]).isEqualTo(sessionTemplate.id.toString())
        },
        isNull()
      )
      verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-session-template-deleted"), any(), isNull())
    }

    @Test
    fun `delete session template by id NOT FOUND`() {
      webTestClient.delete().uri("/visit-session-templates/1234569999999999")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk

      verify(telemetryClient, times(0)).trackEvent(eq("visit-scheduler-prison-session-template-deleted"), any(), isNull())
    }
  }

  @DisplayName("Get /visit-session-templates")
  @Nested
  inner class GetSessionTemplate {
    @Test
    fun `all session templates are returned empty list`() {
      webTestClient.get().uri("/visit-session-templates")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(0)
    }

    @Test
    fun `all session templates are returned`() {
      sessionTemplateCreator(
        repository = sessionTemplateRepository,
        sessionTemplate = sessionTemplate(
          startDate = LocalDate.parse("2021-01-01"),
          frequency = WEEKLY
        )
      ).save()
      sessionTemplateCreator(
        repository = sessionTemplateRepository,
        sessionTemplate = sessionTemplate(
          startDate = LocalDate.parse("2021-02-01"),
          frequency = WEEKLY
        )
      ).save()
      webTestClient.get().uri("/visit-session-templates")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(2)
    }

    @Test
    fun `session templates are returned by id`() {
      val sessionTemplate = sessionTemplateCreator(
        repository = sessionTemplateRepository,
        sessionTemplate = sessionTemplate(
          startDate = LocalDate.parse("2021-01-01"),
          frequency = WEEKLY
        )
      ).save()

      webTestClient.get().uri("/visit-session-templates/${sessionTemplate.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(10)
        .jsonPath("$.sessionTemplateId").isEqualTo(sessionTemplate.id)
        .jsonPath("$.prisonId").isEqualTo("MDI")
        .jsonPath("$.startTime").isEqualTo("09:00:00")
        .jsonPath("$.endTime").isEqualTo("10:00:00")
        .jsonPath("$.startDate").isEqualTo("2021-01-01")
        .jsonPath("$.visitType").isEqualTo(VisitType.SOCIAL.name)
        .jsonPath("$.visitRoom").isEqualTo("1")
        .jsonPath("$.frequency").isEqualTo(WEEKLY.name)
        .jsonPath("$.closedCapacity").isEqualTo(5)
        .jsonPath("$.openCapacity").isEqualTo(10)
    }
  }
}
