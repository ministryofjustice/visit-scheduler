package uk.gov.justice.digital.hmpps.visitscheduler.utils.diff

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditAttributeTypes
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Component
class UpdateVisitSummaryUtil {
  fun getDiff(
    visitDtoAfterUpdate: VisitDto,
    visitDtoBeforeUpdate: VisitDto,
  ): String? {
    val updates = mutableListOf<String>()
    getRestrictionUpdateText(visitDtoAfterUpdate.visitRestriction, visitDtoBeforeUpdate.visitRestriction)?.let { updates.add(it) }
    getVisitorUpdateText(visitDtoAfterUpdate.visitors, visitDtoBeforeUpdate.visitors)?.let { updates.add(it) }
    getSessionSlotUpdateText(visitDtoAfterUpdate, visitDtoBeforeUpdate)?.let { updates.add(it) }
    getAdditionalInformationUpdateText(visitDtoAfterUpdate.visitorSupport?.description, visitDtoBeforeUpdate.visitorSupport?.description)?.let { updates.add(it) }
    getVisitContactUpdateText(visitDtoAfterUpdate.visitContact, visitDtoBeforeUpdate.visitContact)?.let { updates.add(it) }

    return if (updates.isNotEmpty()) {
      updates.joinToString(", ")
    } else {
      null
    }
  }

  fun getUpdateVisitAuditAttributes(
    visitDtoAfterUpdate: VisitDto,
    visitDtoBeforeUpdate: VisitDto,
  ): Map<EventAuditAttributeTypes, String> {
    val updateAttributes = mutableMapOf<EventAuditAttributeTypes, String>()
    updateAttributes.putAll(getRestrictionUpdateAttributes(visitDtoAfterUpdate.visitRestriction, visitDtoBeforeUpdate.visitRestriction))
    updateAttributes.putAll(getVisitorUpdateAttributes(visitDtoAfterUpdate.visitors, visitDtoBeforeUpdate.visitors))
    updateAttributes.putAll(getSessionSlotUpdateAttributes(visitDtoAfterUpdate, visitDtoBeforeUpdate))
    updateAttributes.putAll(getAdditionalInformationUpdateAttributes(visitDtoAfterUpdate.visitorSupport?.description, visitDtoBeforeUpdate.visitorSupport?.description))
    updateAttributes.putAll(getVisitContactUpdateAttributes(visitDtoAfterUpdate.visitContact, visitDtoBeforeUpdate.visitContact))

    return updateAttributes
  }

  private fun getRestrictionUpdateText(
    newVisitRestriction: VisitRestriction,
    oldVisitRestriction: VisitRestriction,
  ): String? = if (newVisitRestriction != oldVisitRestriction) {
    "Visit restriction changed from ${oldVisitRestriction.name} to ${newVisitRestriction.name}"
  } else {
    null
  }

  private fun getRestrictionUpdateAttributes(
    newVisitRestriction: VisitRestriction,
    oldVisitRestriction: VisitRestriction,
  ): Map<EventAuditAttributeTypes, String> = if (newVisitRestriction != oldVisitRestriction) {
    mapOf(
      EventAuditAttributeTypes.VISIT_UPDATE_OLD_RESTRICTION to oldVisitRestriction.name,
      EventAuditAttributeTypes.VISIT_UPDATE_NEW_RESTRICTION to newVisitRestriction.name,
    )
  } else {
    emptyMap()
  }

  private fun getVisitorUpdateText(
    newVisitVisitors: List<VisitorDto>,
    oldVisitVisitors: List<VisitorDto>,
  ): String? {
    val updates = mutableListOf<String>()
    val addedVisitorCount = (newVisitVisitors.map { it.nomisPersonId }.count { applicationVisitorId -> !(oldVisitVisitors.map { it.nomisPersonId }.contains(applicationVisitorId)) })
    val removedVisitorCount = (oldVisitVisitors.map { it.nomisPersonId }.count { existingVisitorId -> !(newVisitVisitors.map { it.nomisPersonId }.contains(existingVisitorId)) })

    if (addedVisitorCount > 0) {
      updates.add("Added $addedVisitorCount visitor(s)")
    }
    if (removedVisitorCount > 0) {
      updates.add("Removed $removedVisitorCount visitor(s)")
    }

    return if (updates.isNotEmpty()) {
      updates.joinToString(", ")
    } else {
      null
    }
  }

