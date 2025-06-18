package uk.gov.justice.digital.hmpps.visitscheduler.dto.builder

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.ApplicationEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.PrisonEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitExternalSystemDetails
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitSupport
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionTemplateService
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@ExtendWith(MockitoExtension::class)
class VisitDtoBuilderTest {

  private var prison: Prison = PrisonEntityHelper.createPrison()
  private val sessionTemplateService = mock<SessionTemplateService>()

  @InjectMocks
  val toTest: VisitDtoBuilder = VisitDtoBuilder()

  @Test
  fun `Visit Dto is built correctly from given entities`() {
    // Given
    val now = LocalDateTime.now()
    val slotDate = now.toLocalDate()
    val visitStart = now.toLocalTime()
    val visitEnd = visitStart.plusHours(2)

    val visit = create(slotDate = slotDate, visitStart = visitStart, visitEnd = visitEnd, reference = "test")
    val slot = SessionTimeSlotDto(visitStart, visitEnd)

    whenever(sessionTemplateService.getSessionTimeSlotDto(visit.sessionSlot.reference)).thenReturn(slot)

    // When

    val result = toTest.build(visit)

    // Then
    assertVisitDto(result, visit, slotDate, visitStart, visitEnd)
  }

  @Test
  fun `Visit Dto is built correctly from given entities when it had an external system client reference`() {
    // Given
    val now = LocalDateTime.now()
    val slotDate = now.toLocalDate()
    val visitStart = now.toLocalTime()
    val visitEnd = visitStart.plusHours(2)
    val clientName = "client-name"
    val clientReference = "client-reference"

    val visit = create(slotDate = slotDate, visitStart = visitStart, visitEnd = visitEnd, reference = "test", isFromExternalSystem = true, clientName = clientName, clientReference = clientReference)

    // When
    val result = toTest.build(visit)

    // Then
    assertVisitDto(result, visit, slotDate, visitStart, visitEnd)
  }

  private fun assertVisitDto(
    visitDto: VisitDto,
    visit: Visit,
    slotDate: LocalDate,
    visitStart: LocalTime,
    visitEnd: LocalTime,
  ) {
    Assertions.assertThat(visitDto.startTimestamp).isEqualTo(slotDate.atTime(visitStart))
    Assertions.assertThat(visitDto.endTimestamp).isEqualTo(slotDate.atTime(visitEnd))

    Assertions.assertThat(visitDto.visitRestriction).isEqualTo(visit.visitRestriction)
    Assertions.assertThat(visitDto.visitStatus).isEqualTo(visit.visitStatus)
    Assertions.assertThat(visitDto.visitRoom).isEqualTo(visit.visitRoom)
    Assertions.assertThat(visitDto.applicationReference).isEqualTo(visit.getLastApplication()?.reference)
    Assertions.assertThat(visitDto.outcomeStatus).isEqualTo(visit.outcomeStatus)
    Assertions.assertThat(visitDto.prisonCode).isEqualTo(visit.prison.code)
    Assertions.assertThat(visitDto.prisonerId).isEqualTo(visit.prisonerId)

    Assertions.assertThat(visitDto.reference).isEqualTo(visit.reference)
    Assertions.assertThat(visitDto.sessionTemplateReference).isEqualTo(visit.sessionSlot.sessionTemplateReference)
    Assertions.assertThat(visitDto.visitType).isEqualTo(visit.visitType)

    Assertions.assertThat(visitDto.createdTimestamp).isNotNull()
    Assertions.assertThat(visitDto.modifiedTimestamp).isNotNull()

    visit.visitContact?.let {
      Assertions.assertThat(visitDto.visitContact.name).isEqualTo(it.name)
      Assertions.assertThat(visitDto.visitContact.telephone).isEqualTo(it.telephone)
    }

    visit.visitNotes.let { notes ->
      Assertions.assertThat(visitDto.visitNotes).hasSize(notes.size)
      visitDto.visitNotes.forEach { dtoNotes ->
        val visitNote = visit.visitNotes.find { it.type == dtoNotes.type }!!
        Assertions.assertThat(dtoNotes.text).isEqualTo(visitNote.text)
      }
    }

    visit.support?.let {
      Assertions.assertThat(it.description).isEqualTo(visitDto.visitorSupport?.description)
    }

    visit.visitors.let { visitors ->
      Assertions.assertThat(visitDto.visitors).hasSize(visitors.size)
      visitDto.visitors.forEach { dtoVisitors ->
        val visitor = visit.visitors.find { it.nomisPersonId == dtoVisitors.nomisPersonId }!!
        Assertions.assertThat(dtoVisitors.visitContact).isEqualTo(visitor.visitContact)
      }
    }

    visit.visitExternalSystemDetails?.let {
      Assertions.assertThat(visitDto.visitExternalSystemDetails?.clientName).isEqualTo(it.clientName)
      Assertions.assertThat(visitDto.visitExternalSystemDetails?.clientVisitReference).isEqualTo(it.clientReference)
    }
  }

  private fun create(
    visitStatus: VisitStatus = BOOKED,
    visitSubStatus: VisitSubStatus = VisitSubStatus.AUTO_APPROVED,
    prisonerId: String = "FF0000AA",
    visitRoom: String = "A1",
    slotDate: LocalDate,
    visitStart: LocalTime,
    visitEnd: LocalTime,
    visitType: VisitType = VisitType.SOCIAL,
    visitRestriction: VisitRestriction = VisitRestriction.OPEN,
    reference: String = "",
    outcomeStatus: OutcomeStatus? = null,
    sessionTemplateReference: String? = "sessionTemplateReference",
    userType: UserType = UserType.STAFF,
    isFromExternalSystem: Boolean = false,
    clientName: String? = null,
    clientReference: String? = null,
  ): Visit {
    val sessionSlot = SessionSlot(sessionTemplateReference, prison.id, slotDate, slotDate.atTime(visitStart), slotDate.atTime(visitEnd))

    val visit = Visit(
      visitStatus = visitStatus,
      visitSubStatus = visitSubStatus,
      prisonerId = prisonerId,
      prisonId = prison.id,
      prison = prison,
      visitRoom = visitRoom,
      visitType = visitType,
      visitRestriction = visitRestriction,
      sessionSlotId = sessionSlot.id,
      sessionSlot = sessionSlot,
      userType = userType,
    )

    visit.outcomeStatus = outcomeStatus
    visit.support = VisitSupport(1, visit.id, "description", visit)
    visit.visitNotes.add(VisitNote(1, visit.id, VISIT_COMMENT, "text", visit))
    visit.visitors.add(VisitVisitor(1, visit.id, 123445, true, visit))
    visit.visitContact = VisitContact(1, visit.id, "test", "0123456", "email@example.com", visit)

    if (isFromExternalSystem && clientName != null && clientReference != null) {
      visit.visitExternalSystemDetails = VisitExternalSystemDetails(visit.id, clientName, clientReference, visit)
    }
    val spyVisit = spy(visit)

    doReturn(reference).`when`(spyVisit).reference

    if (!isFromExternalSystem) {
      visit.addApplication(ApplicationEntityHelper.createApplication(spyVisit))
    }

    return spyVisit
  }
}
