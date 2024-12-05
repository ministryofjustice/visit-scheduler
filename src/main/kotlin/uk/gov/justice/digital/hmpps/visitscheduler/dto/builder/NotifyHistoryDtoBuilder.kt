package uk.gov.justice.digital.hmpps.visitscheduler.dto.builder

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.audit.NotifyHistoryDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNotifyHistory

@Component
class NotifyHistoryDtoBuilder {
  fun build(notifyHistory: List<VisitNotifyHistory>?): List<NotifyHistoryDto> {
    val notifyHistoryValues = mutableListOf<NotifyHistoryDto>()

    if (!notifyHistory.isNullOrEmpty()) {
      notifyHistory.groupBy({ it.notificationId }, { it }).forEach { entry ->
        val notificationHistory = entry.value.sortedByDescending { it.status.order }.first()
        notifyHistoryValues.add(NotifyHistoryDto(notificationHistory))
      }
    }

    return notifyHistoryValues.toList()
  }
}
