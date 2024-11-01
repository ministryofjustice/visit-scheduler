package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_VISITS_BY
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate

@DisplayName("GET /visits/session-template")
class VisitsByDateTest : IntegrationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `get booked visits by session template reference for a single session date`() {
    // Given
    val sessionDate = LocalDate.now().plusDays(4)
    val sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = LocalDate.now(), dayOfWeek = sessionDate.dayOfWeek)

    // When
    val application1 = createApplication(sessionTemplate, prisonerId = "AB123456")
    val application2 = createApplication(sessionTemplate, prisonerId = "AB123457")
    val application3 = createApplication(sessionTemplate, prisonerId = "AB123458")
    val application4 = createApplication(sessionTemplate, prisonerId = "AB123459")
    val application5 = createApplication(sessionTemplate, prisonerId = "AB123460")
    val visit1 = visitEntityHelper.createFromApplication(application1, sessionTemplate = sessionTemplate)
    val visit2 = visitEntityHelper.createFromApplication(application2, sessionTemplate = sessionTemplate)
    val visit3 = visitEntityHelper.createFromApplication(application3, sessionTemplate = sessionTemplate)
    val visit4 = visitEntityHelper.createFromApplication(application4, sessionTemplate = sessionTemplate)

    // visit without a event audit - should be last in the results
    val visit5 = visitEntityHelper.createFromApplication(application5, sessionTemplate = sessionTemplate)

    eventAuditEntityHelper.create(visit = visit4, type = EventAuditType.BOOKED_VISIT)
    Thread.sleep(1000)
    eventAuditEntityHelper.create(visit = visit2, type = EventAuditType.BOOKED_VISIT)
    Thread.sleep(1000)
    eventAuditEntityHelper.create(visit = visit3, type = EventAuditType.MIGRATED_VISIT)
    Thread.sleep(1000)
    eventAuditEntityHelper.create(visit = visit1, type = EventAuditType.BOOKED_VISIT)
    Thread.sleep(1000)
    eventAuditEntityHelper.create(visit = visit4, type = EventAuditType.UPDATED_VISIT)
    Thread.sleep(1000)

    val params = getVisitsBySessionTemplateQueryParams(sessionTemplate.reference, fromDate = sessionDate, toDate = sessionDate, visitStatus = listOf(BOOKED), prisonCode = sessionTemplate.prison.code).joinToString("&")
    val responseSpecVisitsBySession = callVisitsBySessionEndPoint(params)

    // Then
    responseSpecVisitsBySession.expectStatus().isOk
    val visits = parseVisitsPageResponse(responseSpecVisitsBySession)
    Assertions.assertThat(visits.size).isEqualTo(5)

    // ensure the results are sorted by audit event records
    Assertions.assertThat(visits[0].reference).isEqualTo(visit4.reference)
    Assertions.assertThat(visits[1].reference).isEqualTo(visit2.reference)
    Assertions.assertThat(visits[2].reference).isEqualTo(visit3.reference)
    Assertions.assertThat(visits[3].reference).isEqualTo(visit1.reference)
    Assertions.assertThat(visits[4].reference).isEqualTo(visit5.reference)
  }

  private fun createApplication(sessionTemplate: SessionTemplate, prisonerId: String): Application {
    var application = applicationEntityHelper.create(sessionTemplate = sessionTemplate, prisonerId = prisonerId, completed = false)
    applicationEntityHelper.createContact(application = application, name = "Jane Doe", phone = "01234 098765")
    applicationEntityHelper.createVisitor(application = application, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = application, description = "Some Text")
    application = applicationEntityHelper.save(application)
    return application
  }

  fun callVisitsBySessionEndPoint(
    params: String,
    page: Int = 0,
    pageSize: Int = 100,
    roles: List<String> = listOf("ROLE_VISIT_SCHEDULER"),
  ): ResponseSpec {
    val uri = "$GET_VISITS_BY?$params"
    return webTestClient.get().uri(uri)
      .headers(setAuthorisation(roles = roles))
      .exchange()
  }

  private fun getVisitsBySessionTemplateQueryParams(
    sessionTemplateReference: String?,
    fromDate: LocalDate,
    toDate: LocalDate,
    visitStatus: List<VisitStatus>? = null,
    visitRestrictions: List<VisitRestriction>? = null,
    prisonCode: String,
    page: Int? = 0,
    size: Int? = 100,
  ): List<String> {
    val queryParams = ArrayList<String>()
    sessionTemplateReference?.let {
      queryParams.add("sessionTemplateReference=$sessionTemplateReference")
    }
    queryParams.add("fromDate=$fromDate")
    queryParams.add("toDate=$toDate")

    visitStatus?.forEach {
      queryParams.add("visitStatus=$it")
    }
    visitRestrictions?.forEach {
      queryParams.add("visitRestrictions=$it")
    }
    queryParams.add("prisonCode=$prisonCode")
    queryParams.add("page=$page")
    queryParams.add("size=$size")
    return queryParams
  }
}
