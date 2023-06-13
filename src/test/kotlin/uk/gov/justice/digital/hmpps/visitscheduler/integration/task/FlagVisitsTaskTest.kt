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
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Transactional(propagation = SUPPORTS)
@DisplayName("Flag Visits")
class FlagVisitsTaskTest : IntegrationTestBase() {
  @Autowired
  private lateinit var visitTask: VisitTask

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  private val prison: Prison = Prison(code = "MDI", active = true)

  private val prisonerAId = "Prisoner-A"
  private val prisonerBId = "Prisoner-B"
  private val prisonerCId = "Prisoner-C"

  private val visitDate = LocalDate.now().plusDays(7)
  private val startTime = LocalTime.of(9, 0)
  private val endTime = LocalTime.of(10, 0)

  @BeforeEach
  internal fun setUp() {
  }

  @Test
  fun `when prisoners have non associations after visit was created then visits are flagged`() {
    // Given
    val sessionTemplateReference = createSessionTemplate(startTime = startTime, endTime = endTime, dayOfWeek = visitDate.dayOfWeek)

    // prisoner A has non association with prisoner B who has a visit on the same day
    val prisonerAVisit = createVisit(prisonerId = prisonerAId, reference = "aa-bb-cc-dd", sessionTemplateReference = sessionTemplateReference.reference)

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerAId,
      prisonerBId,
      LocalDate.now(),
      LocalDate.now().plusMonths(6),
    )

