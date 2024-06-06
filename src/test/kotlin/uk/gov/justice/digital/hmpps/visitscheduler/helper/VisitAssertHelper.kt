package uk.gov.justice.digital.hmpps.visitscheduler.helper

import org.assertj.core.api.Assertions
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit

@Component
class VisitAssertHelper {

  fun assertVisitDto(visitDto: VisitDto, visit: Visit) {
    Assertions.assertThat(visitDto.reference).isEqualTo(visit.reference)
    Assertions.assertThat(visitDto.applicationReference).isEqualTo(visit.getLastApplication()?.reference)
    Assertions.assertThat(visitDto.prisonerId).isEqualTo(visit.prisonerId)
    Assertions.assertThat(visitDto.prisonCode).isEqualTo(visit.prison.code)
    Assertions.assertThat(visitDto.visitRoom).isEqualTo(visit.visitRoom)
    Assertions.assertThat(visitDto.startTimestamp).isEqualTo(visit.sessionSlot.slotStart)
    Assertions.assertThat(visitDto.endTimestamp).isEqualTo(visit.sessionSlot.slotEnd)
    Assertions.assertThat(visitDto.visitType).isEqualTo(visit.visitType)
    Assertions.assertThat(visitDto.visitStatus).isEqualTo(visit.visitStatus)
    Assertions.assertThat(visitDto.visitRestriction).isEqualTo(visit.visitRestriction)

    Assertions.assertThat(visitDto.visitContact.name).isEqualTo(visit.visitContact!!.name)
    Assertions.assertThat(visitDto.visitContact.telephone).isEqualTo(visit.visitContact!!.telephone)

    Assertions.assertThat(visitDto.visitors.size).isEqualTo(visit.visitors.size)
    visit.visitors.forEachIndexed { index, visitVisitor ->
      Assertions.assertThat(visitDto.visitors[index].nomisPersonId).isEqualTo(visitVisitor.nomisPersonId)
      Assertions.assertThat(visitDto.visitors[index].visitContact).isEqualTo(visitVisitor.visitContact)
    }

    visit.support?.let {
      Assertions.assertThat(visitDto.visitorSupport?.description).isEqualTo(it.description)
    }

    Assertions.assertThat(visitDto.visitNotes.size).isEqualTo(visit.visitNotes.size)
    visit.visitNotes.forEachIndexed { index, visitNote ->
      Assertions.assertThat(visitDto.visitNotes[index].type).isEqualTo(visitNote.type)
      Assertions.assertThat(visitDto.visitNotes[index].text).isEqualTo(visitNote.text)
    }

    visit.outcomeStatus?.let {
      Assertions.assertThat(visitDto.outcomeStatus).isEqualTo(visit.outcomeStatus)
    }

    Assertions.assertThat(visitDto.createdTimestamp).isEqualTo(visit.createTimestamp)
    Assertions.assertThat(visitDto.modifiedTimestamp).isNotNull()

    Assertions.assertThat(visitDto.userType).isEqualTo(visit.userType)
  }
}
