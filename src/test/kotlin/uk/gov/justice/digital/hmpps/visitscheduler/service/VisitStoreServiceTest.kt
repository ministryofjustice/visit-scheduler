package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateVisitFromExternalSystemDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdateVisitFromExternalSystemDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitNoteDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.builder.VisitDtoBuilder
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.exception.PrisonNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.helper.PrisonEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@ActiveProfiles("test")
@ExtendWith(MockitoExtension::class)
internal class VisitStoreServiceTest {
  private val visitRepository = mock<VisitRepository>()
  private val prisonRepository = mock<PrisonRepository>()
  private val sessionSlotService = mock<SessionSlotService>()
  private val applicationValidationService = mock<ApplicationValidationService>()
  private val applicationService = mock<ApplicationService>()
  private val visitDtoBuilder = mock<VisitDtoBuilder>()

  private val visitStoreService: VisitStoreService = VisitStoreService(visitRepository, prisonRepository, sessionSlotService, applicationValidationService, applicationService, visitDtoBuilder, 28, false)

  @Nested
  @DisplayName("createVisitFromExternalSystem")
  inner class CreateVisitFromExternalSystem {
    private val createVisitFromExternalSystemDto = CreateVisitFromExternalSystemDto(
      prisonerId = "AF34567G",
      prisonId = "MKI",
      clientName = "client_name",
      clientVisitReference = "client-visit-reference-1",
      visitRoom = "A1",
      visitType = VisitType.SOCIAL,
      visitRestriction = VisitRestriction.OPEN,
      startTimestamp = LocalDateTime.parse("2018-12-01T13:45:00"),
      endTimestamp = LocalDateTime.parse("2018-12-01T13:45:00"),
      visitContact = ContactDto(
        name = "John Smith",
        telephone = "01234567890",
        email = "email@example.com",
      ),
      visitNotes = listOf(
        VisitNoteDto(
          type = VisitNoteType.VISITOR_CONCERN,
          text = "Visitor is concerned that his mother in-law is coming!",
        ),
      ),
      createDateTime = LocalDateTime.parse("2018-12-01T13:45:00"),
      visitors = setOf(
        VisitorDto(nomisPersonId = 1234, visitContact = true),
        VisitorDto(nomisPersonId = 4321, visitContact = false),
      ),
      visitorSupport = VisitorSupportDto(
        description = "Visually impaired assistance",
      ),
    )

    private val prison = PrisonEntityHelper.createPrison(
      prisonCode = "1234",
      activePrison = true,
      maxTotalVisitors = 2,
      maxAdultVisitors = 1,
      maxChildVisitors = 1,
      adultAgeYears = 18,
    )

    private val sessionSlot = SessionSlot(
      prisonId = prison.id,
      slotDate = createVisitFromExternalSystemDto.startTimestamp.toLocalDate(),
      slotStart = createVisitFromExternalSystemDto.startTimestamp,
      slotEnd = createVisitFromExternalSystemDto.endTimestamp,
    )

    private val visit = Visit(
      prisonId = prison.id,
      prison = prison,
      prisonerId = createVisitFromExternalSystemDto.prisonerId,
      sessionSlotId = sessionSlot.id,
      sessionSlot = sessionSlot,
      visitType = createVisitFromExternalSystemDto.visitType,
      visitRestriction = createVisitFromExternalSystemDto.visitRestriction,
      visitRoom = createVisitFromExternalSystemDto.visitRoom,
      visitStatus = VisitStatus.BOOKED,
      visitSubStatus = VisitSubStatus.AUTO_APPROVED,
      userType = UserType.PRISONER,
    )

    @Test
    fun `throws an exception if there's no prison found`() {
      whenever(
        prisonRepository.findByCode(createVisitFromExternalSystemDto.prisonId),
      ).thenReturn(
        null,
      )

      assertThrows<PrisonNotFoundException> { visitStoreService.createVisitFromExternalSystem(createVisitFromExternalSystemDto) }
    }

    @Test
    fun `creates a visit`() {
      whenever(
        prisonRepository.findByCode(createVisitFromExternalSystemDto.prisonId),
      ).thenReturn(prison)
      whenever(
        sessionSlotService.getSessionSlot(
          startTimeDate = createVisitFromExternalSystemDto.startTimestamp.truncatedTo(ChronoUnit.MINUTES),
          endTimeAndDate = createVisitFromExternalSystemDto.endTimestamp.truncatedTo(ChronoUnit.MINUTES),
          prison = prison,
        ),
      ).thenReturn(sessionSlot)
      whenever(visitRepository.saveAndFlush(visit)).thenReturn(visit)

      visitStoreService.createVisitFromExternalSystem(createVisitFromExternalSystemDto)
      verify(visitRepository, times(2)).saveAndFlush(any<Visit>())
      verify(visitDtoBuilder, times(1)).build(any<Visit>())
    }
  }

