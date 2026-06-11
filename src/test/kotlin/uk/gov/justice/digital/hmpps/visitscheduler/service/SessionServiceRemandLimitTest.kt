package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLocationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.PrisonEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionSlotRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionDatesUtil
import java.time.LocalDate
import java.time.LocalTime

@ExtendWith(MockitoExtension::class)
class SessionServiceRemandLimitTest {

  private val sessionTemplateRepository = mock<SessionTemplateRepository>()
  private val visitRepository = mock<VisitRepository>()
  private val sessionSlotRepository = mock<SessionSlotRepository>()
  private val prisonerService = mock<PrisonerService>()
  private val prisonerValidationService = mock<PrisonerValidationService>()
  private val sessionDatesUtil = SessionDatesUtil()
  private val prisonsService = mock<PrisonsService>()
  private val applicationService = mock<ApplicationService>()
  private val sessionValidationService = mock<PrisonerSessionValidationService>()

  private lateinit var sessionService: SessionService

  private val currentDate = LocalDate.now()

  private val prisonCode = "MDI"
  private val noticeDaysMin = 0
  private val noticeDaysMax = 100
  private val prisonerId = "AA1234BB"

  @BeforeEach
  fun beforeEachTestSetup() {
    whenever(prisonerService.getPrisonerHousingLocation(any(), any())).thenReturn(
      PrisonerHousingLocationsDto(
        levels = listOf(),
      ),
    )
    whenever(prisonerService.getLevelsMapForPrisoner(any(), any())).thenReturn(mutableMapOf())
    whenever(sessionValidationService.isSessionAvailableToPrisoner(any(), any(), any(), any())).thenReturn(true)
  }

  @BeforeEach
  fun setUp() {
    sessionService = SessionService(
      sessionDatesUtil = sessionDatesUtil,
      sessionTemplateRepository = sessionTemplateRepository,
      visitRepository = visitRepository,
      sessionSlotRepository = sessionSlotRepository,
      prisonerService = prisonerService,
      policyFilterDoubleBooking = false,
      policyFilterNonAssociation = false,
      prisonerSessionValidationService = sessionValidationService,
      prisonerValidationService = prisonerValidationService,
      prisonsService = prisonsService,
      applicationService = applicationService,
    )
  }

  @Test
  fun `when remand limit reached then all sessions for the week contain REMAND_VISITS_LIMIT_REACHED conflict`() {
    // Given
    val validFromDate = currentDate.plusDays(noticeDaysMin.toLong() + 1)
    val dayOfWeek = validFromDate.dayOfWeek

    whenever(prisonsService.findPrisonByCode(prisonCode)).thenReturn(
      PrisonEntityHelper.createPrison(
        prisonCode = prisonCode,
        policyNoticeDaysMin = noticeDaysMin,
        policyNoticeDaysMax = noticeDaysMax,
        remandVisitLimitPerWeek = 1,
        weekStartDay = dayOfWeek,
      ),
    )

    whenever(prisonerService.getPrisoner(any())).thenReturn(
      PrisonerDto(
        prisonerId = prisonerId,
        firstName = "John",
        lastName = "Smith",
        prisonCode = prisonCode,
        convictedStatus = "Remand",
      ),
    )

    val session1 = sessionTemplate(
      validFromDate = validFromDate,
      validToDate = validFromDate,
      startTime = LocalTime.parse("11:30"),
      endTime = LocalTime.parse("12:30"),
      dayOfWeek = dayOfWeek,
    )

    val session2 = sessionTemplate(
      validFromDate = validFromDate,
      validToDate = validFromDate,
      startTime = LocalTime.parse("12:31"),
      endTime = LocalTime.parse("13:30"),
      dayOfWeek = dayOfWeek,
    )

    mockSessionTemplateRepositoryResponse(listOf(session1, session2))

    val visit1 = visit(prisonerId, validFromDate, session1)

    whenever(
      prisonerService.getPrisonerNonAssociationList(prisonerId),
    ).thenReturn(emptyList())

    whenever(visitRepository.getBookedVisits(any(), anyOrNull(), any(), anyOrNull())).thenReturn(listOf(visit1))

    // When
    val sessions = sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)

