package uk.gov.justice.digital.hmpps.visitscheduler.integration.task

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonerContactRegistryClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UserClientDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.PrisonEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.VisitNotificationEventHelper
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
class FlagAgeRestrictedVisitsTaskTest : IntegrationTestBase() {
  @Autowired
  private lateinit var flagVisitsTask: FlagVisitsTask

  @MockitoSpyBean
  private lateinit var telemetryClient: TelemetryClient

  @MockitoSpyBean
  private lateinit var sessionService: SessionService

  @MockitoSpyBean
  private lateinit var prisonerContactRegistryClient: PrisonerContactRegistryClient

  @Autowired
  private lateinit var visitNotificationEventHelper: VisitNotificationEventHelper

  private val prisonerAId = "Prisoner-A"
  private val prisonerBId = "Prisoner-B"
  private val prisonerCId = "Prisoner-C"
  private val prisonerDId = "Prisoner-D"
  private val prisonerEId = "Prisoner-E"

  private val startTime = LocalTime.of(9, 0)
  private val endTime = LocalTime.of(10, 0)

  @Captor
  lateinit var mapCapture: ArgumentCaptor<Map<String, String>>

  @BeforeEach
  internal fun setUp() {
    prison = PrisonEntityHelper.createPrison("FVT")
  }

