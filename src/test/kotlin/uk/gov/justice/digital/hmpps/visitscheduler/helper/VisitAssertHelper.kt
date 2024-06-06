package uk.gov.justice.digital.hmpps.visitscheduler.helper

import org.assertj.core.api.Assertions.assertThat
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit

@Component
class VisitAssertHelper {

  fun assertVisitDto(visitDto: VisitDto, visit: Visit) {
    assertThat(visitDto.reference).isEqualTo(visit.reference)
    assertThat(visitDto.applicationReference).isEqualTo(visit.getLastApplication()?.reference)
    assertThat(visitDto.prisonerId).isEqualTo(visit.prisonerId)
    assertThat(visitDto.prisonCode).isEqualTo(visit.prison.code)
    assertThat(visitDto.visitRoom).isEqualTo(visit.visitRoom)
    assertThat(visitDto.startTimestamp).isEqualTo(visit.sessionSlot.slotStart)
    assertThat(visitDto.endTimestamp).isEqualTo(visit.sessionSlot.slotEnd)
    assertThat(visitDto.visitType).isEqualTo(visit.visitType)
    assertThat(visitDto.visitStatus).isEqualTo(visit.visitStatus)
    assertThat(visitDto.visitRestriction).isEqualTo(visit.visitRestriction)

    assertThat(visitDto.visitContact.name).isEqualTo(visit.visitContact!!.name)
    assertThat(visitDto.visitContact.telephone).isEqualTo(visit.visitContact!!.telephone)

    assertThat(visitDto.visitors.size).isEqualTo(visit.visitors.size)
    visit.visitors.forEachIndexed { index, visitVisitor ->
      assertThat(visitDto.visitors[index].nomisPersonId).isEqualTo(visitVisitor.nomisPersonId)
      assertThat(visitDto.visitors[index].visitContact).isEqualTo(visitVisitor.visitContact)
    }

    visit.support?.let {
      assertThat(visitDto.visitorSupport?.description).isEqualTo(it.description)
    }

    assertThat(visitDto.visitNotes.size).isEqualTo(visit.visitNotes.size)
    visit.visitNotes.forEachIndexed { index, visitNote ->
      assertThat(visitDto.visitNotes[index].type).isEqualTo(visitNote.type)
      assertThat(visitDto.visitNotes[index].text).isEqualTo(visitNote.text)
    }

    visit.outcomeStatus?.let {
      assertThat(visitDto.outcomeStatus).isEqualTo(visit.outcomeStatus)
    }

    assertThat(visitDto.createdTimestamp).isEqualTo(visit.createTimestamp)
    assertThat(visitDto.modifiedTimestamp).isNotNull()

    assertThat(visitDto.userType).isEqualTo(visit.userType)
  }
}
