package uk.gov.justice.digital.hmpps.visitscheduler.utils.diff

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction

@Component
class UpdateVisitDifferentiator {
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

  private fun getRestrictionUpdateText(
    newVisitRestriction: VisitRestriction,
    oldVisitRestriction: VisitRestriction,
  ): String? = if (newVisitRestriction != oldVisitRestriction) {
    "Visit type changed from ${oldVisitRestriction.name} to ${newVisitRestriction.name}"
  } else {
    null
  }

  private fun getVisitorUpdateText(
    newVisitVisitors: List<VisitorDto>,
    oldVisitVisitors: List<VisitorDto>,
  ): String? {
    val updates = mutableListOf<String>()
    val addedVisitorCount = (newVisitVisitors.map { it.nomisPersonId }.count { applicationVisitorId -> !(oldVisitVisitors.map { it.nomisPersonId }.contains(applicationVisitorId)) })
    val removedVisitorCount = (newVisitVisitors.map { it.nomisPersonId }.count { existingVisitorId -> !(oldVisitVisitors.map { it.nomisPersonId }.contains(existingVisitorId)) })

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

  private fun getSessionSlotUpdateText(
    visitDtoAfterUpdate: VisitDto,
    visitDtoBeforeUpdate: VisitDto,
  ): String? = if (visitDtoAfterUpdate.startTimestamp != visitDtoBeforeUpdate.startTimestamp &&
    visitDtoAfterUpdate.endTimestamp != visitDtoBeforeUpdate.endTimestamp
  ) {
    "Moved session from ${visitDtoBeforeUpdate.startTimestamp} to ${visitDtoAfterUpdate.startTimestamp}"
  } else {
    null
  }

  private fun getAdditionalInformationUpdateText(
    newVisitAdditionalInformation: String?,
    oldVisitAdditionalInformation: String?,
  ): String? = if (newVisitAdditionalInformation != oldVisitAdditionalInformation) {
    "Updated additional support"
  } else {
    null
  }

  private fun getVisitContactUpdateText(
    newVisitContact: ContactDto,
    oldVisitContact: ContactDto,
  ): String? = if (newVisitContact != oldVisitContact) {
    "Updated contact information"
  } else {
    null
  }
}
