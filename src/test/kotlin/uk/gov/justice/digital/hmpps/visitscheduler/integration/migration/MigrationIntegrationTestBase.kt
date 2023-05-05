package uk.gov.justice.digital.hmpps.visitscheduler.integration.migration

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateLegacyContactOnVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateLegacyDataRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitNoteDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.integration.mock.HmppsAuthExtension
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus.COMPLETED_NORMALLY
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.STATUS_CHANGED_REASON
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionCategoryGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import uk.gov.justice.digital.hmpps.visitscheduler.repository.LegacyDataRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(HmppsAuthExtension::class)
abstract class MigrationIntegrationTestBase : IntegrationTestBase() {

  companion object {
    @JvmStatic
    protected val VISIT_TIME: LocalDateTime = LocalDateTime.of(LocalDate.now().year + 1, 11, 1, 12, 30, 44)

    @JvmStatic
    protected val TEST_END_POINT = "/migrate-visits"

    @JvmStatic
    protected val PRISON_CODE = "MDI"

    @JvmStatic
    protected val CANCELLED_BY_BY_USER = "user-2"
  }

  protected lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  protected lateinit var visitRepository: VisitRepository

  @Autowired
  protected lateinit var legacyDataRepository: LegacyDataRepository

  @SpyBean
  protected lateinit var telemetryClient: TelemetryClient

  protected fun createSessionTemplateFrom(
    migrateVisitRequestDto: MigrateVisitRequestDto,
    visitRoom: String = migrateVisitRequestDto.visitRoom,
    startTime: LocalTime? = null,
    endTime: LocalTime? = null,
  ): SessionTemplate {
    return sessionTemplateEntityHelper.create(
      validFromDate = migrateVisitRequestDto.startTimestamp.toLocalDate().minusDays(1),
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = migrateVisitRequestDto.startTimestamp.dayOfWeek,
      visitRoom = visitRoom,
      startTime = startTime?.let { startTime } ?: migrateVisitRequestDto.startTimestamp.toLocalTime(),
      endTime = endTime?.let { endTime } ?: migrateVisitRequestDto.endTimestamp.toLocalTime(),
    )
  }

  protected fun createMigrateVisitRequestDto(
    actionedBy: String? = "Aled Evans",
    visitStartTimeAndDate: LocalDateTime = VISIT_TIME,
    visitRoom: String = "A1",
    outcomeStatus: OutcomeStatus? = COMPLETED_NORMALLY,
    contactName: String? = "John Smith",
    prisonCode: String = PRISON_CODE,
    prisonerId: String = "FF0000FF",
    housingLocations: String? = null,
    category: String? = null,
    incentiveLevelCode: String? = null,
  ): MigrateVisitRequestDto {
    val migrateVisitRequestDto = MigrateVisitRequestDto(
      prisonCode = PRISON_CODE,
      prisonerId = prisonerId,
      visitRoom = visitRoom,
      visitType = SOCIAL,
      startTimestamp = visitStartTimeAndDate,
      endTimestamp = visitStartTimeAndDate.plusHours(1),
      visitStatus = BOOKED,
      outcomeStatus = outcomeStatus,
      visitRestriction = OPEN,
      visitContact = CreateLegacyContactOnVisitRequestDto(contactName!!, "013448811538"),
      visitors = setOf(VisitorDto(123, true)),
      visitNotes = setOf(
        VisitNoteDto(type = VISITOR_CONCERN, "A visit concern"),
        VisitNoteDto(type = VISIT_OUTCOMES, "A visit outcome"),
        VisitNoteDto(type = VISIT_COMMENT, "A visit comment"),
        VisitNoteDto(type = STATUS_CHANGED_REASON, "Status has changed"),
      ),
      legacyData = CreateLegacyDataRequestDto(123),
      createDateTime = LocalDateTime.of(2022, 9, 11, 12, 30),
      modifyDateTime = LocalDateTime.of(2022, 10, 1, 12, 30),
      actionedBy = actionedBy,
    )

    mockApiCalls(prisonerId, prisonCode, housingLocations, category, incentiveLevelCode = incentiveLevelCode)

    return migrateVisitRequestDto
  }

