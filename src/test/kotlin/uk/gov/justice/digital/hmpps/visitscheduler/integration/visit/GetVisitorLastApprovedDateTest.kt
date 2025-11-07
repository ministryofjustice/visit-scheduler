package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.FIND_LAST_APPROVED_DATE_FOR_VISITORS_BY_PRISONER
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_EVENTS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visit.VisitorLastApprovedDateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visit.VisitorLastApprovedDatesRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callPost
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestEventAuditRepository
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
@DisplayName("GET $VISIT_NOTIFICATION_EVENTS")
class GetVisitorLastApprovedDateTest : IntegrationTestBase() {
  @Autowired
  private lateinit var testEventAuditRepository: TestEventAuditRepository

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val prisonerId = "AA11BCC"
  val visitor1PersonId = 1L
  val visitor2PersonId = 2L
  val visitor3PersonId = 3L
  val visitor4PersonId = 4L

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when visit is auto approved then the date of auto-approved event needs to be returned`() {
    // Given
    val visitorIds = listOf(visitor1PersonId, visitor2PersonId, visitor3PersonId, visitor4PersonId)
    val visit1BookedEventDate = LocalDate.now().minusDays(4)

    val visit1 = visitEntityHelper.create(
      prisonerId = prisonerId,
      slotDate = LocalDate.now().plusDays(2),
      sessionTemplate = sessionTemplateDefault,
    )
    visitEntityHelper.createVisitor(visit1, visitor1PersonId, false)
    visitEntityHelper.createVisitor(visit1, visitor2PersonId, false)
    visitEntityHelper.createVisitor(visit1, visitor3PersonId, true)
    visitEntityHelper.save(visit1)
    val event = eventAuditEntityHelper.create(visit = visit1, type = EventAuditType.BOOKED_VISIT)
    // visit was booked 2 days back
    testEventAuditRepository.updateCreateTimeStamp(event.id, visit1BookedEventDate.atTime(9, 0))

    val responseSpec = callFindLastApprovedDate(webTestClient, prisonerId, visitorIds, roleVisitSchedulerHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val lastApprovedDates = getResults(returnResult)
    assertThat(lastApprovedDates.size).isEqualTo(4)
    assertThat(getLastApprovedDate(lastApprovedDates, visitor1PersonId)).isEqualTo(visit1BookedEventDate)
    assertThat(getLastApprovedDate(lastApprovedDates, visitor2PersonId)).isEqualTo(visit1BookedEventDate)
    assertThat(getLastApprovedDate(lastApprovedDates, visitor3PersonId)).isEqualTo(visit1BookedEventDate)
    assertThat(getLastApprovedDate(lastApprovedDates, visitor4PersonId)).isNull()
  }

  @Test
  fun `when visit is requested and approved then the date of auto-approved event needs to be returned`() {
    // Given
    val visitorIds = listOf(visitor1PersonId, visitor2PersonId, visitor3PersonId, visitor4PersonId)
    val visit1BookedDate = LocalDate.now().minusDays(2)
    val visit1ApprovedDate = LocalDate.now().minusDays(1)

    val visit1 = visitEntityHelper.create(
      prisonerId = prisonerId,
      slotDate = LocalDate.now().plusDays(2),
      sessionTemplate = sessionTemplateDefault,
    )
    visitEntityHelper.createVisitor(visit1, visitor1PersonId, false)
    visitEntityHelper.createVisitor(visit1, visitor2PersonId, false)
    visitEntityHelper.createVisitor(visit1, visitor3PersonId, true)
    visitEntityHelper.save(visit1)
    var event = eventAuditEntityHelper.create(visit = visit1, type = EventAuditType.REQUESTED_VISIT_APPROVED)
    testEventAuditRepository.updateCreateTimeStamp(event.id, visit1ApprovedDate.atTime(9, 0))
    event = eventAuditEntityHelper.create(visit = visit1, type = EventAuditType.BOOKED_VISIT)
    testEventAuditRepository.updateCreateTimeStamp(event.id, visit1BookedDate.atTime(9, 0))

    val responseSpec = callFindLastApprovedDate(webTestClient, prisonerId, visitorIds, roleVisitSchedulerHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val lastApprovedDates = getResults(returnResult)
    assertThat(lastApprovedDates.size).isEqualTo(4)
    assertThat(getLastApprovedDate(lastApprovedDates, visitor1PersonId)).isEqualTo(visit1ApprovedDate)
    assertThat(getLastApprovedDate(lastApprovedDates, visitor2PersonId)).isEqualTo(visit1ApprovedDate)
    assertThat(getLastApprovedDate(lastApprovedDates, visitor3PersonId)).isEqualTo(visit1ApprovedDate)
    assertThat(getLastApprovedDate(lastApprovedDates, visitor4PersonId)).isNull()
  }

  @Test
  fun `when visit is migrated then the date of migrated event needs to be returned`() {
    // Given
    val visitorIds = listOf(visitor1PersonId, visitor2PersonId, visitor3PersonId, visitor4PersonId)
    val visit1BookedDate = LocalDate.now().minusDays(2)
    val visit1MigratedDate = LocalDate.now().minusDays(1)

    val visit1 = visitEntityHelper.create(
      prisonerId = prisonerId,
      slotDate = LocalDate.now().plusDays(2),
      sessionTemplate = sessionTemplateDefault,
    )
    visitEntityHelper.createVisitor(visit1, visitor1PersonId, false)
    visitEntityHelper.createVisitor(visit1, visitor2PersonId, false)
    visitEntityHelper.createVisitor(visit1, visitor3PersonId, true)
    visitEntityHelper.save(visit1)
    var event = eventAuditEntityHelper.create(visit = visit1, type = EventAuditType.MIGRATED_VISIT)
    testEventAuditRepository.updateCreateTimeStamp(event.id, visit1MigratedDate.atTime(9, 0))
    event = eventAuditEntityHelper.create(visit = visit1, type = EventAuditType.BOOKED_VISIT)
    testEventAuditRepository.updateCreateTimeStamp(event.id, visit1BookedDate.atTime(9, 0))

    val responseSpec = callFindLastApprovedDate(webTestClient, prisonerId, visitorIds, roleVisitSchedulerHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val lastApprovedDates = getResults(returnResult)
    assertThat(lastApprovedDates.size).isEqualTo(4)
    assertThat(getLastApprovedDate(lastApprovedDates, visitor1PersonId)).isEqualTo(visit1MigratedDate)
    assertThat(getLastApprovedDate(lastApprovedDates, visitor2PersonId)).isEqualTo(visit1MigratedDate)
    assertThat(getLastApprovedDate(lastApprovedDates, visitor3PersonId)).isEqualTo(visit1MigratedDate)
    assertThat(getLastApprovedDate(lastApprovedDates, visitor4PersonId)).isNull()
  }

  @Test
  fun `when multiple visits then last approved date is calculated correctly`() {
    // Given
    // booked, approved and cancelled visit - visit date is calculated correctly
    val visitorIds = listOf(visitor1PersonId, visitor2PersonId, visitor3PersonId, visitor4PersonId)
    val visit1BookedEventDate = LocalDate.now().minusDays(7)
    val visit2ApprovedEventDate = LocalDate.now().minusDays(6)
    val visit3ApprovedBookedDate = LocalDate.now().minusDays(5)
    val visit4MigratedDate = LocalDate.now().minusDays(4)

    val visit1 = visitEntityHelper.create(
      prisonerId = prisonerId,
      slotDate = LocalDate.now().plusDays(2),
      sessionTemplate = sessionTemplateDefault,
    )
    visitEntityHelper.createVisitor(visit1, visitor1PersonId, false)
    visitEntityHelper.createVisitor(visit1, visitor2PersonId, false)
    visitEntityHelper.createVisitor(visit1, visitor3PersonId, true)
    visitEntityHelper.save(visit1)
    var event = eventAuditEntityHelper.create(visit = visit1, type = EventAuditType.BOOKED_VISIT)
    // visit was booked 2 days back
    testEventAuditRepository.updateCreateTimeStamp(event.id, visit1BookedEventDate.atTime(9, 0))

    val visit2 = visitEntityHelper.create(
      prisonerId = prisonerId,
      slotDate = LocalDate.now().plusDays(4),
      sessionTemplate = sessionTemplateDefault,
    )
    visitEntityHelper.createVisitor(visit2, visitor2PersonId, false)
    visitEntityHelper.save(visit2)
    event = eventAuditEntityHelper.create(visit = visit2, type = EventAuditType.REQUESTED_VISIT_APPROVED)
    // visit was approved 1 day back
    testEventAuditRepository.updateCreateTimeStamp(event.id, visit2ApprovedEventDate.atTime(9, 0))

    val visit3 = visitEntityHelper.create(
      prisonerId = prisonerId,
      slotDate = LocalDate.now().plusDays(7),
      sessionTemplate = sessionTemplateDefault,
      visitStatus = VisitStatus.CANCELLED,
      visitSubStatus = VisitSubStatus.CANCELLED,
    )
    visitEntityHelper.createVisitor(visit3, visitor1PersonId, false)
    visitEntityHelper.createVisitor(visit3, visitor2PersonId, false)
    visitEntityHelper.createVisitor(visit3, visitor3PersonId, false)
    visitEntityHelper.createVisitor(visit3, visitor4PersonId, false)
    visitEntityHelper.save(visit3)

    event = eventAuditEntityHelper.create(visit = visit3, type = EventAuditType.BOOKED_VISIT)
    // visit was BOOKED today but as it's a canceled visit it should be ignored
    testEventAuditRepository.updateCreateTimeStamp(event.id, visit3ApprovedBookedDate.atTime(9, 0))

    val visit4 = visitEntityHelper.create(
      prisonerId = prisonerId,
      slotDate = LocalDate.now().plusDays(7),
      sessionTemplate = sessionTemplateDefault,
      visitStatus = VisitStatus.BOOKED,
    )
    visitEntityHelper.createVisitor(visit4, visitor3PersonId, false)
    visitEntityHelper.save(visit4)

    event = eventAuditEntityHelper.create(visit = visit4, type = EventAuditType.MIGRATED_VISIT)
    // visit was BOOKED today but as it's a canceled visit it should be ignored
    testEventAuditRepository.updateCreateTimeStamp(event.id, visit4MigratedDate.atTime(9, 0))

    val responseSpec = callFindLastApprovedDate(webTestClient, prisonerId, visitorIds, roleVisitSchedulerHttpHeaders)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val lastApprovedDates = getResults(returnResult)
    assertThat(lastApprovedDates.size).isEqualTo(4)
    // should take the booked date for visit 1 as that is max
    assertThat(getLastApprovedDate(lastApprovedDates, visitor1PersonId)).isEqualTo(visit1BookedEventDate)
    // should take the approved date for visit 1 as that is max
    assertThat(getLastApprovedDate(lastApprovedDates, visitor2PersonId)).isEqualTo(visit2ApprovedEventDate)
    // should take migrated booked date for visit 4 as that is max
    assertThat(getLastApprovedDate(lastApprovedDates, visitor3PersonId)).isEqualTo(visit4MigratedDate)
    assertThat(getLastApprovedDate(lastApprovedDates, visitor4PersonId)).isNull()
  }

  @Test
  fun `when no role specified then access forbidden status is returned`() {
    // Given
    val authHttpHeaders = setAuthorisation(roles = listOf())
    val prisonerNumber = "AA-11-22"
    val visitorIds = listOf(1L, 2L, 3L)

    // When
    val responseSpec = callFindLastApprovedDate(webTestClient, prisonerNumber, visitorIds, authHttpHeaders)

    // Then
    responseSpec.expectStatus().isForbidden
  }

  @Test
  fun `when no token passed then unauthorized status is returned`() {
    // Given
    val prisonerNumber = "AA-11-22"

    // When
    val responseSpec = webTestClient.post().uri(FIND_LAST_APPROVED_DATE_FOR_VISITORS_BY_PRISONER.replace("{prisonerNumber}", prisonerNumber)).exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  fun callFindLastApprovedDate(
    webTestClient: WebTestClient,
    prisonerNumber: String,
    visitorIds: List<Long>,
    authHttpHeaders: (HttpHeaders) -> Unit,
  ): ResponseSpec {
    val dto = VisitorLastApprovedDatesRequestDto(visitorIds)
    return callPost(
      dto,
      webTestClient,
      FIND_LAST_APPROVED_DATE_FOR_VISITORS_BY_PRISONER.replace("{prisonerNumber}", prisonerNumber),
      authHttpHeaders,
    )
  }

  private fun getLastApprovedDate(lastApprovedDateList: List<VisitorLastApprovedDateDto>, visitorId: Long): LocalDate? = lastApprovedDateList.first { it.nomisPersonId == visitorId }.lastApprovedVisitDate

  private fun getResults(returnResult: WebTestClient.BodyContentSpec): List<VisitorLastApprovedDateDto> = objectMapper.readValue(returnResult.returnResult().responseBody, Array<VisitorLastApprovedDateDto>::class.java).toList()
}
