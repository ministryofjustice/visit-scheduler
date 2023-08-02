package uk.gov.justice.digital.hmpps.visitscheduler.integration

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category.SessionCategoryGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.SessionIncentiveLevelGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.SessionLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AssertHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.DeleteEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.EventAuditEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.JwtAuthHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.PrisonEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.SessionLocationGroupHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.SessionPrisonerCategoryHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.SessionPrisonerIncentiveLevelHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.SessionTemplateEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.VisitEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.integration.container.LocalStackContainer
import uk.gov.justice.digital.hmpps.visitscheduler.integration.container.PostgresContainer
import uk.gov.justice.digital.hmpps.visitscheduler.integration.mock.HmppsAuthExtension
import uk.gov.justice.digital.hmpps.visitscheduler.integration.mock.PrisonApiMockServer
import uk.gov.justice.digital.hmpps.visitscheduler.integration.mock.PrisonOffenderSearchMockServer
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestEventAuditRepository

@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(HmppsAuthExtension::class)
abstract class IntegrationTestBase {
  @Suppress("unused")
  @Autowired
  lateinit var webTestClient: WebTestClient

  @Autowired
  protected lateinit var objectMapper: ObjectMapper

  @Autowired
  protected lateinit var visitEntityHelper: VisitEntityHelper

  @Autowired
  protected lateinit var eventAuditEntityHelper: EventAuditEntityHelper

  @Autowired
  protected lateinit var sessionTemplateEntityHelper: SessionTemplateEntityHelper

  @Autowired
  protected lateinit var sessionPrisonerCategoryHelper: SessionPrisonerCategoryHelper

  @Autowired
  protected lateinit var eventAuditRepository: TestEventAuditRepository

  @Autowired
  protected lateinit var sessionPrisonerIncentiveLevelHelper: SessionPrisonerIncentiveLevelHelper

  @Autowired
  lateinit var prisonEntityHelper: PrisonEntityHelper

  @Autowired
  protected lateinit var sessionLocationGroupHelper: SessionLocationGroupHelper

  @Autowired
  protected lateinit var deleteEntityHelper: DeleteEntityHelper

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthHelper

  @Autowired
  protected lateinit var assertHelper: AssertHelper

  init {
    // Resolves an issue where Wiremock keeps previous sockets open from other tests causing connection resets
    System.setProperty("http.keepAlive", "false")
  }

  @BeforeEach
  fun resetStubs() {
    prisonApiMockServer.resetAll()
    prisonOffenderSearchMockServer.resetAll()
  }

  @AfterEach
  internal fun deleteAll() {
    deleteEntityHelper.deleteAll()
  }

  fun getSessionTemplate(responseSpec: ResponseSpec): SessionTemplateDto =
    objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, SessionTemplateDto::class.java)

  fun getSessionLocationGroup(responseSpec: ResponseSpec): SessionLocationGroupDto =
    objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, SessionLocationGroupDto::class.java)

  fun getSessionLocationGroups(responseSpec: ResponseSpec): Array<SessionLocationGroupDto> =
    objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, Array<SessionLocationGroupDto>::class.java)

  fun getSessionCategoryGroups(responseSpec: ResponseSpec): Array<SessionCategoryGroupDto> =
    objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, Array<SessionCategoryGroupDto>::class.java)

  fun getSessionCategoryGroup(responseSpec: ResponseSpec): SessionCategoryGroupDto =
    objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, SessionCategoryGroupDto::class.java)

  fun getSessionIncentiveGroup(responseSpec: ResponseSpec): SessionIncentiveLevelGroupDto =
    objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, SessionIncentiveLevelGroupDto::class.java)

  fun getSessionIncentiveGroups(responseSpec: ResponseSpec): Array<SessionIncentiveLevelGroupDto> =
    objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, Array<SessionIncentiveLevelGroupDto>::class.java)

  fun getErrorResponse(responseSpec: ResponseSpec) =
    objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, ErrorResponse::class.java)

  internal fun setAuthorisation(
    user: String = "AUTH_ADM",
    roles: List<String> = listOf(),
    scopes: List<String> = listOf(),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisation(user, roles, scopes)

  companion object {
    internal val prisonApiMockServer = PrisonApiMockServer()
    internal val prisonOffenderSearchMockServer = PrisonOffenderSearchMockServer()

    @BeforeAll
    @JvmStatic
    fun startMocks() {
      prisonApiMockServer.start()
      prisonOffenderSearchMockServer.start()
    }

    @AfterAll
    @JvmStatic
    fun stopMocks() {
      prisonApiMockServer.stop()
      prisonOffenderSearchMockServer.stop()
    }

    private val pgContainer = PostgresContainer.instance
    private val lsContainer = LocalStackContainer.instance

    @JvmStatic
    @DynamicPropertySource
    fun properties(registry: DynamicPropertyRegistry) {
      pgContainer?.run {
        registry.add("spring.datasource.url", pgContainer::getJdbcUrl)
        registry.add("spring.datasource.username", pgContainer::getUsername)
        registry.add("spring.datasource.password", pgContainer::getPassword)
        registry.add("spring.datasource.placeholders.database_update_password", pgContainer::getPassword)
        registry.add("spring.datasource.placeholders.database_read_only_password", pgContainer::getPassword)
        registry.add("spring.flyway.url", pgContainer::getJdbcUrl)
        registry.add("spring.flyway.user", pgContainer::getUsername)
        registry.add("spring.flyway.password", pgContainer::getPassword)
      }
      lsContainer?.run {
        registry.add("hmpps.sqs.localstackUrl") { lsContainer.getEndpointOverride(org.testcontainers.containers.localstack.LocalStackContainer.Service.SNS) }
        registry.add("hmpps.sqs.region") { lsContainer.region }
      }
    }
  }
}
