package uk.gov.justice.digital.hmpps.visitscheduler.integration.task

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NonAssociationDomainEventType.NON_ASSOCIATION_CREATED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.NON_ASSOCIATION_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_RELEASED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerReleaseReasonType.RELEASED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NonAssociationChangedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerReleasedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.PrisonEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatNonAssociationHasChanged
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatPrisonerHadBeenReleased
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionCategoryGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.SessionIncentiveLevelGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionService
import uk.gov.justice.digital.hmpps.visitscheduler.task.FlagVisitsTask
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.function.Consumer

@Transactional(propagation = SUPPORTS)
@DisplayName("Flag Visits")
class FlagVisitsTaskTest : IntegrationTestBase() {
  @Autowired
  private lateinit var flagVisitsTask: FlagVisitsTask

  @MockitoSpyBean
  private lateinit var telemetryClient: TelemetryClient

  @MockitoSpyBean
  private lateinit var sessionService: SessionService

  private val prisonerAId = "Prisoner-A"
  private val prisonerBId = "Prisoner-B"
  private val prisonerCId = "Prisoner-C"

  private val visitDate = LocalDate.now().plusDays(7)
  private val startTime = LocalTime.of(9, 0)
  private val endTime = LocalTime.of(10, 0)

  @Captor
  lateinit var mapCapture: ArgumentCaptor<Map<String, String>>

  @BeforeEach
  internal fun setUp() {
    prison = PrisonEntityHelper.createPrison("FVT")
  }

  @Test
  fun `when prisoners have non associations after visit was created then visits are flagged`() {
    // Given
    val sessionTemplateReference = createSessionTemplate(startTime = startTime, endTime = endTime, dayOfWeek = visitDate.dayOfWeek)
    // 2nd session
    createSessionTemplate(startTime = startTime.plusHours(2), endTime = endTime.plusHours(2), dayOfWeek = visitDate.dayOfWeek)
    // prisoner A has non association with prisoner B who has a visit on the same day
    val prisonerAVisit = createApplicationAndVisit(prisonerId = prisonerAId, sessionTemplate = sessionTemplateReference)

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerAId,
      prisonerBId,
    )

    // prisoner B has a visit on the same day but offender list is empty for test purposes
    createApplicationAndVisit(prisonerId = prisonerBId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerBId)

