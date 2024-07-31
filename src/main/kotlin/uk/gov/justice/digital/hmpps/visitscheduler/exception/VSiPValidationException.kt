package uk.gov.justice.digital.hmpps.visitscheduler.exception

import java.util.function.Supplier

class VSiPValidationException(val messages: Array<String>) :
  RuntimeException(messages.joinToString()),
  Supplier<VSiPValidationException> {
  override fun get(): VSiPValidationException {
    val messages = message?.split(",")?.toTypedArray() ?: arrayOf()
    return VSiPValidationException(messages)
  }

  constructor(message: String) : this(arrayOf(message))
}
