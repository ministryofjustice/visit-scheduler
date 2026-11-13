package uk.gov.justice.digital.hmpps.visitscheduler.integration.swagger

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class OpenApiDocsTest : IntegrationTestBase() {

  @Test
  fun `open api docs are available`() {
    webTestClient.get()
      .uri("/swagger-ui/index.html?configUrl=/v3/api-docs/swagger-config")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `open api docs redirect to correct page`() {
    webTestClient.get()
      .uri("/swagger-ui.html")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().is3xxRedirection
      .expectHeader().value("Location") { it.contains("/swagger-ui/index.html?configUrl=/v3/api-docs/swagger-config") }
  }

  @Test
  fun `the swagger json is valid`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("messages").doesNotExist()
  }

  @Test
  fun `the swagger json contains the version number`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("info.version").isEqualTo(DateTimeFormatter.ISO_DATE.format(LocalDate.now()))
  }

  @Test
  fun `external update and cancel schemas make clientName optional while creation requires it`() {
    val response = webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()

    listOf("UpdateVisitFromExternalSystemDto", "CancelVisitFromExternalSystemDto").forEach { schema ->
      response
        .jsonPath("$.components.schemas.$schema.required").value<List<String>> {
          assertThat(it).doesNotContain("clientName")
        }
        .jsonPath("$.components.schemas.$schema.properties.clientName.description").value<String> {
          assertThat(it).contains("Omitted, null or blank", "must exactly match")
        }
        .jsonPath("$.components.schemas.$schema.properties.clientName.minLength").doesNotExist()
    }
    response.jsonPath("$.components.schemas.CreateVisitFromExternalSystemDto.required").value<List<String>> {
      assertThat(it).contains("clientName")
    }
  }

  @Test
  fun `external visit schemas advertise video visits and optional audit metadata`() {
    val response = webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.schemas.CreateVisitFromExternalSystemDto.properties.visitType.enum").isEqualTo(listOf("SOCIAL", "SOCIAL_VIDEO"))
      .jsonPath("$.components.schemas.UpdateVisitFromExternalSystemDto.properties.visitType.enum").isEqualTo(listOf("SOCIAL", "SOCIAL_VIDEO"))
      .jsonPath("$.components.schemas.VisitDto.properties.visitType.enum").isEqualTo(listOf("SOCIAL", "SOCIAL_VIDEO"))

    listOf("CreateVisitFromExternalSystemDto", "UpdateVisitFromExternalSystemDto").forEach { schema ->
      response
        .jsonPath("$.components.schemas.$schema.properties.actionedBy").exists()
        .jsonPath("$.components.schemas.$schema.properties.username").doesNotExist()
        .jsonPath("$.components.schemas.$schema.properties.applicationMethodType.enum")
        .isEqualTo(listOf("PHONE", "WEBSITE", "EMAIL", "IN_PERSON", "NOT_KNOWN", "NOT_APPLICABLE", "BY_PRISONER"))
        .jsonPath("$.components.schemas.$schema.properties.userType.enum").isEqualTo(listOf("STAFF", "PUBLIC", "SYSTEM", "PRISONER"))
        .jsonPath("$.components.schemas.$schema.required").value<List<String>> {
          assertThat(it).doesNotContain("actionedBy", "applicationMethodType", "userType")
        }
    }
  }
}
