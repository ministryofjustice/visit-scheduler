package uk.gov.justice.digital.hmpps.visitscheduler.integration.task

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.PrisonEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionCategoryGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.SessionIncentiveLevelGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import uk.gov.justice.digital.hmpps.visitscheduler.task.VisitTask
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Transactional(propagation = SUPPORTS)
@DisplayName("Flag Visits")
class FlagVisitsTaskTest : IntegrationTestBase() {
  @Autowired
  private lateinit var visitTask: VisitTask

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  private val prisonerAId = "Prisoner-A"
  private val prisonerBId = "Prisoner-B"
  private val prisonerCId = "Prisoner-C"

  private val visitDate = LocalDate.now().plusDays(7)
  private val startTime = LocalTime.of(9, 0)
  private val endTime = LocalTime.of(10, 0)

  @BeforeEach
  internal fun setUp() {
    prison = PrisonEntityHelper.createPrison()
  }

  @Test
  fun `when prisoners have non associations after visit was created then visits are flagged`() {
    // Given
    val sessionTemplateReference = createSessionTemplate(startTime = startTime, endTime = endTime, dayOfWeek = visitDate.dayOfWeek)

    // prisoner A has non association with prisoner B who has a visit on the same day
    val prisonerAVisit = createVisit(prisonerId = prisonerAId, sessionTemplate = sessionTemplateReference)

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerAId,
      prisonerBId,
    )

    // prisoner B has a visit on the same day but offender list is empty for test purposes
    createVisit(prisonerId = prisonerBId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerBId)

