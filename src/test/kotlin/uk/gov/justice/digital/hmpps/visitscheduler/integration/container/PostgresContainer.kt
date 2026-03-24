package uk.gov.justice.digital.hmpps.visitscheduler.integration.container

import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.io.IOException
import java.net.ServerSocket

object PostgresContainer {

  private const val DBNAME = "visit_scheduler"
  private const val USERNAME = "visit_scheduler"
  private const val PASSWORD = "visit_scheduler"

  val jdbcUrl: String get() = "jdbc:postgresql://localhost:5432/$DBNAME"
  val dbUsername: String get() = USERNAME
  val dbPassword: String get() = PASSWORD

  private val log = LoggerFactory.getLogger(this::class.java)

  val instance: GenericContainer<*>? by lazy { startPostgresqlIfNotRunning() }

  // This only starts a TestContainer if postgresql is not already running
  private fun startPostgresqlIfNotRunning(): GenericContainer<*>? {
    if (isPostgresRunning()) {
      return null
    }

    val logConsumer = Slf4jLogConsumer(log).withPrefix("postgresql")

    return GenericContainer(DockerImageName.parse("postgres:16")).apply {
      withEnv("HOSTNAME_EXTERNAL", "localhost")
      portBindings = listOf("5432:5432")
      withEnv("POSTGRES_DB", DBNAME)
      withEnv("POSTGRES_USER", USERNAME)
      withEnv("POSTGRES_PASSWORD", PASSWORD)
      withReuse(true)
      waitingFor(Wait.forListeningPort())
      start()
      followOutput(logConsumer)
    }
  }

  private fun isPostgresRunning(): Boolean = try {
    val serverSocket = ServerSocket(5432)
    serverSocket.localPort == 0
  } catch (e: IOException) {
    true
  }
}
