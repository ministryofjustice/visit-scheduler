package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive

import java.util.Arrays
import kotlin.jvm.optionals.getOrNull

enum class IncentiveLevels(val code: String) {
  ENHANCED("ENH"),
  ;

  companion object {
    fun getIncentiveLevel(code: String): IncentiveLevels? {
      return Arrays.stream(values()).filter { it.code == code }.findFirst().getOrNull()
    }
  }
}