  @Nested
  @DisplayName("updateVisitFromExternalSystem")
  inner class UpdateVisitFromExternalSystem {
    private val updateVisitFromExternalSystemDto = UpdateVisitFromExternalSystemDto(
      visitRoom = "A1",
      visitType = VisitType.SOCIAL,
      visitRestriction = VisitRestriction.OPEN,
      startTimestamp = LocalDateTime.parse("2018-12-01T13:45:00"),
      endTimestamp = LocalDateTime.parse("2018-12-01T13:45:00"),
      visitContact = ContactDto(
        name = "John Smith",
        telephone = "01234567890",
        email = "email@example.com",
      ),
      visitNotes = listOf(
        VisitNoteDto(
          type = VisitNoteType.VISITOR_CONCERN,
          text = "Visitor is concerned that his mother in-law is coming!",
        ),
      ),
      visitors = setOf(
        VisitorDto(nomisPersonId = 1234, visitContact = true),
        VisitorDto(nomisPersonId = 4321, visitContact = false),
      ),
      visitorSupport = VisitorSupportDto(
        description = "Visually impaired assistance",
      ),
    )

    private val prison = PrisonEntityHelper.createPrison(
      prisonCode = "1234",
      activePrison = true,
      maxTotalVisitors = 2,
      maxAdultVisitors = 1,
      maxChildVisitors = 1,
      adultAgeYears = 18,
    )

    private val sessionSlot = SessionSlot(
      prisonId = prison.id,
      slotDate = updateVisitFromExternalSystemDto.startTimestamp.toLocalDate(),
      slotStart = updateVisitFromExternalSystemDto.startTimestamp,
      slotEnd = updateVisitFromExternalSystemDto.endTimestamp,
    )

    private val visit = Visit(
      prisonId = prison.id,
      prison = prison,
      prisonerId = "AF34567G",
      sessionSlotId = sessionSlot.id,
      sessionSlot = sessionSlot,
      visitType = updateVisitFromExternalSystemDto.visitType,
      visitRestriction = updateVisitFromExternalSystemDto.visitRestriction,
      visitRoom = updateVisitFromExternalSystemDto.visitRoom,
      visitStatus = VisitStatus.BOOKED,
      visitSubStatus = VisitSubStatus.AUTO_APPROVED,
      userType = UserType.PRISONER,
    )

    @Test
    fun `updates a visit`() {
      whenever(
        sessionSlotService.getSessionSlot(
          startTimeDate = updateVisitFromExternalSystemDto.startTimestamp.truncatedTo(ChronoUnit.MINUTES),
          endTimeAndDate = updateVisitFromExternalSystemDto.endTimestamp.truncatedTo(ChronoUnit.MINUTES),
          prison = prison,
        ),
      ).thenReturn(sessionSlot)
      whenever(visitRepository.saveAndFlush(visit)).thenReturn(visit)

      visitStoreService.updateVisitFromExternalSystem(updateVisitFromExternalSystemDto, visit)
      verify(visitRepository, atLeastOnce()).saveAndFlush(any<Visit>())
      verify(visitDtoBuilder, times(1)).build(any<Visit>())
    }
  }
}
