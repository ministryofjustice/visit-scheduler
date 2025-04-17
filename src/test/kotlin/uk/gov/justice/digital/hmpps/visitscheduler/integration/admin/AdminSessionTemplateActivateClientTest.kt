package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.ACTIVATE_SESSION_TEMPLATE_CLIENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UserClientDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callActivateSessionTemplateClient
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGetSessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Session template tests for activate - $ACTIVATE_SESSION_TEMPLATE_CLIENT")
class AdminSessionTemplateActivateClientTest : IntegrationTestBase() {

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  private lateinit var sessionTemplateWithClient: SessionTemplate
  private lateinit var sessionTemplateWithActiveClient: SessionTemplate
  private lateinit var sessionTemplateWithoutClient: SessionTemplate

  @BeforeEach
  internal fun setUp() {
    sessionTemplateWithClient = sessionTemplateEntityHelper.create(
      name = "session-template-with-client",
      validFromDate = LocalDate.now(),
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      isActive = true,
      clients = listOf(UserClientDto(UserType.STAFF, false)),
    )

    sessionTemplateWithActiveClient = sessionTemplateEntityHelper.create(
      name = "session-template-with-active-client",
      validFromDate = LocalDate.now(),
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      isActive = true,
      clients = listOf(UserClientDto(UserType.STAFF, false)),
    )

    sessionTemplateWithoutClient = sessionTemplateEntityHelper.create(
      name = "session-template-without-client",
      validFromDate = LocalDate.now(),
      startTime = LocalTime.parse("11:00"),
      endTime = LocalTime.parse("12:00"),
      isActive = true,
      clients = emptyList(),
    )
  }

  @Test
  fun `when session has inactive client activating the client sets it to active`() {
    // Given
    val reference = sessionTemplateWithClient.reference

    // When
    var responseSpec = callActivateSessionTemplateClient(webTestClient, reference, userType = UserType.STAFF, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
    val userClient = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, UserClientDto::class.java)

    Assertions.assertThat(userClient).isEqualTo(UserClientDto(UserType.STAFF, true))

    responseSpec = callGetSessionTemplate(webTestClient, reference, setAuthorisation(roles = adminRole))
    val sessionTemplate = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, SessionTemplateDto::class.java)
    Assertions.assertThat(sessionTemplate.clients.size).isEqualTo(1)
    Assertions.assertThat(sessionTemplate.clients[0].userType).isEqualTo(UserType.STAFF)
    Assertions.assertThat(sessionTemplate.clients[0].active).isEqualTo(true)
  }

  @Test
  fun `when session has active client activating the client still keeps it as active`() {
    // Given
    val reference = sessionTemplateWithActiveClient.reference

    // When
    var responseSpec = callActivateSessionTemplateClient(webTestClient, reference, userType = UserType.STAFF, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
    val userClient = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, UserClientDto::class.java)
    Assertions.assertThat(userClient).isEqualTo(UserClientDto(UserType.STAFF, true))

    responseSpec = callGetSessionTemplate(webTestClient, reference, setAuthorisation(roles = adminRole))
    val sessionTemplate = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, SessionTemplateDto::class.java)
    Assertions.assertThat(sessionTemplate.reference).isEqualTo(sessionTemplateWithActiveClient.reference)
    Assertions.assertThat(sessionTemplate.clients.size).isEqualTo(1)
    Assertions.assertThat(sessionTemplate.clients[0].userType).isEqualTo(UserType.STAFF)
    Assertions.assertThat(sessionTemplate.clients[0].active).isEqualTo(true)
  }

  @Test
  fun `when session has no client activating the client adds it to the client`() {
    // Given
    val reference = sessionTemplateWithoutClient.reference

    // When
    var responseSpec = callActivateSessionTemplateClient(webTestClient, reference, userType = UserType.STAFF, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
    val userClient = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, UserClientDto::class.java)
    Assertions.assertThat(userClient).isEqualTo(UserClientDto(UserType.STAFF, true))

    responseSpec = callGetSessionTemplate(webTestClient, reference, setAuthorisation(roles = adminRole))
    val sessionTemplate = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, SessionTemplateDto::class.java)
    Assertions.assertThat(sessionTemplate.reference).isEqualTo(sessionTemplateWithoutClient.reference)
    Assertions.assertThat(sessionTemplate.clients.size).isEqualTo(1)
    Assertions.assertThat(sessionTemplate.clients[0].userType).isEqualTo(UserType.STAFF)
    Assertions.assertThat(sessionTemplate.clients[0].active).isEqualTo(true)
  }

  @Test
  fun `when non existing session activated then BAD_REQUEST error is thrown`() {
    // Given
    val reference = "i-do-not-exist"

    // When
    val responseSpec = callActivateSessionTemplateClient(webTestClient, reference, userType = UserType.STAFF, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isNotFound
      .expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Template reference:i-do-not-exist not found")
  }

  @Test
  fun `when activate session client is called with incorrect role access forbidden error is returned`() {
    // Given
    val nonAdminRole = listOf("ROLE_VISIT_SCHEDULER")
    // When
    val responseSpec = callActivateSessionTemplateClient(
      webTestClient,
      sessionTemplateDefault.reference,
      userType = UserType.STAFF,
      setAuthorisation(roles = nonAdminRole),
    )

    // Then
    responseSpec.expectStatus().isForbidden
  }
}
