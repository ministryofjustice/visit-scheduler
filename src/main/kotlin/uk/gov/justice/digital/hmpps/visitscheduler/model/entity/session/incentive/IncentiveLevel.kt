package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive

import java.util.Arrays
import kotlin.jvm.optionals.getOrNull

enum class IncentiveLevel(val code: String) {
  ENHANCED("ENH"),
  ENHANCED_2("EN2"),
  ENHANCED_3("EN3"),
  BASIC("BAS"),
  STANDARD("STD"),
  ;

  companion object {
    fun getIncentiveLevel(code: String): IncentiveLevel? {
      return Arrays.stream(values()).filter { it.code == code }.findFirst().getOrNull()
    }
  }
}