  private fun getVisitorUpdateAttributes(
    newVisitVisitors: List<VisitorDto>,
    oldVisitVisitors: List<VisitorDto>,
  ): Map<EventAuditAttributeTypes, String> {
    val attributes = mutableMapOf<EventAuditAttributeTypes, String>()
    val addedVisitorCount = (newVisitVisitors.map { it.nomisPersonId }.count { applicationVisitorId -> !(oldVisitVisitors.map { it.nomisPersonId }.contains(applicationVisitorId)) })
    val removedVisitorCount = (oldVisitVisitors.map { it.nomisPersonId }.count { existingVisitorId -> !(newVisitVisitors.map { it.nomisPersonId }.contains(existingVisitorId)) })

    if (addedVisitorCount > 0) {
      attributes.put(EventAuditAttributeTypes.VISIT_UPDATE_VISITORS_ADDED, addedVisitorCount.toString())
    }
    if (removedVisitorCount > 0) {
      attributes.put(EventAuditAttributeTypes.VISIT_UPDATE_VISITORS_REMOVED, removedVisitorCount.toString())
    }

    return attributes.toMap()
  }

  private fun getSessionSlotUpdateText(
    visitDtoAfterUpdate: VisitDto,
    visitDtoBeforeUpdate: VisitDto,
  ): String? = if (visitDtoAfterUpdate.startTimestamp != visitDtoBeforeUpdate.startTimestamp &&
    visitDtoAfterUpdate.endTimestamp != visitDtoBeforeUpdate.endTimestamp
  ) {
    val sessionDateBeforeUpdateDateString = getSessionDateString(visitDtoBeforeUpdate.startTimestamp.toLocalDate())
    val sessionTimeBeforeUpdateString = getSessionTimeString(visitDtoBeforeUpdate.startTimestamp.toLocalTime(), visitDtoBeforeUpdate.endTimestamp.toLocalTime())
    val sessionTimeAfterUpdateString = getSessionTimeString(visitDtoAfterUpdate.startTimestamp.toLocalTime(), visitDtoAfterUpdate.endTimestamp.toLocalTime())
    if (visitDtoBeforeUpdate.startTimestamp.toLocalDate() == visitDtoAfterUpdate.startTimestamp.toLocalDate()) {
      "Moved session from $sessionDateBeforeUpdateDateString ($sessionTimeBeforeUpdateString) to ($sessionTimeAfterUpdateString)"
    } else {
      val sessionDateAfterUpdateDateString = getSessionDateString(visitDtoAfterUpdate.startTimestamp.toLocalDate())
      "Moved session from $sessionDateBeforeUpdateDateString ($sessionTimeBeforeUpdateString) to $sessionDateAfterUpdateDateString ($sessionTimeAfterUpdateString)"
    }
  } else {
    null
  }

