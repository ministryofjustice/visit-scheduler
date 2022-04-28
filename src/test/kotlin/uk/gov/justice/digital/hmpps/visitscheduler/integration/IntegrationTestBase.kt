package uk.gov.justice.digital.hmpps.visitscheduler.integration

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.visitscheduler.helper.JwtAuthHelper
import uk.gov.justice.digital.hmpps.visitscheduler.integration.container.LocalStackContainer
import uk.gov.justice.digital.hmpps.visitscheduler.integration.container.PostgresContainer
import uk.gov.justice.digital.hmpps.visitscheduler.integration.mock.PrisonApiMockServer

@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTestBase {
  @Suppress("unused")
  @Autowired
  lateinit var webTestClient: WebTestClient

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthHelper

  init {
    // Resolves an issue where Wiremock keeps previous sockets open from other tests causing connection resets
    System.setProperty("http.keepAlive", "false")
  }

  @BeforeEach
  fun resetStubs() {
    prisonApiMockServer.resetAll()
  }

  internal fun setAuthorisation(
    user: String = "AUTH_ADM",
    roles: List<String> = listOf(),
    scopes: List<String> = listOf()
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisation(user, roles, scopes)

  companion object {
    internal val prisonApiMockServer = PrisonApiMockServer()

    @BeforeAll
    @JvmStatic
    fun startMocks() {
      prisonApiMockServer.start()
    }

    @AfterAll
    @JvmStatic
    fun stopMocks() {
      prisonApiMockServer.stop()
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
        lsContainer.getEndpointConfiguration(org.testcontainers.containers.localstack.LocalStackContainer.Service.SNS)
          .let { it.serviceEndpoint to it.signingRegion }
          .also {
            registry.add("hmpps.sqs.localstackUrl") { it.first }
            registry.add("hmpps.sqs.region") { it.second }
          }
      }
    }
  }
}
