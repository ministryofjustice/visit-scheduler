package uk.gov.justice.digital.hmpps.visitscheduler.integration.container

import org.slf4j.LoggerFactory
import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.io.IOException
import java.net.ServerSocket

object LocalStackContainer {
  private val log = LoggerFactory.getLogger(this::class.java)
  val instance by lazy { startLocalstackIfNotRunning() }

  fun setLocalStackProperties(container: GenericContainer<*>, registry: DynamicPropertyRegistry) {
    registry.add("hmpps.sqs.localstackUrl") { "http://${container.host}:${container.getMappedPort(4566)}" }
    registry.add("hmpps.sqs.region") { "eu-west-2" }
  }

  private fun startLocalstackIfNotRunning(): GenericContainer<*>? {
    if (localstackIsRunning()) return null
    val logConsumer = Slf4jLogConsumer(log).withPrefix("localstack")

    return GenericContainer(DockerImageName.parse("localstack/localstack:community-archive")).apply {
      withExposedPorts(4566)
      withEnv("SERVICES", "sns,sqs")
      withEnv("HOSTNAME_EXTERNAL", "localhost")
      waitingFor(
        Wait.forLogMessage(".*Ready.*", 1),
      )
      start()
      followOutput(logConsumer)
    }
  }

  private fun localstackIsRunning(): Boolean = try {
    val serverSocket = ServerSocket(4566)
    serverSocket.localPort == 0
  } catch (e: IOException) {
    true
  }
}