  @Test
  fun `when a prison has an age restricted session any visits with visitors below the allowed age are flagged`() {
    // Given
    val visitDate = LocalDate.now().plusDays(1)
    // session1 has age restrictions
    val ageRestrictedSessionTemplate = createSessionTemplate(startTime = startTime, endTime = endTime, dayOfWeek = visitDate.dayOfWeek, isAgeRestricted = true, ageRestriction = 18)
    // 2nd session
    val nonAgeRestrictedSessionTemplate = createSessionTemplate(startTime = startTime.plusHours(2), endTime = endTime.plusHours(2), dayOfWeek = visitDate.dayOfWeek, isAgeRestricted = false, ageRestriction = 18)

    // visitor is over allowed age hence should not be flagged
    val visitor1 = createContactWithOptionalPrisonerRelationshipDto(personId = 1L, dateOfBirth = visitDate.minusYears(19), approvedVisitor = true)
    // visitor is below allowed age hence should be flagged
    val visitor2 = createContactWithOptionalPrisonerRelationshipDto(personId = 2L, dateOfBirth = visitDate.minusYears(12), approvedVisitor = true)
    val prisonerAVisitors = listOf(Pair(visitor1.contactId, false), Pair(visitor2.contactId, false))

    // visitor is over allowed age hence should not be flagged
    val visitor3 = createContactWithOptionalPrisonerRelationshipDto(personId = 3L, dateOfBirth = visitDate.minusYears(31), approvedVisitor = true)
    // visitor is over allowed age hence should not be flagged
    val visitor4 = createContactWithOptionalPrisonerRelationshipDto(personId = 4L, dateOfBirth = visitDate.minusYears(21), approvedVisitor = true)
    // visitor is exactly the allowed age hence should not be flagged
    val visitor5 = createContactWithOptionalPrisonerRelationshipDto(personId = 5L, dateOfBirth = visitDate.minusYears(18), approvedVisitor = true)
    val prisonerBVisitors = listOf(Pair(visitor3.contactId, false), Pair(visitor4.contactId, false), Pair(visitor5.contactId, false))

    // visitor does not have a DOB hence should be flagged
    val visitor6 = createContactWithOptionalPrisonerRelationshipDto(personId = 6L, dateOfBirth = null, approvedVisitor = true)
    // visitor is over allowed age hence should not be flagged
    val visitor7 = createContactWithOptionalPrisonerRelationshipDto(personId = 7L, dateOfBirth = visitDate.minusYears(21), approvedVisitor = true)
    // visitor is over allowed age hence should not be flagged
    val visitor8 = createContactWithOptionalPrisonerRelationshipDto(personId = 8L, dateOfBirth = visitDate.minusYears(45), approvedVisitor = true)
    val prisonerCVisitors = listOf(Pair(visitor6.contactId, false), Pair(visitor7.contactId, false), Pair(visitor8.contactId, false))

    // visitor is below allowed age hence should be flagged
    val visitor9 = createContactWithOptionalPrisonerRelationshipDto(personId = 9L, dateOfBirth = visitDate.minusYears(18).plusDays(1), approvedVisitor = true)
    val prisonerDVisitors = listOf(Pair(visitor9.contactId, false))

    // visitor is over allowed age hence should not be flagged
    val visitor10 = createContactWithOptionalPrisonerRelationshipDto(personId = 10L, dateOfBirth = visitDate.minusYears(18).minusDays(1), approvedVisitor = true)
    val prisonerEVisitors = listOf(Pair(visitor10.contactId, false))

    val prisonerAVisit1 = createApplicationAndVisit(prisonerId = prisonerAId, sessionTemplate = ageRestrictedSessionTemplate, slotDate = visitDate, visitorIds = prisonerAVisitors)
    val prisonerAVisit2 = createApplicationAndVisit(prisonerId = prisonerAId, sessionTemplate = nonAgeRestrictedSessionTemplate, slotDate = visitDate, visitorIds = prisonerAVisitors)

    val prisonerBVisit1 = createApplicationAndVisit(prisonerId = prisonerBId, sessionTemplate = ageRestrictedSessionTemplate, slotDate = visitDate, visitorIds = prisonerBVisitors)
    val prisonerBVisit2 = createApplicationAndVisit(prisonerId = prisonerBId, sessionTemplate = nonAgeRestrictedSessionTemplate, slotDate = visitDate, visitorIds = prisonerBVisitors)

    val prisonerCVisit1 = createApplicationAndVisit(prisonerId = prisonerCId, sessionTemplate = ageRestrictedSessionTemplate, slotDate = visitDate, visitorIds = prisonerCVisitors)
    val prisonerCVisit2 = createApplicationAndVisit(prisonerId = prisonerCId, sessionTemplate = nonAgeRestrictedSessionTemplate, slotDate = visitDate, visitorIds = prisonerCVisitors)

    val prisonerDVisit1 = createApplicationAndVisit(prisonerId = prisonerDId, sessionTemplate = ageRestrictedSessionTemplate, slotDate = visitDate, visitorIds = prisonerDVisitors)
    val prisonerDVisit2 = createApplicationAndVisit(prisonerId = prisonerDId, sessionTemplate = nonAgeRestrictedSessionTemplate, slotDate = visitDate, visitorIds = prisonerDVisitors)

    val prisonerEVisit1 = createApplicationAndVisit(prisonerId = prisonerEId, sessionTemplate = ageRestrictedSessionTemplate, slotDate = visitDate, visitorIds = prisonerEVisitors)
    val prisonerEVisit2 = createApplicationAndVisit(prisonerId = prisonerEId, sessionTemplate = nonAgeRestrictedSessionTemplate, slotDate = visitDate, visitorIds = prisonerEVisitors)

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerAId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerAId, "${prison.code}-C-1-C001")

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerBId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerBId, "${prison.code}-C-1-C001")

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerCId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerCId, "${prison.code}-C-1-C001")

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerDId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerDId, "${prison.code}-C-1-C001")

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerEId, prison.code)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerEId, "${prison.code}-C-1-C001")

    prisonerContactRegistryMockServer.stubSearchContacts(contactIds = prisonerAVisitors.map { it.first }, withRestrictions = false, contactsList = listOf(visitor1, visitor2))
    prisonerContactRegistryMockServer.stubSearchContacts(contactIds = prisonerBVisitors.map { it.first }, withRestrictions = false, contactsList = listOf(visitor3, visitor4, visitor5))
    prisonerContactRegistryMockServer.stubSearchContacts(contactIds = prisonerCVisitors.map { it.first }, withRestrictions = false, contactsList = listOf(visitor6, visitor7, visitor8))
    prisonerContactRegistryMockServer.stubSearchContacts(contactIds = prisonerDVisitors.map { it.first }, withRestrictions = false, contactsList = listOf(visitor9))
    prisonerContactRegistryMockServer.stubSearchContacts(contactIds = prisonerEVisitors.map { it.first }, withRestrictions = false, contactsList = listOf(visitor10))

    // When
    flagVisitsTask.flagVisits()

    // Then
    verify(telemetryClient, times(3)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    assertFlaggedVisitEvent(prisonerAVisit1, "Age restricted visit - visitors below allowed age")
    assertFlaggedVisitEvent(prisonerCVisit1, "Age restricted visit - visitors without a DOB")
    assertFlaggedVisitEvent(prisonerDVisit1, "Age restricted visit - visitors below allowed age")
  }

  private fun assertFlaggedVisitEvent(visit: Visit, additionalInformation: String) {
    val base = getVisitAssert(visit, additionalInformation)
    assertFlaggedVisitEvent(base)
  }

  private fun getVisitAssert(visit: Visit, additionalInformation: String): Consumer<Map<String, String>> = Consumer<Map<String, String>> {
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

  private fun assertFlaggedVisitEvent(test: Consumer<Map<String, String>>) {
    verify(telemetryClient).trackEvent(
      eq("flagged-visit-event"),
      check { test.accept(it) },
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
    userTypes: List<UserType> = listOf(UserType.STAFF, UserType.PUBLIC),
    isAgeRestricted: Boolean,
    ageRestriction: Int,
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
      clients = userTypes.map { UserClientDto(it, true) },
      isAgeRestricted = isAgeRestricted,
      ageRestriction = ageRestriction,
    )
  }
}
