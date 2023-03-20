package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class MessageService(
  private val messageSource: MessageSource,
) {

  fun getMessage(code: String, vararg args: String): String? {
    val locale: Locale = LocaleContextHolder.getLocale()
    return messageSource.getMessage(code, args, locale)
  }
}