    // prisoner B has a visit on the same day but offender list is empty for test purposes
    createVisit(prisonerId = prisonerBId, reference = "ee-ff-gg-hh", sessionTemplateReference = sessionTemplateReference.reference)
    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerBId)

    // prisoner C has no non associations
    createVisit(prisonerId = prisonerCId, reference = "ii-jj-kk-ll", sessionTemplateReference = sessionTemplateReference.reference)
    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerCId)

    prisonApiMockServer.stubGetPrisonerDetails(prisonerAId, prison.code)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerAId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerAId, "${prison.code}-C-1-C001")

    prisonApiMockServer.stubGetPrisonerDetails(prisonerBId, prison.code)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerBId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerBId, "${prison.code}-C-1-C001")

    prisonApiMockServer.stubGetPrisonerDetails(prisonerCId, prison.code)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerCId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerCId, "${prison.code}-C-1-C001")

    // When
    visitTask.flagVisits()
    verify(telemetryClient, times(1)).trackEvent(eq("flag-visit"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("flag-visit"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(prisonerAVisit.reference)
        Assertions.assertThat(it["prisonerId"]).isEqualTo(prisonerAVisit.prisonerId)
        Assertions.assertThat(it["prisonId"]).isEqualTo(prisonerAVisit.prison.code)
        Assertions.assertThat(it["visitType"]).isEqualTo(prisonerAVisit.visitType.name)
        Assertions.assertThat(it["visitRestriction"]).isEqualTo(prisonerAVisit.visitRestriction.name)
        Assertions.assertThat(it["visitStart"]).isEqualTo(prisonerAVisit.visitStart.format(DateTimeFormatter.ISO_DATE_TIME))
        Assertions.assertThat(it["visitEnd"]).isEqualTo(prisonerAVisit.visitEnd.format(DateTimeFormatter.ISO_DATE_TIME))
        Assertions.assertThat(it["visitStatus"]).isEqualTo(prisonerAVisit.visitStatus.name)
        Assertions.assertThat(it["createdBy"]).isEqualTo(prisonerAVisit.createdBy)
        Assertions.assertThat(it["hasException"]).isNull()
        Assertions.assertThat(it["hasPrisonerMoved"]).isNull()
        Assertions.assertThat(it["additionalInformation"]).isNull()
      },
      isNull(),
    )
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

    val prisonerAVisit = createVisit(prisonerId = prisonerAId, reference = "aa-bb-cc-dd", sessionTemplateReference = sessionTemplateReference.reference)
    // prisoner A has moved location since the visit was created
    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerAId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerAId, "${prison.code}-B")
    prisonApiMockServer.stubGetPrisonerDetails(prisonerAId, prison.code)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerAId, prison.code)

    // prisoner B visit will not be flagged
    createVisit(prisonerId = prisonerBId, reference = "ee-ff-gg-hh", sessionTemplateReference = sessionTemplateReference.reference)
    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerBId)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerBId, prison.code)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerBId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerBId, "${prison.code}-A-0-001")

    // prisoner C visit will not be flagged
    createVisit(prisonerId = prisonerCId, reference = "ii-jj-kk-ll", sessionTemplateReference = sessionTemplateReference.reference)
    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerCId)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerCId, prison.code)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerCId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerCId, "${prison.code}-A-2")

    // When
    visitTask.flagVisits()
    verify(telemetryClient, times(1)).trackEvent(eq("flag-visit"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("flag-visit"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(prisonerAVisit.reference)
        Assertions.assertThat(it["prisonerId"]).isEqualTo(prisonerAVisit.prisonerId)
        Assertions.assertThat(it["prisonId"]).isEqualTo(prisonerAVisit.prison.code)
        Assertions.assertThat(it["visitType"]).isEqualTo(prisonerAVisit.visitType.name)
        Assertions.assertThat(it["visitRestriction"]).isEqualTo(prisonerAVisit.visitRestriction.name)
        Assertions.assertThat(it["visitStart"]).isEqualTo(prisonerAVisit.visitStart.format(DateTimeFormatter.ISO_DATE_TIME))
        Assertions.assertThat(it["visitEnd"]).isEqualTo(prisonerAVisit.visitEnd.format(DateTimeFormatter.ISO_DATE_TIME))
        Assertions.assertThat(it["visitStatus"]).isEqualTo(prisonerAVisit.visitStatus.name)
        Assertions.assertThat(it["createdBy"]).isEqualTo(prisonerAVisit.createdBy)
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
    val sessionTemplateReference = createSessionTemplate(
      startTime = startTime,
      endTime = endTime,
      dayOfWeek = visitDate.dayOfWeek,
      permittedCategories = listOf(PrisonerCategoryType.A_STANDARD),
    )

    val prisonerAVisit = createVisit(prisonerId = prisonerAId, reference = "aa-bb-cc-dd", sessionTemplateReference = sessionTemplateReference.reference)
    // prisoner A has changed category to B since the visit was created
    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerAId)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerAId, prison.code)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerAId, prison.code, category = PrisonerCategoryType.B.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerAId, "${prison.code}-B")

    // prisoner B visit will not be flagged
    createVisit(prisonerId = prisonerBId, reference = "ee-ff-gg-hh", sessionTemplateReference = sessionTemplateReference.reference)
    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerBId)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerBId, prison.code)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerBId, prison.code, category = PrisonerCategoryType.A_STANDARD.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerBId, "${prison.code}-A")

    // prisoner C visit will not be flagged
    createVisit(prisonerId = prisonerCId, reference = "ii-jj-kk-ll", sessionTemplateReference = sessionTemplateReference.reference)
    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerCId)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerCId, prison.code)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerCId, prison.code, category = PrisonerCategoryType.A_STANDARD.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerCId, "${prison.code}-C")

    // When
    visitTask.flagVisits()
    verify(telemetryClient, times(1)).trackEvent(eq("flag-visit"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("flag-visit"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(prisonerAVisit.reference)
        Assertions.assertThat(it["prisonerId"]).isEqualTo(prisonerAVisit.prisonerId)
        Assertions.assertThat(it["prisonId"]).isEqualTo(prisonerAVisit.prison.code)
        Assertions.assertThat(it["visitType"]).isEqualTo(prisonerAVisit.visitType.name)
        Assertions.assertThat(it["visitRestriction"]).isEqualTo(prisonerAVisit.visitRestriction.name)
        Assertions.assertThat(it["visitStart"]).isEqualTo(prisonerAVisit.visitStart.format(DateTimeFormatter.ISO_DATE_TIME))
        Assertions.assertThat(it["visitEnd"]).isEqualTo(prisonerAVisit.visitEnd.format(DateTimeFormatter.ISO_DATE_TIME))
        Assertions.assertThat(it["visitStatus"]).isEqualTo(prisonerAVisit.visitStatus.name)
        Assertions.assertThat(it["createdBy"]).isEqualTo(prisonerAVisit.createdBy)
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

    val prisonerAVisit = createVisit(prisonerId = prisonerAId, reference = "aa-bb-cc-dd", sessionTemplateReference = sessionTemplateReference.reference)
    // prisoner A has changed category to B since the visit was created
    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerAId)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerAId, prison.code)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerAId, prison.code, incentiveLevelCode = IncentiveLevel.STANDARD)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerAId, "${prison.code}-B")

    // prisoner B visit will not be flagged as incentiveLevel is ENHANCED
    createVisit(prisonerId = prisonerBId, reference = "ee-ff-gg-hh", sessionTemplateReference = sessionTemplateReference.reference)
    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerBId)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerBId, prison.code)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerBId, prison.code, incentiveLevelCode = IncentiveLevel.ENHANCED)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerBId, "${prison.code}-A")

    // prisoner C visit will not be flagged as incentiveLevel is ENHANCED
    createVisit(prisonerId = prisonerCId, reference = "ii-jj-kk-ll", sessionTemplateReference = sessionTemplateReference.reference)
    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerCId)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerCId, prison.code)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerCId, prison.code, incentiveLevelCode = IncentiveLevel.ENHANCED)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerCId, "${prison.code}-C")

    // When
    visitTask.flagVisits()
    verify(telemetryClient, times(1)).trackEvent(eq("flag-visit"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("flag-visit"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(prisonerAVisit.reference)
        Assertions.assertThat(it["prisonerId"]).isEqualTo(prisonerAVisit.prisonerId)
        Assertions.assertThat(it["prisonId"]).isEqualTo(prisonerAVisit.prison.code)
        Assertions.assertThat(it["visitType"]).isEqualTo(prisonerAVisit.visitType.name)
        Assertions.assertThat(it["visitRestriction"]).isEqualTo(prisonerAVisit.visitRestriction.name)
        Assertions.assertThat(it["visitStart"]).isEqualTo(prisonerAVisit.visitStart.format(DateTimeFormatter.ISO_DATE_TIME))
        Assertions.assertThat(it["visitEnd"]).isEqualTo(prisonerAVisit.visitEnd.format(DateTimeFormatter.ISO_DATE_TIME))
        Assertions.assertThat(it["visitStatus"]).isEqualTo(prisonerAVisit.visitStatus.name)
        Assertions.assertThat(it["createdBy"]).isEqualTo(prisonerAVisit.createdBy)
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

    val prisonerAVisit = createVisit(prisonerId = prisonerAId, reference = "aa-bb-cc-dd", sessionTemplateReference = sessionTemplateReference.reference)
    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerAId)
    // prisoner is now in prison XYZ
    prisonApiMockServer.stubGetPrisonerDetails(prisonerAId, newPrisonCode)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerAId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerAId, "${prison.code}-B")

    // prisoner B visit will not be flagged as in the same prison
    createVisit(prisonerId = prisonerBId, reference = "ee-ff-gg-hh", sessionTemplateReference = sessionTemplateReference.reference)
    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerBId)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerBId, prison.code)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerBId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerBId, "${prison.code}-A")

    // prisoner C visit will not be flagged as in the same prison
    createVisit(prisonerId = prisonerCId, reference = "ii-jj-kk-ll", sessionTemplateReference = sessionTemplateReference.reference)
    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerCId)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerCId, prison.code)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerCId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerCId, "${prison.code}-C")

    // When
    visitTask.flagVisits()
    verify(telemetryClient, times(1)).trackEvent(eq("flag-visit"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("flag-visit"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(prisonerAVisit.reference)
        Assertions.assertThat(it["prisonerId"]).isEqualTo(prisonerAVisit.prisonerId)
        Assertions.assertThat(it["prisonId"]).isEqualTo(prisonerAVisit.prison.code)
        Assertions.assertThat(it["visitType"]).isEqualTo(prisonerAVisit.visitType.name)
        Assertions.assertThat(it["visitRestriction"]).isEqualTo(prisonerAVisit.visitRestriction.name)
        Assertions.assertThat(it["visitStart"]).isEqualTo(prisonerAVisit.visitStart.format(DateTimeFormatter.ISO_DATE_TIME))
        Assertions.assertThat(it["visitEnd"]).isEqualTo(prisonerAVisit.visitEnd.format(DateTimeFormatter.ISO_DATE_TIME))
        Assertions.assertThat(it["visitStatus"]).isEqualTo(prisonerAVisit.visitStatus.name)
        Assertions.assertThat(it["createdBy"]).isEqualTo(prisonerAVisit.createdBy)
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

    val prisonerAVisit = createVisit(prisonerId = prisonerAId, reference = "aa-bb-cc-dd", sessionTemplateReference = sessionTemplateReference.reference)
    prisonApiMockServer.stubGetOffenderNonAssociation(prisonerAId)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerAId, prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerAId, null)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerAId, "${prison.code}-B")

    createVisit(prisonerId = prisonerBId, reference = "ee-ff-gg-hh", sessionTemplateReference = sessionTemplateReference.reference)
    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerBId)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerBId, prison.code)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerBId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerBId, "${prison.code}-A")

    createVisit(prisonerId = prisonerCId, reference = "ii-jj-kk-ll", sessionTemplateReference = sessionTemplateReference.reference)
    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerCId)
    prisonApiMockServer.stubGetPrisonerDetails(prisonerCId, prison.code)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerCId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerCId, "${prison.code}-C")

    // When
    visitTask.flagVisits()
    verify(telemetryClient, times(1)).trackEvent(eq("flag-visit"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("flag-visit"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(prisonerAVisit.reference)
        Assertions.assertThat(it["prisonerId"]).isEqualTo(prisonerAVisit.prisonerId)
        Assertions.assertThat(it["prisonId"]).isEqualTo(prisonerAVisit.prison.code)
        Assertions.assertThat(it["visitType"]).isEqualTo(prisonerAVisit.visitType.name)
        Assertions.assertThat(it["visitRestriction"]).isEqualTo(prisonerAVisit.visitRestriction.name)
        Assertions.assertThat(it["visitStart"]).isEqualTo(prisonerAVisit.visitStart.format(DateTimeFormatter.ISO_DATE_TIME))
        Assertions.assertThat(it["visitEnd"]).isEqualTo(prisonerAVisit.visitEnd.format(DateTimeFormatter.ISO_DATE_TIME))
        Assertions.assertThat(it["visitStatus"]).isEqualTo(prisonerAVisit.visitStatus.name)
        Assertions.assertThat(it["createdBy"]).isEqualTo(prisonerAVisit.createdBy)
        Assertions.assertThat(it["hasException"]).isEqualTo("true")
        Assertions.assertThat(it["hasPrisonerMoved"]).isNull()
        Assertions.assertThat(it["additionalInformation"]).isNotNull()
      },
      isNull(),
    )
  }

  private fun createVisit(
    visitStatus: VisitStatus = BOOKED,
    prisonerId: String,
    prisonCode: String = "MDI",
    visitRoom: String = "A1",
    visitStart: LocalDateTime = LocalDateTime.of(visitDate, startTime),
    visitEnd: LocalDateTime = LocalDateTime.of(visitDate, endTime),
    visitType: VisitType = VisitType.SOCIAL,
    visitRestriction: VisitRestriction = VisitRestriction.OPEN,
    reference: String,
    sessionTemplateReference: String,
  ): Visit {
    return visitEntityHelper.create(
      visitStatus = visitStatus,
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      visitRoom = visitRoom,
      visitStart = visitStart,
      visitEnd = visitEnd,
      visitType = visitType,
      visitRestriction = visitRestriction,
      reference = reference,
      sessionTemplateReference = sessionTemplateReference,
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
      startTime = startTime,
      endTime = endTime,
      dayOfWeek = dayOfWeek,
      permittedLocationGroups = permittedLocationGroups,
      permittedCategories = permittedCategoryGroups,
      permittedIncentiveLevels = permittedIncentiveLevelGroups,
    )
  }
}
