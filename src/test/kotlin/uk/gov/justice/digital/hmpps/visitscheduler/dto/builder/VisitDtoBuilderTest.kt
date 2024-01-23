package uk.gov.justice.digital.hmpps.visitscheduler.dto.builder

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.PrisonEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitSupport
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionTemplateService
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class VisitDtoBuilderTest() {

  private var prison: Prison = PrisonEntityHelper.createPrison()
  private val sessionTemplateService: SessionTemplateService = mock<SessionTemplateService>()

  @InjectMocks
  val toTest: VisitDtoBuilder = VisitDtoBuilder()

  @Test
  fun `Visit Dto is built correctly from given entities`() {
    // Given
    val now = LocalDateTime.now()

    val sessionStart = now.plusHours(4)
    val sessionEnd = sessionStart.plusHours(2)
    val visitStart = now
    val visitEnd = visitStart.plusHours(2)

    val visit = create(visitStart = visitStart, visitEnd = visitEnd)
    val slot = SessionTimeSlotDto(sessionStart.toLocalTime(), sessionEnd.toLocalTime())

    whenever(sessionTemplateService.getSessionTimeSlotDto(visit.sessionTemplateReference)).thenReturn(slot)

    // When

    val result = toTest.build(visit)

    // Then
    assertVisitDto(result, visit, sessionStart, sessionEnd)
  }

  @Test
  fun `Visit Dto is built correctly from given entities 2`() {
    // Given
    val now = LocalDateTime.now()
    val visitStart = now
    val visitEnd = visitStart.plusHours(2)

    val visit = create(visitStart = visitStart, visitEnd = visitEnd)

    // When

    val result = toTest.build(visit)

    // Then
    assertVisitDto(result, visit, visitStart, visitEnd)
  }

  private fun assertVisitDto(
    visitDto: VisitDto,
    visit: Visit,
    visitStart: LocalDateTime,
    visitEnd: LocalDateTime,
  ) {
    Assertions.assertThat(visitDto.startTimestamp).isEqualTo(visitStart)
    Assertions.assertThat(visitDto.endTimestamp).isEqualTo(visitEnd)

    Assertions.assertThat(visitDto.visitRestriction).isEqualTo(visit.visitRestriction)
    Assertions.assertThat(visitDto.visitStatus).isEqualTo(visit.visitStatus)
    Assertions.assertThat(visitDto.visitRoom).isEqualTo(visit.visitRoom)
    Assertions.assertThat(visitDto.applicationReference).isEqualTo(visit.applicationReference)
    Assertions.assertThat(visitDto.outcomeStatus).isEqualTo(visit.outcomeStatus)
    Assertions.assertThat(visitDto.prisonCode).isEqualTo(visit.prison.code)
    Assertions.assertThat(visitDto.prisonerId).isEqualTo(visit.prisonerId)

    Assertions.assertThat(visitDto.reference).isEqualTo(visit.reference)
    Assertions.assertThat(visitDto.sessionTemplateReference).isEqualTo(visit.sessionTemplateReference)
    Assertions.assertThat(visitDto.visitType).isEqualTo(visit.visitType)

    Assertions.assertThat(visitDto.createdTimestamp).isNotNull()
    Assertions.assertThat(visitDto.modifiedTimestamp).isNotNull()

    visit.visitContact?.let {
      Assertions.assertThat(visitDto.visitContact?.name).isEqualTo(it.name)
      Assertions.assertThat(visitDto.visitContact?.telephone).isEqualTo(it.telephone)
    }

    visit.visitNotes.let { notes ->
      Assertions.assertThat(visitDto.visitNotes).hasSize(notes.size)
      visitDto.visitNotes.forEach { dtoNotes ->
        val visitNote = visit.visitNotes.find { it.type == dtoNotes.type }!!
        Assertions.assertThat(dtoNotes.text).isEqualTo(visitNote.text)
      }
    }

    visit.support.let { supportList ->
      Assertions.assertThat(visitDto.visitorSupport).hasSize(supportList.size)
      visitDto.visitorSupport.forEach { dtoSupport ->
        val support = visit.support.find { it.type == dtoSupport.type }!!
        Assertions.assertThat(dtoSupport.text).isEqualTo(support.text)
      }
    }

    visit.visitors.let { visitors ->
      Assertions.assertThat(visitDto.visitors).hasSize(visitors.size)
      visitDto.visitors.forEach { dtoVisitors ->
        val visitor = visit.visitors.find { it.nomisPersonId == dtoVisitors.nomisPersonId }!!
        Assertions.assertThat(dtoVisitors.visitContact).isEqualTo(visitor.visitContact)
      }
    }
  }

  private fun create(
    visitStatus: VisitStatus = RESERVED,
    prisonerId: String = "FF0000AA",
    visitRoom: String = "A1",
    visitStart: LocalDateTime,
    visitEnd: LocalDateTime,
    visitType: VisitType = VisitType.SOCIAL,
    visitRestriction: VisitRestriction = VisitRestriction.OPEN,
    reference: String = "",
    outcomeStatus: OutcomeStatus? = null,
    sessionTemplateReference: String? = "sessionTemplateReference",
  ): Visit {
    val visit = Visit(
      visitStatus = visitStatus,
      prisonerId = prisonerId,
      prisonId = prison.id,
      prison = prison,
      visitRoom = visitRoom,
      visitStart = visitStart,
      visitEnd = visitEnd,
      visitType = visitType,
      visitRestriction = visitRestriction,
      _reference = reference,
      outcomeStatus = outcomeStatus,
      sessionTemplateReference = sessionTemplateReference,
    )

    visit.support.add(VisitSupport(1, visit.id, "test", "text", visit))
    visit.visitNotes.add(VisitNote(1, visit.id, VISIT_COMMENT, "text", visit))
    visit.visitors.add(VisitVisitor(1, visit.id, 123445, true, visit))
    visit.visitContact = VisitContact(1, visit.id, "test", "0123456", visit)

    return visit
  }
}
