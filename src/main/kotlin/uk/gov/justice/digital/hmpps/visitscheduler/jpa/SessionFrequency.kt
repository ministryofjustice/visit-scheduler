package uk.gov.justice.digital.hmpps.visitscheduler.jpa

import java.time.Period

enum class SessionFrequency(
  val frequencyPeriod: Period,
) {
  DAILY(Period.ofDays(1)),
  WEEKLY(Period.ofWeeks(1)),
  MONTHLY(Period.ofMonths(1)), // not a very useful one - will be replaced when supported frequencies defined
  SINGLE(Period.ofYears(100)) // slight cheat here to allow slot calculation to only return 1
}
