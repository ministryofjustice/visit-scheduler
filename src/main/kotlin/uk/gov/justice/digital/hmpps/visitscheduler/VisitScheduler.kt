package uk.gov.justice.digital.hmpps.visitscheduler

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@SecurityScheme(name = "bearerAuth", scheme = "bearer", type = SecuritySchemeType.HTTP, bearerFormat = "JWT")
@EnableScheduling
class VisitScheduler

fun main(args: Array<String>) {
  runApplication<VisitScheduler>(*args)
}