    // prisoner C has no non associations
    createVisit(prisonerId = prisonerCId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerCId)

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerAId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerAId, "${prison.code}-C-1-C001")

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerBId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerBId, "${prison.code}-C-1-C001")

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerCId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerCId, "${prison.code}-C-1-C001")

    // When
    visitTask.flagVisits()
    verify(telemetryClient, times(1)).trackEvent(eq("flagged-visit-event"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("flagged-visit-event"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(prisonerAVisit.reference)
        Assertions.assertThat(it["prisonerId"]).isEqualTo(prisonerAVisit.prisonerId)
        Assertions.assertThat(it["prisonId"]).isEqualTo(prisonerAVisit.prison.code)
        Assertions.assertThat(it["visitType"]).isEqualTo(prisonerAVisit.visitType.name)
        Assertions.assertThat(it["visitRestriction"]).isEqualTo(prisonerAVisit.visitRestriction.name)
        Assertions.assertThat(it["visitStart"]).isEqualTo(getVisitStartStr(prisonerAVisit))
        Assertions.assertThat(it["visitEnd"]).isEqualTo(getVisitEndStr(prisonerAVisit))
        Assertions.assertThat(it["visitStatus"]).isEqualTo(prisonerAVisit.visitStatus.name)
        Assertions.assertThat(it["hasException"]).isNull()
        Assertions.assertThat(it["hasPrisonerMoved"]).isNull()
        Assertions.assertThat(it["additionalInformation"]).isNull()
      },
      isNull(),
    )
  }

  private fun getVisitEndStr(prisonerAVisit: Visit): String? =
    prisonerAVisit.sessionSlot.slotDate.atTime(prisonerAVisit.sessionSlot.slotEndTime)
      .format(DateTimeFormatter.ISO_DATE_TIME)

  private fun getVisitStartStr(prisonerAVisit: Visit): String? =
    prisonerAVisit.sessionSlot.slotDate.atTime(prisonerAVisit.sessionSlot.slotTime)
      .format(DateTimeFormatter.ISO_DATE_TIME)

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

    val prisonerAVisit = createVisit(prisonerId = prisonerAId, sessionTemplate = sessionTemplateReference)
    // prisoner A has moved location since the visit was created
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerAId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerAId, "${prison.code}-B")
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerAId, prison.code)

    // prisoner B visit will not be flagged
    createVisit(prisonerId = prisonerBId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerBId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerBId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerBId, "${prison.code}-A-0-001")

    // prisoner C visit will not be flagged
    createVisit(prisonerId = prisonerCId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerCId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerCId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerCId, "${prison.code}-A-2")

    // When
    visitTask.flagVisits()
    verify(telemetryClient, times(1)).trackEvent(eq("flagged-visit-event"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("flagged-visit-event"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(prisonerAVisit.reference)
        Assertions.assertThat(it["prisonerId"]).isEqualTo(prisonerAVisit.prisonerId)
        Assertions.assertThat(it["prisonId"]).isEqualTo(prisonerAVisit.prison.code)
        Assertions.assertThat(it["visitType"]).isEqualTo(prisonerAVisit.visitType.name)
        Assertions.assertThat(it["visitRestriction"]).isEqualTo(prisonerAVisit.visitRestriction.name)
        Assertions.assertThat(it["visitStart"]).isEqualTo(getVisitStartStr(prisonerAVisit))
        Assertions.assertThat(it["visitEnd"]).isEqualTo(getVisitEndStr(prisonerAVisit))
        Assertions.assertThat(it["visitStatus"]).isEqualTo(prisonerAVisit.visitStatus.name)
        Assertions.assertThat(it["hasException"]).isNull()
        Assertions.assertThat(it["hasPrisonerMoved"]).isNull()
        Assertions.assertThat(it["additionalInformation"]).isNull()
      },
      isNull(),
    )
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

    val prisonerAVisit = createVisit(prisonerId = prisonerAId, sessionTemplate = sessionTemplate)
    // prisoner A has changed category to B since the visit was created
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerAId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerAId, prison.code, category = PrisonerCategoryType.B.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerAId, "${prison.code}-B")

    // prisoner B visit will not be flagged
    createVisit(prisonerId = prisonerBId, sessionTemplate = sessionTemplate)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerBId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerBId, prison.code, category = PrisonerCategoryType.A_STANDARD.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerBId, "${prison.code}-A")

    // prisoner C visit will not be flagged
    createVisit(prisonerId = prisonerCId, sessionTemplate = sessionTemplate)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerCId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerCId, prison.code, category = PrisonerCategoryType.A_STANDARD.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerCId, "${prison.code}-C")

    // When
    visitTask.flagVisits()
    verify(telemetryClient, times(1)).trackEvent(eq("flagged-visit-event"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("flagged-visit-event"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(prisonerAVisit.reference)
        Assertions.assertThat(it["prisonerId"]).isEqualTo(prisonerAVisit.prisonerId)
        Assertions.assertThat(it["prisonId"]).isEqualTo(prisonerAVisit.prison.code)
        Assertions.assertThat(it["visitType"]).isEqualTo(prisonerAVisit.visitType.name)
        Assertions.assertThat(it["visitRestriction"]).isEqualTo(prisonerAVisit.visitRestriction.name)
        Assertions.assertThat(it["visitStart"]).isEqualTo(getVisitStartStr(prisonerAVisit))
        Assertions.assertThat(it["visitEnd"]).isEqualTo(getVisitEndStr(prisonerAVisit))
        Assertions.assertThat(it["visitStatus"]).isEqualTo(prisonerAVisit.visitStatus.name)
        Assertions.assertThat(it["hasException"]).isNull()
        Assertions.assertThat(it["hasPrisonerMoved"]).isNull()
        Assertions.assertThat(it["additionalInformation"]).isNull()
      },
      isNull(),
    )
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

    val prisonerAVisit = createVisit(prisonerId = prisonerAId, sessionTemplate = sessionTemplateReference)
    // prisoner A has changed category to B since the visit was created
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerAId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerAId, prison.code, incentiveLevelCode = IncentiveLevel.STANDARD)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerAId, "${prison.code}-B")

    // prisoner B visit will not be flagged as incentiveLevel is ENHANCED
    createVisit(prisonerId = prisonerBId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerBId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerBId, prison.code, incentiveLevelCode = IncentiveLevel.ENHANCED)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerBId, "${prison.code}-A")

    // prisoner C visit will not be flagged as incentiveLevel is ENHANCED
    createVisit(prisonerId = prisonerCId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerCId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerCId, prison.code, incentiveLevelCode = IncentiveLevel.ENHANCED)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerCId, "${prison.code}-C")

    // When
    visitTask.flagVisits()

    verify(telemetryClient, times(1)).trackEvent(eq("flagged-visit-event"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("flagged-visit-event"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(prisonerAVisit.reference)
        Assertions.assertThat(it["prisonerId"]).isEqualTo(prisonerAVisit.prisonerId)
        Assertions.assertThat(it["prisonId"]).isEqualTo(prisonerAVisit.prison.code)
        Assertions.assertThat(it["visitType"]).isEqualTo(prisonerAVisit.visitType.name)
        Assertions.assertThat(it["visitRestriction"]).isEqualTo(prisonerAVisit.visitRestriction.name)
        Assertions.assertThat(it["visitStart"]).isEqualTo(getVisitStartStr(prisonerAVisit))
        Assertions.assertThat(it["visitEnd"]).isEqualTo(getVisitEndStr(prisonerAVisit))
        Assertions.assertThat(it["visitStatus"]).isEqualTo(prisonerAVisit.visitStatus.name)
        Assertions.assertThat(it["hasException"]).isNull()
        Assertions.assertThat(it["hasPrisonerMoved"]).isNull()
        Assertions.assertThat(it["additionalInformation"]).isNull()
      },
      isNull(),
    )
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

    val prisonerAVisit = createVisit(prisonerId = prisonerAId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerAId)
    // prisoner is now in prison XYZ
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerAId, newPrisonCode)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerAId, "${prison.code}-B")

    // prisoner B visit will not be flagged as in the same prison
    createVisit(prisonerId = prisonerBId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerBId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerBId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerBId, "${prison.code}-A")

    // prisoner C visit will not be flagged as in the same prison
    createVisit(prisonerId = prisonerCId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerCId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerCId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerCId, "${prison.code}-C")

    // When
    visitTask.flagVisits()
    verify(telemetryClient, times(1)).trackEvent(eq("flagged-visit-event"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("flagged-visit-event"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(prisonerAVisit.reference)
        Assertions.assertThat(it["prisonerId"]).isEqualTo(prisonerAVisit.prisonerId)
        Assertions.assertThat(it["prisonId"]).isEqualTo(prisonerAVisit.prison.code)
        Assertions.assertThat(it["visitType"]).isEqualTo(prisonerAVisit.visitType.name)
        Assertions.assertThat(it["visitRestriction"]).isEqualTo(prisonerAVisit.visitRestriction.name)
        Assertions.assertThat(it["visitStart"]).isEqualTo(getVisitStartStr(prisonerAVisit))
        Assertions.assertThat(it["visitEnd"]).isEqualTo(getVisitEndStr(prisonerAVisit))
        Assertions.assertThat(it["visitStatus"]).isEqualTo(prisonerAVisit.visitStatus.name)
        Assertions.assertThat(it["hasException"]).isEqualTo("true")
        Assertions.assertThat(it["hasPrisonerMoved"]).isEqualTo("true")
        Assertions.assertThat(it["additionalInformation"]).isEqualTo("Prisoner with ID - $prisonerAId is not in prison - ${prison.code} but $newPrisonCode")
      },
      isNull(),
    )
  }

  @Test
  fun `when prisoners has some other exception then visits are flagged with additionalInformation`() {
    // Given
    val sessionTemplateReference = createSessionTemplate(
      startTime = startTime,
      endTime = endTime,
      dayOfWeek = visitDate.dayOfWeek,
    )

    val prisonerAVisit = createVisit(prisonerId = prisonerAId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(prisonerAId)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerAId, null)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerAId, "${prison.code}-B")

    createVisit(prisonerId = prisonerBId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerBId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerBId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerBId, "${prison.code}-A")

    createVisit(prisonerId = prisonerCId, sessionTemplate = sessionTemplateReference)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerCId)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerCId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerCId, "${prison.code}-C")

    // When
    visitTask.flagVisits()
    verify(telemetryClient, times(1)).trackEvent(eq("flagged-visit-event"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("flagged-visit-event"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(prisonerAVisit.reference)
        Assertions.assertThat(it["prisonerId"]).isEqualTo(prisonerAVisit.prisonerId)
        Assertions.assertThat(it["prisonId"]).isEqualTo(prisonerAVisit.prison.code)
        Assertions.assertThat(it["visitType"]).isEqualTo(prisonerAVisit.visitType.name)
        Assertions.assertThat(it["visitRestriction"]).isEqualTo(prisonerAVisit.visitRestriction.name)
        Assertions.assertThat(it["visitStart"]).isEqualTo(getVisitStartStr(prisonerAVisit))
        Assertions.assertThat(it["visitEnd"]).isEqualTo(getVisitEndStr(prisonerAVisit))
        Assertions.assertThat(it["visitStatus"]).isEqualTo(prisonerAVisit.visitStatus.name)
        Assertions.assertThat(it["hasException"]).isEqualTo("true")
        Assertions.assertThat(it["hasPrisonerMoved"]).isNull()
        Assertions.assertThat(it["additionalInformation"]).isNotNull()
      },
      isNull(),
    )
  }

  @Transactional
  private fun createVisit(
    visitStatus: VisitStatus = BOOKED,
    prisonerId: String,
    prisonCode: String = "MDI",
    visitRoom: String = "A1",
    visitStart: LocalTime = startTime,
    visitEnd: LocalTime = endTime,
    visitType: VisitType = VisitType.SOCIAL,
    visitRestriction: VisitRestriction = VisitRestriction.OPEN,
    sessionTemplate: SessionTemplate,
  ): Visit {
    var visit = visitEntityHelper.create(
      visitStatus = visitStatus,
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      visitRoom = visitRoom,
      visitStart = visitStart,
      visitEnd = visitEnd,
      visitType = visitType,
      visitRestriction = visitRestriction,
      slotDate = visitDate,
      sessionTemplate = sessionTemplate,
      createApplication = true,
    )

    visitEntityHelper.createContact(visit, name = "Bob", phone = "012345678")

    visit = visitEntityHelper.save(visit)

    eventAuditEntityHelper.create(visit)

    return visit
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
      startTime = startTime,
      endTime = endTime,
      dayOfWeek = dayOfWeek,
      permittedLocationGroups = permittedLocationGroups,
      permittedCategories = permittedCategoryGroups,
      permittedIncentiveLevels = permittedIncentiveLevelGroups,
    )
  }
}
