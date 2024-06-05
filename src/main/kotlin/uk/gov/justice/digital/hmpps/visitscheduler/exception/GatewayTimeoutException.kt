package uk.gov.justice.digital.hmpps.visitscheduler.exception

import java.util.function.Supplier

class GatewayTimeoutException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<GatewayTimeoutException> {
  override fun get(): GatewayTimeoutException {
    return GatewayTimeoutException(message, cause)
  }
}
