package uk.gov.justice.digital.hmpps.visitscheduler.model

import java.time.Period

enum class SessionFrequency(
  val frequencyPeriod: Period,
) {
  WEEKLY(Period.ofWeeks(1)),
  SINGLE(Period.ofYears(100)) // slight cheat here to allow slot calculation to only return 1
}