  protected fun mockApiCalls(
    prisonerId: String,
    prisonCode: String,
    housingLocations: String? = null,
    category: String? = null,
    incentiveLevelCode: String? = null,
  ) {
    prisonOffenderSearchMockServer.stubGetPrisonerByString(
      prisonerId,
      prisonCode,
      incentiveLevelCode,
      category = category,
    )
    prisonApiMockServer.stubGetOffenderNonAssociation(prisonerId)
    prisonApiMockServer.stubGetPrisonerDetails(
      prisonerId,
      prisonerDetailsDto = PrisonerDetailsDto(nomsId = prisonerId, establishmentCode = prisonCode, bookingId = 1),
    )
    housingLocations?.let {
      prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, housingLocations)
    }
  }

  protected fun getReference(responseSpec: ResponseSpec): String {
    var reference = ""
    responseSpec.expectBody()
      .jsonPath("$")
      .value<String> { json -> reference = json }
    return reference
  }

  protected fun callMigrateVisit(jsonString: String): ResponseSpec {
    return webTestClient.post().uri(TEST_END_POINT)
      .headers(roleVisitSchedulerHttpHeaders)
      .contentType(MediaType.APPLICATION_JSON)
      .body(
        BodyInserters.fromValue(
          jsonString,
        ),
      )
      .exchange()
  }

  protected fun callMigrateVisit(
    authHttpHeaders: (HttpHeaders) -> Unit,
    migrateVisitRequestDto: MigrateVisitRequestDto,
  ): ResponseSpec {
    return webTestClient.post().uri(TEST_END_POINT)
      .headers(authHttpHeaders)
      .body(BodyInserters.fromValue(migrateVisitRequestDto))
      .exchange()
  }

  protected fun assertTelemetryClientEvents(
    cancelledVisit: VisitDto,
    type: TelemetryVisitEvents,
  ) {
    verify(telemetryClient).trackEvent(
      eq(type.eventName),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(cancelledVisit.reference)
        Assertions.assertThat(it["prisonerId"]).isEqualTo(cancelledVisit.prisonerId)
        Assertions.assertThat(it["prisonId"]).isEqualTo(cancelledVisit.prisonCode)
        Assertions.assertThat(it["visitType"]).isEqualTo(cancelledVisit.visitType.name)
        Assertions.assertThat(it["visitRoom"]).isEqualTo(cancelledVisit.visitRoom)
        Assertions.assertThat(it["sessionTemplateReference"]).isEqualTo(cancelledVisit.sessionTemplateReference)
        Assertions.assertThat(it["visitRestriction"]).isEqualTo(cancelledVisit.visitRestriction.name)
        Assertions.assertThat(it["visitStart"]).isEqualTo(cancelledVisit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        Assertions.assertThat(it["visitStatus"]).isEqualTo(cancelledVisit.visitStatus.name)
        Assertions.assertThat(it["applicationReference"]).isEqualTo(cancelledVisit.applicationReference)
        Assertions.assertThat(it["outcomeStatus"]).isEqualTo(cancelledVisit.outcomeStatus!!.name)
      },
      isNull(),
    )

    val eventsMap = mutableMapOf(
      "reference" to cancelledVisit.reference,
      "applicationReference" to cancelledVisit.applicationReference,
      "prisonerId" to cancelledVisit.prisonerId,
      "prisonId" to cancelledVisit.prisonCode,
      "visitType" to cancelledVisit.visitType.name,
      "visitRoom" to cancelledVisit.visitRoom,
      "sessionTemplateReference" to cancelledVisit.sessionTemplateReference,
      "visitRestriction" to cancelledVisit.visitRestriction.name,
      "visitStart" to cancelledVisit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME),
      "visitStatus" to cancelledVisit.visitStatus.name,
      "outcomeStatus" to cancelledVisit.outcomeStatus!!.name,
    )
    verify(telemetryClient, times(1)).trackEvent(type.eventName, eventsMap, null)
  }

  protected fun assertCancelledDomainEvent(
    cancelledVisit: VisitDto,
  ) {
    verify(telemetryClient).trackEvent(
      eq("prison-visit.cancelled-domain-event"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(cancelledVisit.reference)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("prison-visit.cancelled-domain-event"), any(), isNull())
  }

  protected fun assertVisitCancellation(
    cancelledVisit: VisitDto,
    expectedOutcomeStatus: OutcomeStatus,
    cancelledBy: String,
  ) {
    Assertions.assertThat(cancelledVisit.visitStatus).isEqualTo(VisitStatus.CANCELLED)
    Assertions.assertThat(cancelledVisit.outcomeStatus).isEqualTo(expectedOutcomeStatus)
    Assertions.assertThat(cancelledVisit.cancelledBy).isEqualTo(cancelledBy)
  }

  protected fun cratePermittedCategories(
    name: String? = "Group A",
    prisonCode: String = "MDI",
    vararg category: PrisonerCategoryType,
  ): MutableList<SessionCategoryGroup> {
    return mutableListOf(sessionPrisonerCategoryHelper.create(name = name, prisonCode = prisonCode, category.asList()))
  }

  protected fun createSessionLocationGroup(housingLocation: String): MutableList<SessionLocationGroup> {
    val delimiter = "-"
    val array = housingLocation.split(delimiter, ignoreCase = false, limit = 5)

    val allowedPermittedLocationsB1 = listOf(
      AllowedSessionLocationHierarchy(
        array[1],
        if (array.size == 3) array[2] else null,
        if (array.size == 4) array[3] else null,
        if (array.size == 5) array[4] else null,
      ),
    )
    return mutableListOf(sessionLocationGroupHelper.create(prisonCode = array[0], prisonHierarchies = allowedPermittedLocationsB1))
  }
}
