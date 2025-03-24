package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrivatePrisonVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.exception.PrisonNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionSlotRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@ActiveProfiles("test")
class VisitStoreServiceTest {
  private val visitRepository = mock<VisitRepository>()
  private val prisonRepository = mock<PrisonRepository>()
  private val sessionSlotRepository = mock<SessionSlotRepository>()
  private val applicationValidationService = mock<ApplicationValidationService>()
  private val applicationService = mock<ApplicationService>()

  private val visitStoreService: VisitStoreService = VisitStoreService(visitRepository, prisonRepository, sessionSlotRepository, applicationValidationService, applicationService, 28)

  @Nested
  @DisplayName("createVisit")
  inner class CreateVisit {
    private val privatePrisonVisitDto = PrivatePrisonVisitDto(
      prisonerId = "AF34567G",
      prisonId = "MDI",
      clientVisitReference = "client-visit-reference-1",
      visitRoom = "A1",
      visitType = VisitType.SOCIAL,
      visitStatus = VisitStatus.BOOKED,
      visitRestriction = VisitRestriction.OPEN,
      startTimestamp = LocalDateTime.parse("2018-12-01T13:45:00"),
      endTimestamp = LocalDateTime.parse("2018-12-01T13:45:00"),
      createDateTime = LocalDateTime.parse("2018-12-01T13:45:00"),
      visitors = setOf(
        VisitorDto(nomisPersonId = 1234, visitContact = true),
        VisitorDto(nomisPersonId = 4321, visitContact = false),
      ),
      actionedBy = "test-user",
    )

    private val prison =
      Prison(
        code = "1234",
        active = true,
        policyNoticeDaysMin = 1,
        policyNoticeDaysMax = 2,
        maxTotalVisitors = 2,
        maxAdultVisitors = 1,
        maxChildVisitors = 1,
        adultAgeYears = 18,
      )

    private val sessionSlot = SessionSlot(
      prisonId = prison.id,
      slotDate = privatePrisonVisitDto.startTimestamp.toLocalDate(),
      slotStart = privatePrisonVisitDto.startTimestamp,
      slotEnd = privatePrisonVisitDto.endTimestamp,
    )

    private val visit = Visit(
      prisonId = prison.id,
      prison = prison,
      prisonerId = privatePrisonVisitDto.prisonerId,
      sessionSlotId = sessionSlot.id,
      sessionSlot = sessionSlot,
      visitType = privatePrisonVisitDto.visitType,
      visitRestriction = privatePrisonVisitDto.visitRestriction,
      visitRoom = privatePrisonVisitDto.visitRoom,
      visitStatus = privatePrisonVisitDto.visitStatus,
      userType = UserType.PRIVATE,
    )

    @Test
    fun `throws an exception if there's no prison found`() {
      whenever(
        prisonRepository.findByCode(privatePrisonVisitDto.prisonId),
      ).thenReturn(
        null,
      )

      assertThrows<PrisonNotFoundException> { visitStoreService.createPrivatePrisonVisit(privatePrisonVisitDto) }
    }

    @Test
    fun `creates a visit`() {
      whenever(
        prisonRepository.findByCode(privatePrisonVisitDto.prisonId),
      ).thenReturn(prison)
      whenever(sessionSlotRepository.saveAndFlush(sessionSlot)).thenReturn(sessionSlot)
      whenever(visitRepository.saveAndFlush(visit)).thenReturn(visit)

      val visitId = visitStoreService.createPrivatePrisonVisit(privatePrisonVisitDto)
      verify(visitRepository, times(1)).saveAndFlush(visit)
      assertThat(visitId).isEqualTo(0)
    }
  }
}