    // Then
    assertThat(sessions).size().isEqualTo(2)
    assertThat(sessions[0].sessionTemplateReference).isEqualTo(session1.reference)
    assertThat(sessions[0].sessionConflicts).size().isEqualTo(1)
    assertThat(sessions[0].sessionConflicts.first()).isEqualTo(SessionConflict.REMAND_VISITS_LIMIT_REACHED)

    assertThat(sessions[1].sessionTemplateReference).isEqualTo(session2.reference)
    assertThat(sessions[1].sessionConflicts).size().isEqualTo(1)
    assertThat(sessions[1].sessionConflicts.first()).isEqualTo(SessionConflict.REMAND_VISITS_LIMIT_REACHED)
  }

  @Test
  fun `when remand limit not reached sessions then other sessions for the week do not REMAND_VISITS_LIMIT_REACHED conflict`() {
    // Given
    val validFromDate = currentDate.plusDays(noticeDaysMin.toLong() + 1)
    val dayOfWeek = validFromDate.dayOfWeek

    // remand limit is 2
    whenever(prisonsService.findPrisonByCode(prisonCode)).thenReturn(
      PrisonEntityHelper.createPrison(
        prisonCode = prisonCode,
        policyNoticeDaysMin = noticeDaysMin,
        policyNoticeDaysMax = noticeDaysMax,
        remandVisitLimitPerWeek = 2,
        weekStartDay = dayOfWeek,
      ),
    )

    whenever(prisonerService.getPrisoner(any())).thenReturn(
      PrisonerDto(
        prisonerId = prisonerId,
        firstName = "John",
        lastName = "Smith",
        prisonCode = prisonCode,
        convictedStatus = "Remand",
      ),
    )

    val session1 = sessionTemplate(
      validFromDate = validFromDate,
      validToDate = validFromDate,
      startTime = LocalTime.parse("11:30"),
      endTime = LocalTime.parse("12:30"),
      dayOfWeek = dayOfWeek,
    )

    val session2 = sessionTemplate(
      validFromDate = validFromDate,
      validToDate = validFromDate,
      startTime = LocalTime.parse("12:31"),
      endTime = LocalTime.parse("13:30"),
      dayOfWeek = dayOfWeek,
    )

    mockSessionTemplateRepositoryResponse(listOf(session1, session2))

    // only 1 visit booked for the week
    val visit1 = visit(prisonerId, validFromDate, session1)

    whenever(
      prisonerService.getPrisonerNonAssociationList(prisonerId),
    ).thenReturn(emptyList())

    whenever(visitRepository.hasActiveVisitForSessionSlot(any(), any(), anyOrNull())).thenReturn(false)
    whenever(visitRepository.getBookedVisits(any(), anyOrNull(), any(), anyOrNull())).thenReturn(listOf(visit1))

    // When
    val sessions = sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)

    // Then
    assertThat(sessions).size().isEqualTo(2)
    assertThat(sessions[0].sessionTemplateReference).isEqualTo(session1.reference)
    assertThat(sessions[0].sessionConflicts).size().isEqualTo(0)

    assertThat(sessions[1].sessionTemplateReference).isEqualTo(session2.reference)
    assertThat(sessions[1].sessionConflicts).size().isEqualTo(0)
  }

  @Test
  fun `when remand limit not reached for the week then other sessions for the week do not have REMAND_VISITS_LIMIT_REACHED conflict`() {
    // Given
    val validFromDate = currentDate.plusDays(noticeDaysMin.toLong() + 1)
    val dayOfWeek = validFromDate.dayOfWeek

    // remand limit is 1
    whenever(prisonsService.findPrisonByCode(prisonCode)).thenReturn(
      PrisonEntityHelper.createPrison(
        prisonCode = prisonCode,
        policyNoticeDaysMin = noticeDaysMin,
        policyNoticeDaysMax = noticeDaysMax,
        remandVisitLimitPerWeek = 1,
        weekStartDay = dayOfWeek,
      ),
    )

    whenever(prisonerService.getPrisoner(any())).thenReturn(
      PrisonerDto(
        prisonerId = prisonerId,
        firstName = "John",
        lastName = "Smith",
        prisonCode = prisonCode,
        convictedStatus = "Remand",
      ),
    )

    val session1 = sessionTemplate(
      validFromDate = validFromDate,
      validToDate = validFromDate,
      startTime = LocalTime.parse("11:30"),
      endTime = LocalTime.parse("12:30"),
      dayOfWeek = dayOfWeek,
    )

    val session2 = sessionTemplate(
      validFromDate = validFromDate,
      validToDate = validFromDate,
      startTime = LocalTime.parse("12:31"),
      endTime = LocalTime.parse("13:30"),
      dayOfWeek = dayOfWeek,
    )

    mockSessionTemplateRepositoryResponse(listOf(session1, session2))

    // visit1 booked - falls in last week
    val visit1 = visit(prisonerId, validFromDate.minusDays(1), session1)

    // visit2 booked - falls in next week
    val visit2 = visit(prisonerId, validFromDate.plusWeeks(1), session1)

    whenever(
      prisonerService.getPrisonerNonAssociationList(prisonerId),
    ).thenReturn(emptyList())

    whenever(visitRepository.hasActiveVisitForSessionSlot(any(), any(), anyOrNull())).thenReturn(false)
    whenever(visitRepository.getBookedVisits(any(), anyOrNull(), any(), anyOrNull())).thenReturn(listOf(visit1, visit2))

    // When
    val sessions = sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)

    // Then
    assertThat(sessions).size().isEqualTo(2)
    assertThat(sessions[0].sessionTemplateReference).isEqualTo(session1.reference)
    assertThat(sessions[0].sessionConflicts).size().isEqualTo(0)

    assertThat(sessions[1].sessionTemplateReference).isEqualTo(session2.reference)
    assertThat(sessions[1].sessionConflicts).size().isEqualTo(0)
  }

  @Test
  fun `when prisoner is convicted then no sessions for the week contains REMAND_VISITS_LIMIT_REACHED conflict`() {
    // Given
    val prisonerId = "A1234AA"
    val validFromDate = currentDate.plusDays(noticeDaysMin.toLong() + 1)
    val dayOfWeek = validFromDate.dayOfWeek

    whenever(prisonerService.getPrisoner(any())).thenReturn(
      PrisonerDto(
        prisonerId = prisonerId,
        firstName = "John",
        lastName = "Smith",
        category = "C",
        incentiveLevel = IncentiveLevel.STANDARD,
        prisonCode = prisonCode,
        convictedStatus = "Convicted",
      ),
    )

    whenever(prisonsService.findPrisonByCode(prisonCode)).thenReturn(
      PrisonEntityHelper.createPrison(
        prisonCode = prisonCode,
        policyNoticeDaysMin = noticeDaysMin,
        policyNoticeDaysMax = noticeDaysMax,
        remandVisitLimitPerWeek = 1,
        weekStartDay = dayOfWeek,
      ),
    )

    val session1 = sessionTemplate(
      validFromDate = validFromDate,
      validToDate = validFromDate,
      startTime = LocalTime.parse("11:30"),
      endTime = LocalTime.parse("12:30"),
      dayOfWeek = dayOfWeek,
    )

    val session2 = sessionTemplate(
      validFromDate = validFromDate,
      validToDate = validFromDate,
      startTime = LocalTime.parse("12:31"),
      endTime = LocalTime.parse("13:30"),
      dayOfWeek = dayOfWeek,
    )
    mockSessionTemplateRepositoryResponse(listOf(session1, session2))

    val visit1 = visit(prisonerId, validFromDate, session1)

    whenever(
      prisonerService.getPrisonerNonAssociationList(prisonerId),
    ).thenReturn(emptyList())

    whenever(visitRepository.hasActiveVisitForSessionSlot(any(), any(), anyOrNull())).thenReturn(false)
    whenever(visitRepository.getBookedVisits(any(), anyOrNull(), any(), anyOrNull())).thenReturn(listOf(visit1))

    // When
    val sessions = sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)

    // Then
    assertThat(sessions).size().isEqualTo(2)
    assertThat(sessions[0].sessionTemplateReference).isEqualTo(session1.reference)
    assertThat(sessions[0].sessionConflicts).size().isEqualTo(0)

    assertThat(sessions[1].sessionTemplateReference).isEqualTo(session2.reference)
    assertThat(sessions[1].sessionConflicts).size().isEqualTo(0)
  }

  private fun mockSessionTemplateRepositoryResponse(response: List<SessionTemplate>) {
    whenever(
      sessionTemplateRepository.findSessionTemplateMinCapacityBy(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()),
    ).thenReturn(response)
  }
}
