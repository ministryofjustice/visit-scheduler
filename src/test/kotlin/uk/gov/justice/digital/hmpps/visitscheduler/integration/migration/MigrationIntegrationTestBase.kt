package uk.gov.justice.digital.hmpps.visitscheduler.integration.migration

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateLegacyContactOnVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateLegacyDataRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitNoteDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus.COMPLETED_NORMALLY
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType.STATUS_CHANGED_REASON
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.integration.mock.HmppsAuthExtension
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionCategoryGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import uk.gov.justice.digital.hmpps.visitscheduler.repository.LegacyDataRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(HmppsAuthExtension::class)
abstract class MigrationIntegrationTestBase : IntegrationTestBase() {

  companion object {
    @JvmStatic
    protected val VISIT_TIME: LocalDateTime = LocalDateTime.now().plusDays(10).truncatedTo(ChronoUnit.SECONDS)

    @JvmStatic
    protected val TEST_END_POINT = "/migrate-visits"

    @JvmStatic
    protected val PRISON_CODE = "MGV"
  }

  protected lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  protected lateinit var visitRepository: VisitRepository

  @Autowired
  protected lateinit var legacyDataRepository: LegacyDataRepository

  @MockitoSpyBean
  protected lateinit var telemetryClient: TelemetryClient

  protected fun createSessionTemplateFrom(
    migrateVisitRequestDto: MigrateVisitRequestDto,
    visitRoom: String = migrateVisitRequestDto.visitRoom,
    startTime: LocalTime? = null,
    endTime: LocalTime? = null,
  ): SessionTemplate = sessionTemplateEntityHelper.create(
    validFromDate = migrateVisitRequestDto.startTimestamp.toLocalDate().minusDays(1),
    prisonCode = migrateVisitRequestDto.prisonCode,
    dayOfWeek = migrateVisitRequestDto.startTimestamp.dayOfWeek,
    visitRoom = visitRoom,
    startTime = startTime?.let { startTime } ?: migrateVisitRequestDto.startTimestamp.toLocalTime(),
    endTime = endTime?.let { endTime } ?: migrateVisitRequestDto.endTimestamp.toLocalTime(),
  )

  protected fun createMigrateVisitRequestDto(
    visitStatus: VisitStatus = BOOKED,
    visitSubStatus: VisitSubStatus = VisitSubStatus.AUTO_APPROVED,
    actionedBy: String? = "Aled Evans",
    visitStartTimeAndDate: LocalDateTime = VISIT_TIME,
    visitRoom: String = "A1",
    outcomeStatus: OutcomeStatus? = COMPLETED_NORMALLY,
    contactName: String? = "John Smith",
    prisonCode: String = PRISON_CODE,
    prisonerId: String = "FF0000FF",
    housingLocations: String? = "$prisonCode-A-1-001",
    category: String? = null,
    incentiveLevelCode: IncentiveLevel? = null,
    visitRestriction: VisitRestriction = OPEN,
    createDateTime: LocalDateTime = LocalDateTime.of(2022, 9, 11, 12, 30),
    modifyDateTime: LocalDateTime? = null,
    lastPermanentLevels: String? = null,
  ): MigrateVisitRequestDto {
    val migrateVisitRequestDto = MigrateVisitRequestDto(
      prisonCode = prisonCode,
      prisonerId = prisonerId,
      visitRoom = visitRoom,
      visitType = SOCIAL,
      startTimestamp = visitStartTimeAndDate,
      endTimestamp = visitStartTimeAndDate.plusHours(1),
      visitStatus = visitStatus,
      outcomeStatus = outcomeStatus,
      visitRestriction = visitRestriction,
      visitContact = CreateLegacyContactOnVisitRequestDto(contactName!!, "013448811538"),
      visitors = setOf(VisitorDto(123, true)),
      visitNotes = setOf(
        VisitNoteDto(type = VISITOR_CONCERN, "A visit concern"),
        VisitNoteDto(type = VISIT_OUTCOMES, "A visit outcome"),
        VisitNoteDto(type = VISIT_COMMENT, "A visit comment"),
        VisitNoteDto(type = STATUS_CHANGED_REASON, "Status has changed"),
      ),
      legacyData = CreateLegacyDataRequestDto(123),
      createDateTime = createDateTime,
      modifyDateTime = modifyDateTime,
      actionedBy = actionedBy,
    )

    mockApiCalls(prisonerId, prisonCode, housingLocations, category, incentiveLevelCode = incentiveLevelCode, lastPermanentLevels = lastPermanentLevels)

    return migrateVisitRequestDto
  }

