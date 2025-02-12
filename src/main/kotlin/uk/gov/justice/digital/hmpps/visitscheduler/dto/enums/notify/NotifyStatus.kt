package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.notify

enum class NotifyStatus(val order: Int) {
  SENDING(1),
  DELIVERED(3),
  FAILED(3),
  UNKNOWN(3),
  ;

  companion object {
    fun get(notificationStatus: String): NotifyStatus = when (notificationStatus) {
      "delivered" -> DELIVERED
      "permanent-failure", "temporary-failure", "technical-failure" -> FAILED
      "created", "sending" -> SENDING
      else -> UNKNOWN // used in case a new status is added on Gov Notify
    }
  }
}