    // prisoner C has no non associations
    createApplicationAndVisit(prisonerId = prisonerCId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerCId)

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerAId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerAId, "${prison.code}-C-1-C001")

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerBId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerBId, "${prison.code}-C-1-C001")

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerCId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerCId, "${prison.code}-C-1-C001")

    // When
    flagVisitsTask.flagVisits()
    // Then

    assertFlaggedVisitEvent(prisonerAVisit, "possible non-association or session not suitable")
  }

  @Test
  fun `when prisoners have change of location after visit was created then visits are flagged`() {
    // Given
    val permittedLocations = listOf(AllowedSessionLocationHierarchy("A"))

    // session only available to wing A prisoners
    val sessionTemplateReference = createSessionTemplate(
      startTime = startTime,
      endTime = endTime,
      dayOfWeek = visitDate.dayOfWeek,
      permittedLocations = permittedLocations,
    )

    // session available to all prisoners
    createSessionTemplate(
      startTime = startTime.plusHours(2),
      endTime = endTime.plusHours(2),
      dayOfWeek = visitDate.dayOfWeek,
    )

    val prisonerAVisit = createApplicationAndVisit(prisonerId = prisonerAId, sessionTemplate = sessionTemplateReference)
    // prisoner A has moved location since the visit was created
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerAId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerAId, "${prison.code}-B")
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerAId, prison.code)

    // prisoner B visit will not be flagged
    createApplicationAndVisit(prisonerId = prisonerBId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerBId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerBId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerBId, "${prison.code}-A-0-001")

    // prisoner C visit will not be flagged
    createApplicationAndVisit(prisonerId = prisonerCId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerCId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerCId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerCId, "${prison.code}-A-2")

    // When
    flagVisitsTask.flagVisits()
    // Then

    assertFlaggedVisitEvent(prisonerAVisit, "possible non-association or session not suitable")
  }

  @Test
  fun `when prisoners have change of category after visit was created then visits are flagged`() {
    // Given
    // session only available to category A Standard prisoners
    val sessionTemplate = createSessionTemplate(
      startTime = startTime,
      endTime = endTime,
      dayOfWeek = visitDate.dayOfWeek,
      permittedCategories = listOf(PrisonerCategoryType.A_STANDARD),
    )

    // 2nd session
    createSessionTemplate(startTime = startTime.plusHours(2), endTime = endTime.plusHours(2), dayOfWeek = visitDate.dayOfWeek)

    val prisonerAVisit = createApplicationAndVisit(prisonerId = prisonerAId, sessionTemplate = sessionTemplate)
    // prisoner A has changed category to B since the visit was created
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerAId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerAId, prison.code, category = PrisonerCategoryType.B.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerAId, "${prison.code}-B")

    // prisoner B visit will not be flagged
    createApplicationAndVisit(prisonerId = prisonerBId, sessionTemplate = sessionTemplate)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerBId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerBId, prison.code, category = PrisonerCategoryType.A_STANDARD.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerBId, "${prison.code}-A")

    // prisoner C visit will not be flagged
    createApplicationAndVisit(prisonerId = prisonerCId, sessionTemplate = sessionTemplate)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerCId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerCId, prison.code, category = PrisonerCategoryType.A_STANDARD.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerCId, "${prison.code}-C")

    // When
    flagVisitsTask.flagVisits()
    // Then

    assertFlaggedVisitEvent(prisonerAVisit, "possible non-association or session not suitable")
  }

  @Test
  fun `when prisoners have change of incentive level after visit was created then visits are flagged`() {
    // Given
    // session only available to category A Standard prisoners
    val sessionTemplateReference = createSessionTemplate(
      startTime = startTime,
      endTime = endTime,
      dayOfWeek = visitDate.dayOfWeek,
      permittedIncentiveLevels = listOf(IncentiveLevel.ENHANCED),
    )

    val prisonerAVisit = createApplicationAndVisit(prisonerId = prisonerAId, sessionTemplate = sessionTemplateReference)
    // prisoner A has changed category to B since the visit was created
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerAId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerAId, prison.code, incentiveLevelCode = IncentiveLevel.STANDARD)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerAId, "${prison.code}-B")

    // prisoner B visit will not be flagged as incentiveLevel is ENHANCED
    createApplicationAndVisit(prisonerId = prisonerBId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerBId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerBId, prison.code, incentiveLevelCode = IncentiveLevel.ENHANCED)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerBId, "${prison.code}-A")

    // prisoner C visit will not be flagged as incentiveLevel is ENHANCED
    createApplicationAndVisit(prisonerId = prisonerCId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerCId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerCId, prison.code, incentiveLevelCode = IncentiveLevel.ENHANCED)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerCId, "${prison.code}-C")

    // When
    flagVisitsTask.flagVisits()

    // Then

    assertFlaggedVisitEvent(prisonerAVisit, "possible non-association or session not suitable")
  }

  @Test
  fun `when prisoners have change of prison after visit was created then visits are flagged`() {
    // Given
    val newPrisonCode = "XYZ"
    val sessionTemplateReference = createSessionTemplate(
      startTime = startTime,
      endTime = endTime,
      dayOfWeek = visitDate.dayOfWeek,
    )

    // 2nd session
    createSessionTemplate(startTime = startTime.plusHours(2), endTime = endTime.plusHours(2), dayOfWeek = visitDate.dayOfWeek)

    val prisonerAVisit = createApplicationAndVisit(prisonerId = prisonerAId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerAId)
    // prisoner is now in prison XYZ
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerAId, newPrisonCode)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerAId, "${prison.code}-B")

    // prisoner B visit will not be flagged as in the same prison
    createApplicationAndVisit(prisonerId = prisonerBId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerBId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerBId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerBId, "${prison.code}-A")

    // prisoner C visit will not be flagged as in the same prison
    createApplicationAndVisit(prisonerId = prisonerCId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerCId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerCId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerCId, "${prison.code}-C")

    // When
    flagVisitsTask.flagVisits()

    // Then

    assertFlaggedVisitEvent(prisonerAVisit, "Prisoner - $prisonerAId has moved prison")
  }

  @Test
  fun `when prisoners has some other exception then visits are flagged with additionalInformation`() {
    // Given
    val sessionTemplateReference = createSessionTemplate(
      startTime = startTime,
      endTime = endTime,
      dayOfWeek = visitDate.dayOfWeek,
    )

    // 2nd session
    createSessionTemplate(startTime = startTime.plusHours(2), endTime = endTime.plusHours(2), dayOfWeek = visitDate.dayOfWeek)

    val prisonerAVisit = createApplicationAndVisit(prisonerId = prisonerAId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(prisonerAId)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerAId, null)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerAId, "${prison.code}-B")

    createApplicationAndVisit(prisonerId = prisonerBId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerBId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerBId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerBId, "${prison.code}-A")

    createApplicationAndVisit(prisonerId = prisonerCId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerCId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerCId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerCId, "${prison.code}-C")

    // When
    flagVisitsTask.flagVisits()

    // Then

    assertFlaggedVisitEvent(prisonerAVisit, "possible non-association or session not suitable")
  }

  @Test
  fun `when prisoners has notifications then visits are flagged with relevant details in additionalInformation`() {
    // Given
    val sessionTemplateReference = createSessionTemplate(
      startTime = startTime,
      endTime = endTime,
      dayOfWeek = visitDate.dayOfWeek,
    )

    // 2nd session
    createSessionTemplate(startTime = startTime.plusHours(2), endTime = endTime.plusHours(2), dayOfWeek = visitDate.dayOfWeek)
    val roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    val prisonerAVisit = createApplicationAndVisit(prisonerId = prisonerAId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerAId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerAId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerAId, "${prison.code}-B")

    val prisonerBVisit = createApplicationAndVisit(prisonerId = prisonerBId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerBId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerBId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerBId, "${prison.code}-A")

    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(NON_ASSOCIATION_CREATED, prisonerAId, prisonerBId)
    callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // When
    flagVisitsTask.flagVisits()
    verify(telemetryClient, times(4)).trackEvent(eq("flagged-visit-event"), mapCapture.capture(), isNull())

    // ignore 1st and 2nd events as they are non-association events
    val flaggedVisitForPrisoner1 = mapCapture.allValues[2]
    var test = getVisitAssert(prisonerAVisit, NON_ASSOCIATION_EVENT.description)
    test.accept(flaggedVisitForPrisoner1)

    val flaggedVisitForPrisoner2 = mapCapture.allValues[3]
    test = getVisitAssert(prisonerBVisit, NON_ASSOCIATION_EVENT.description)
    test.accept(flaggedVisitForPrisoner2)

    verify(sessionService, times(0)).getVisitSessions(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
  }

  @Test
  fun `when prisoners has multiple notifications then visits are flagged with relevant details in additionalInformation`() {
    // Given
    val sessionTemplateReference = createSessionTemplate(
      startTime = startTime,
      endTime = endTime,
      dayOfWeek = visitDate.dayOfWeek,
    )
    // 2nd session
    createSessionTemplate(startTime = startTime.plusHours(2), endTime = endTime.plusHours(2), dayOfWeek = visitDate.dayOfWeek)

    val roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    val prisonerAVisit = createApplicationAndVisit(prisonerId = prisonerAId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerAId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerAId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerAId, "${prison.code}-B")

    val prisonerBVisit = createApplicationAndVisit(prisonerId = prisonerBId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerBId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerBId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerBId, "${prison.code}-A")

    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(NON_ASSOCIATION_CREATED, prisonerAId, prisonerBId)
    callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    val prisonerReleasedNotification = PrisonerReleasedNotificationDto(prisonerAId, prison.code, RELEASED)
    callNotifyVSiPThatPrisonerHadBeenReleased(webTestClient, roleVisitSchedulerHttpHeaders, prisonerReleasedNotification)

    // When
    flagVisitsTask.flagVisits()
    verify(telemetryClient, times(5)).trackEvent(eq("flagged-visit-event"), mapCapture.capture(), isNull())

    // ignore 1st 3 events as they are non-association and prisoner-release events
    val flaggedVisitForPrisoner1 = mapCapture.allValues[3]
    var test = getVisitAssert(prisonerAVisit, "${NON_ASSOCIATION_EVENT.description}, ${PRISONER_RELEASED_EVENT.description}")
    test.accept(flaggedVisitForPrisoner1)

    val flaggedVisitForPrisoner2 = mapCapture.allValues[4]
    test = getVisitAssert(prisonerBVisit, NON_ASSOCIATION_EVENT.description)
    test.accept(flaggedVisitForPrisoner2)

    verify(sessionService, times(0)).getVisitSessions(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
  }

  @Test
  fun `when prisoners has no notifications or invalid sessions then no visits are flagged`() {
    // Given
    val sessionTemplateReference = createSessionTemplate(
      startTime = startTime,
      endTime = endTime,
      dayOfWeek = visitDate.dayOfWeek,
    )

    createApplicationAndVisit(prisonerId = prisonerAId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerAId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerAId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerAId, "${prison.code}-B")

    // When
    flagVisitsTask.flagVisits()
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), mapCapture.capture(), isNull())
    verify(sessionService, times(1)).getVisitSessions(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
  }

  private fun assertFlaggedVisitEvent(visit: Visit, additionalInformation: String) {
    verify(telemetryClient, times(1)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    val base = getVisitAssert(visit, additionalInformation)
    assertFlaggedVisitEvent(base)
  }

  private fun getVisitAssert(visit: Visit, additionalInformation: String): Consumer<Map<String, String>> {
    return Consumer<Map<String, String>> {
      assertThat(it["reference"]).isEqualTo(visit.reference)
      assertThat(it["prisonerId"]).isEqualTo(visit.prisonerId)
      assertThat(it["prisonId"]).isEqualTo(visit.prison.code)
      assertThat(it["visitType"]).isEqualTo(visit.visitType.name)
      assertThat(it["visitRestriction"]).isEqualTo(visit.visitRestriction.name)
      assertThat(it["visitStart"]).isEqualTo(formatStartSlotDateTimeToString(visit.sessionSlot))
      assertThat(it["visitEnd"]).isEqualTo(formatSlotEndDateTimeToString(visit.sessionSlot))
      assertThat(it["reviewType"]).isEqualTo("flag-visits-report")
      assertThat(it["additionalInformation"]).isEqualTo(additionalInformation)
    }
  }

  private fun assertFlaggedVisitEvent(test: Consumer<Map<String, String>>) {
    verify(telemetryClient).trackEvent(
      eq("flagged-visit-event"),
      org.mockito.kotlin.check { test.accept(it) },
      isNull(),
    )
  }

  private fun createSessionTemplate(
    startTime: LocalTime,
    endTime: LocalTime,
    dayOfWeek: DayOfWeek,
    permittedLocations: List<AllowedSessionLocationHierarchy>? = null,
    permittedCategories: List<PrisonerCategoryType>? = null,
    permittedIncentiveLevels: List<IncentiveLevel>? = null,
  ): SessionTemplate {
    val permittedLocationGroups: MutableList<SessionLocationGroup> = mutableListOf()
    val permittedCategoryGroups: MutableList<SessionCategoryGroup> = mutableListOf()
    val permittedIncentiveLevelGroups: MutableList<SessionIncentiveLevelGroup> = mutableListOf()

    permittedLocations?.let {
      permittedLocationGroups.add(sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = it))
    }

    permittedCategories?.let {
      permittedCategoryGroups.add(sessionPrisonerCategoryHelper.create(prisonCode = prison.code, prisonerCategories = permittedCategories))
    }

    permittedIncentiveLevels?.let {
      permittedIncentiveLevelGroups.add(sessionPrisonerIncentiveLevelHelper.create(name = "ENHANCED", prisonCode = prison.code, incentiveLevelList = permittedIncentiveLevels))
    }

    return sessionTemplateEntityHelper.create(
      prisonCode = prison.code,
      startTime = startTime,
      endTime = endTime,
      dayOfWeek = dayOfWeek,
      permittedLocationGroups = permittedLocationGroups,
      permittedCategories = permittedCategoryGroups,
      permittedIncentiveLevels = permittedIncentiveLevelGroups,
    )
  }
}
