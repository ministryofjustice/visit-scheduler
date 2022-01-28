package uk.gov.justice.digital.hmpps.visitscheduler

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class VisitScheduler

fun main(args: Array<String>) {
  runApplication<VisitScheduler>(*args)
}