  private fun getSessionSlotUpdateAttributes(
    visitDtoAfterUpdate: VisitDto,
    visitDtoBeforeUpdate: VisitDto,
  ): Map<EventAuditAttributeTypes, String> {
    val attributes = mutableMapOf<EventAuditAttributeTypes, String>()

    if (visitDtoAfterUpdate.sessionTemplateReference != visitDtoBeforeUpdate.sessionTemplateReference ||
      (visitDtoAfterUpdate.startTimestamp != visitDtoBeforeUpdate.startTimestamp || visitDtoAfterUpdate.endTimestamp != visitDtoBeforeUpdate.endTimestamp)
    ) {
      visitDtoBeforeUpdate.sessionTemplateReference?.let {
        attributes.put(EventAuditAttributeTypes.VISIT_UPDATE_SLOT_CHANGED_OLD_SESSION_TEMPLATE_REFERENCE, it)
      }
      attributes[EventAuditAttributeTypes.VISIT_UPDATE_SLOT_CHANGED_OLD_START_DATE] = visitDtoBeforeUpdate.startTimestamp.toLocalDate().toString()
      attributes[EventAuditAttributeTypes.VISIT_UPDATE_SLOT_CHANGED_OLD_START_TIME] = visitDtoBeforeUpdate.startTimestamp.toLocalTime().toString()
      attributes[EventAuditAttributeTypes.VISIT_UPDATE_SLOT_CHANGED_OLD_END_DATE] = visitDtoBeforeUpdate.endTimestamp.toLocalDate().toString()
      attributes[EventAuditAttributeTypes.VISIT_UPDATE_SLOT_CHANGED_OLD_END_TIME] = visitDtoBeforeUpdate.endTimestamp.toLocalTime().toString()

      visitDtoAfterUpdate.sessionTemplateReference?.let {
        attributes.put(EventAuditAttributeTypes.VISIT_UPDATE_SLOT_CHANGED_NEW_SESSION_TEMPLATE_REFERENCE, it)
      }
      attributes[EventAuditAttributeTypes.VISIT_UPDATE_SLOT_CHANGED_NEW_START_DATE] = visitDtoAfterUpdate.startTimestamp.toLocalDate().toString()
      attributes[EventAuditAttributeTypes.VISIT_UPDATE_SLOT_CHANGED_NEW_START_TIME] = visitDtoAfterUpdate.startTimestamp.toLocalTime().toString()
      attributes[EventAuditAttributeTypes.VISIT_UPDATE_SLOT_CHANGED_NEW_END_DATE] = visitDtoAfterUpdate.endTimestamp.toLocalDate().toString()
      attributes[EventAuditAttributeTypes.VISIT_UPDATE_SLOT_CHANGED_NEW_END_TIME] = visitDtoAfterUpdate.endTimestamp.toLocalTime().toString()
    }

    return attributes.toMap()
  }

  private fun getAdditionalInformationUpdateText(
    newVisitAdditionalInformation: String?,
    oldVisitAdditionalInformation: String?,
  ): String? = if (newVisitAdditionalInformation != oldVisitAdditionalInformation) {
    "Updated additional support"
  } else {
    null
  }

  private fun getAdditionalInformationUpdateAttributes(
    newVisitAdditionalInformation: String?,
    oldVisitAdditionalInformation: String?,
  ): Map<EventAuditAttributeTypes, String> = if (newVisitAdditionalInformation != oldVisitAdditionalInformation) {
    mapOf(EventAuditAttributeTypes.VISIT_UPDATE_ADDITIONAL_INFO_UPDATED to "updated")
  } else {
    emptyMap()
  }

  private fun getVisitContactUpdateText(
    newVisitContact: ContactDto,
    oldVisitContact: ContactDto,
  ): String? = if (newVisitContact != oldVisitContact) {
    "Updated contact information"
  } else {
    null
  }

  private fun getVisitContactUpdateAttributes(
    newVisitContact: ContactDto,
    oldVisitContact: ContactDto,
  ): Map<EventAuditAttributeTypes, String> = if (newVisitContact != oldVisitContact) {
    mapOf(EventAuditAttributeTypes.VISIT_UPDATE_CONTACT_INFO_UPDATED to "updated")
  } else {
    emptyMap()
  }

  private fun getSessionDateString(sessionDate: LocalDate): String {
    val dateFormatter = DateTimeFormatter.ofPattern("E dd-MM-yyyy")
    return sessionDate.format(dateFormatter)
  }

  private fun getSessionTimeString(sessionStartTime: LocalTime, sessionEndTime: LocalTime): String {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val startTimeString = sessionStartTime.format(timeFormatter)
    val endTimeString = sessionEndTime.format(timeFormatter)

    return "$startTimeString - $endTimeString"
  }
}
