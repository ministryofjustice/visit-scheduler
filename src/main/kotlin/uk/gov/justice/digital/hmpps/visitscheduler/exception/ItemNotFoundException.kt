package uk.gov.justice.digital.hmpps.visitscheduler.exception

import java.util.function.Supplier

class ItemNotFoundException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<ItemNotFoundException> {
  override fun get(): ItemNotFoundException {
    return ItemNotFoundException(message, cause)
  }
}
