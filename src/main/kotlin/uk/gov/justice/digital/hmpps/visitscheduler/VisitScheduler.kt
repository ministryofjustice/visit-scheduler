package uk.gov.justice.digital.hmpps.visitscheduler

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class VisitScheduler

fun main(args: Array<String>) {
  runApplication<VisitScheduler>(*args)
}