  protected fun mockApiCalls(
    prisonerId: String,
    prisonCode: String,
    housingLocations: String? = null,
    category: String? = null,
    incentiveLevelCode: IncentiveLevel? = null,
    lastPermanentLevels: String? = null,
  ) {
    prisonOffenderSearchMockServer.stubGetPrisonerByString(
      prisonerId,
      prisonCode,
      incentiveLevelCode,
      category = category,
    )
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(prisonerId)
    housingLocations?.let {
      prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, housingLocations, lastPermanentLevels)
    }
  }

  protected fun getReference(responseSpec: ResponseSpec): String {
    var reference = ""
    responseSpec.expectBody()
      .jsonPath("$")
      .value<String> { json -> reference = json }
    return reference
  }

  protected fun callMigrateVisit(jsonString: String): ResponseSpec = webTestClient.post().uri(TEST_END_POINT)
    .headers(roleVisitSchedulerHttpHeaders)
    .contentType(MediaType.APPLICATION_JSON)
    .body(
      BodyInserters.fromValue(
        jsonString,
      ),
    )
    .exchange()

  protected fun callMigrateVisit(
    authHttpHeaders: (HttpHeaders) -> Unit,
    migrateVisitRequestDto: MigrateVisitRequestDto,
  ): ResponseSpec = webTestClient.post().uri(TEST_END_POINT)
    .headers(authHttpHeaders)
    .body(BodyInserters.fromValue(migrateVisitRequestDto))
    .exchange()

  protected fun assertTelemetryClientEvents(
    visit: VisitDto,
    type: TelemetryVisitEvents,
  ) {
    val visitStart = visit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME)

    verify(telemetryClient).trackEvent(
      eq(type.eventName),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
        Assertions.assertThat(it["prisonerId"]).isEqualTo(visit.prisonerId)
        Assertions.assertThat(it["prisonId"]).isEqualTo(visit.prisonCode)
        Assertions.assertThat(it["visitType"]).isEqualTo(visit.visitType.name)
        Assertions.assertThat(it["visitRoom"]).isEqualTo(visit.visitRoom)
        Assertions.assertThat(it["sessionTemplateReference"]).isEqualTo(visit.sessionTemplateReference)
        Assertions.assertThat(it["visitRestriction"]).isEqualTo(visit.visitRestriction.name)
        Assertions.assertThat(it["visitStart"]).isEqualTo(visitStart)
        Assertions.assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
        Assertions.assertThat(it["applicationReference"]).isEqualTo(visit.applicationReference)
        Assertions.assertThat(it["outcomeStatus"]).isEqualTo(visit.outcomeStatus!!.name)
      },
      isNull(),
    )

    val eventsMap = mutableMapOf(
      "reference" to visit.reference,
      "applicationReference" to visit.applicationReference,
      "prisonerId" to visit.prisonerId,
      "prisonId" to visit.prisonCode,
      "visitType" to visit.visitType.name,
      "visitRoom" to visit.visitRoom,
      "sessionTemplateReference" to visit.sessionTemplateReference,
      "visitRestriction" to visit.visitRestriction.name,
      "visitStart" to visitStart,
      "visitStatus" to visit.visitStatus.name,
      "outcomeStatus" to visit.outcomeStatus!!.name,
    )
    verify(telemetryClient, times(1)).trackEvent(type.eventName, eventsMap, null)
  }

  protected fun cratePermittedCategories(
    name: String? = "Group A",
    prisonCode: String = "MDI",
    vararg category: PrisonerCategoryType,
  ): MutableList<SessionCategoryGroup> = mutableListOf(sessionPrisonerCategoryHelper.create(name = name, prisonCode = prisonCode, category.asList()))

  protected fun createSessionLocationGroup(housingLocation: String): MutableList<SessionLocationGroup> {
    val delimiter = "-"
    val array = housingLocation.split(delimiter, ignoreCase = false, limit = 5)
    val prisonCode = array[0]

    val allowedPermittedLocationsB1 = listOf(
      AllowedSessionLocationHierarchy(
        array[1],
        if (array.size > 2) array[2] else null,
        if (array.size > 3) array[3] else null,
        if (array.size > 4) array[4] else null,
      ),
    )
    return mutableListOf(sessionLocationGroupHelper.create(prisonCode = prisonCode, prisonHierarchies = allowedPermittedLocationsB1))
  }
}
