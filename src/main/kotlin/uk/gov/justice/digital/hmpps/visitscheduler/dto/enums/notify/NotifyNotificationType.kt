package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.notify

import java.lang.IllegalArgumentException

enum class NotifyNotificationType {
  EMAIL,
  SMS,
  ;

  companion object {
    fun get(notificationType: String): NotifyNotificationType = when (notificationType) {
      "email" -> EMAIL
      "sms" -> SMS
      else -> throw IllegalArgumentException("Not a supported Notify Notification Type $notificationType")
    }
  }
}
