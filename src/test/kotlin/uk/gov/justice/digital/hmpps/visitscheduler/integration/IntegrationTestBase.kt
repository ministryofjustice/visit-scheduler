package uk.gov.justice.digital.hmpps.visitscheduler.integration

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.config.ErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFY_CONTROLLER_CALLBACK_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFY_CONTROLLER_CREATE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_SESSIONS_AVAILABLE_CONTROLLER_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_SESSION_CONTROLLER_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ApplicationSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.CreateApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.audit.EventAuditDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus.ACCEPTED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.BOOKED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.CANCELLED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.RESERVED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus.CANCELLATION
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.notify.NotifyCallbackNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.notify.NotifyCreateNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category.SessionCategoryGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.SessionIncentiveLevelGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.SessionLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.ApplicationEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AssertHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.DeleteEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.EventAuditEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.JwtAuthHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.PrisonEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.SessionLocationGroupHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.SessionPrisonerCategoryHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.SessionPrisonerIncentiveLevelHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.SessionTemplateEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.VisitEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.VisitNotifyHistoryHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.VsipReportingEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGet
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callPut
import uk.gov.justice.digital.hmpps.visitscheduler.integration.container.LocalStackContainer
import uk.gov.justice.digital.hmpps.visitscheduler.integration.container.PostgresContainer
import uk.gov.justice.digital.hmpps.visitscheduler.integration.mock.HmppsAuthExtension
import uk.gov.justice.digital.hmpps.visitscheduler.integration.mock.NonAssociationsApiMockServer
import uk.gov.justice.digital.hmpps.visitscheduler.integration.mock.PrisonApiMockServer
import uk.gov.justice.digital.hmpps.visitscheduler.integration.mock.PrisonOffenderSearchMockServer
import uk.gov.justice.digital.hmpps.visitscheduler.integration.mock.PrisonerContactRegistryMockServer
import uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.application.ReserveSlotTest
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestEventAuditRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestPrisonRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestPrisonUserClientRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionSlotRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.DateRange
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionDatesUtil
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID.randomUUID

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(HmppsAuthExtension::class)
abstract class IntegrationTestBase {
  @Suppress("unused")
  @Autowired
  lateinit var webTestClient: WebTestClient

  @Autowired
  protected lateinit var objectMapper: ObjectMapper

  @Autowired
  protected lateinit var sessionDatesUtil: SessionDatesUtil

  @Autowired
  protected lateinit var visitEntityHelper: VisitEntityHelper

  @Autowired
  protected lateinit var applicationEntityHelper: ApplicationEntityHelper

  @Autowired
  protected lateinit var eventAuditEntityHelper: EventAuditEntityHelper

  @Autowired
  protected lateinit var sessionTemplateEntityHelper: SessionTemplateEntityHelper

  @Autowired
  protected lateinit var sessionPrisonerCategoryHelper: SessionPrisonerCategoryHelper

  @Autowired
  protected lateinit var eventAuditRepository: TestEventAuditRepository

  @Autowired
  protected lateinit var testSessionSlotRepository: TestSessionSlotRepository

  @Autowired
  protected lateinit var testPrisonRepository: TestPrisonRepository

  @Autowired
  protected lateinit var testPrisonUserClientRepository: TestPrisonUserClientRepository

  @Autowired
  protected lateinit var sessionPrisonerIncentiveLevelHelper: SessionPrisonerIncentiveLevelHelper

  @Autowired
  lateinit var prisonEntityHelper: PrisonEntityHelper

  @Autowired
  protected lateinit var sessionLocationGroupHelper: SessionLocationGroupHelper

  @Autowired
  protected lateinit var deleteEntityHelper: DeleteEntityHelper

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthHelper

  @Autowired
  protected lateinit var assertHelper: AssertHelper

  @Autowired
  protected lateinit var vsipReportingEntityHelper: VsipReportingEntityHelper

  @Autowired
  protected lateinit var visitNotifyHistoryHelper: VisitNotifyHistoryHelper

  init {
    // Resolves an issue where Wiremock keeps previous sockets open from other tests causing connection resets
    System.setProperty("http.keepAlive", "false")
  }

  lateinit var prison: Prison
  lateinit var sessionTemplateDefault: SessionTemplate
  lateinit var startDate: LocalDate

  @BeforeEach
  fun resetStubs() {
    prisonApiMockServer.resetAll()
    prisonOffenderSearchMockServer.resetAll()
    prisonerContactRegistryMockServer.resetAll()
    sessionTemplateDefault = sessionTemplateEntityHelper.create(prisonCode = "DFT")
    startDate = this.sessionDatesUtil.getFirstBookableSessionDay(sessionTemplateDefault)
  }

  fun shouldICreateDefault(): Boolean = true

  @AfterEach
  @Transactional
  internal fun deleteAll() {
    deleteEntityHelper.deleteAll()
  }

  fun getApplicationDto(responseSpec: ResponseSpec): ApplicationDto = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, ApplicationDto::class.java)

  fun getVisitDto(responseSpec: ResponseSpec): VisitDto = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, VisitDto::class.java)

  fun getSessionTemplate(responseSpec: ResponseSpec): SessionTemplateDto = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, SessionTemplateDto::class.java)

  fun getSessionLocationGroup(responseSpec: ResponseSpec): SessionLocationGroupDto = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, SessionLocationGroupDto::class.java)

  fun getSessionLocationGroups(responseSpec: ResponseSpec): Array<SessionLocationGroupDto> = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, Array<SessionLocationGroupDto>::class.java)

  fun getSessionCategoryGroups(responseSpec: ResponseSpec): Array<SessionCategoryGroupDto> = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, Array<SessionCategoryGroupDto>::class.java)

  fun getSessionCategoryGroup(responseSpec: ResponseSpec): SessionCategoryGroupDto = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, SessionCategoryGroupDto::class.java)

  fun getSessionIncentiveGroup(responseSpec: ResponseSpec): SessionIncentiveLevelGroupDto = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, SessionIncentiveLevelGroupDto::class.java)

  fun getSessionIncentiveGroups(responseSpec: ResponseSpec): Array<SessionIncentiveLevelGroupDto> = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, Array<SessionIncentiveLevelGroupDto>::class.java)

  fun getCheckingMatchingTemplatesOnCreate(responseSpec: ResponseSpec): Array<String> = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, Array<String>::class.java)

  fun getErrorResponse(responseSpec: ResponseSpec): ErrorResponse = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, ErrorResponse::class.java)

  fun getEventAuditList(responseSpec: ResponseSpec) = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, Array<EventAuditDto>::class.java)

  internal fun setAuthorisation(
    user: String = "AUTH_ADM",
    roles: List<String> = listOf(),
    scopes: List<String> = listOf(),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisation(user, roles, scopes)

  companion object {
    internal val prisonApiMockServer = PrisonApiMockServer()
    internal val nonAssociationsApiMockServer = NonAssociationsApiMockServer()
    internal val prisonOffenderSearchMockServer = PrisonOffenderSearchMockServer()
    internal val prisonerContactRegistryMockServer = PrisonerContactRegistryMockServer()

    @BeforeAll
    @JvmStatic
    fun startMocks() {
      prisonApiMockServer.start()
      nonAssociationsApiMockServer.start()
      prisonOffenderSearchMockServer.start()
      prisonerContactRegistryMockServer.start()
    }

    @AfterAll
    @JvmStatic
    fun stopMocks() {
      nonAssociationsApiMockServer.stop()
      prisonApiMockServer.stop()
      prisonOffenderSearchMockServer.stop()
      prisonerContactRegistryMockServer.stop()
    }

    private val pgContainer = PostgresContainer.instance
    private val lsContainer = LocalStackContainer.instance

    @JvmStatic
    @DynamicPropertySource
    fun properties(registry: DynamicPropertyRegistry) {
      pgContainer?.run {
        registry.add("spring.datasource.url", pgContainer::getJdbcUrl)
        registry.add("spring.datasource.username", pgContainer::getUsername)
        registry.add("spring.datasource.password", pgContainer::getPassword)
        registry.add("spring.datasource.placeholders.database_update_password", pgContainer::getPassword)
        registry.add("spring.datasource.placeholders.database_read_only_password", pgContainer::getPassword)
        registry.add("spring.flyway.url", pgContainer::getJdbcUrl)
        registry.add("spring.flyway.user", pgContainer::getUsername)
        registry.add("spring.flyway.password", pgContainer::getPassword)
      }
      lsContainer?.run {
        registry.add("hmpps.sqs.localstackUrl") { lsContainer.getEndpointOverride(org.testcontainers.containers.localstack.LocalStackContainer.Service.SNS) }
        registry.add("hmpps.sqs.region") { lsContainer.region }
      }
    }
  }

  fun formatDateToString(dateTime: LocalDateTime): String = dateTime.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_DATE_TIME)

  fun formatStartSlotDateTimeToString(sessionSlot: SessionSlot): String = sessionSlot.slotStart.truncatedTo(ChronoUnit.SECONDS).format(
    DateTimeFormatter.ISO_DATE_TIME,
  )

  fun formatSlotEndDateTimeToString(sessionSlot: SessionSlot): String = sessionSlot.slotEnd.truncatedTo(ChronoUnit.SECONDS).format(
    DateTimeFormatter.ISO_DATE_TIME,
  )

  fun formatDateToString(sessionSlot: SessionSlot): String = formatDateToString(sessionSlot.slotDate)

  fun formatDateToString(localDate: LocalDate): String = localDate.format(DateTimeFormatter.ISO_DATE)

  fun createApplicationAndVisit(
    prisonerId: String? = "testPrisonerId",
    sessionTemplate: SessionTemplate,
    visitStatus: VisitStatus? = VisitStatus.BOOKED,
    slotDate: LocalDate? = null,
    visitRestriction: VisitRestriction = VisitRestriction.OPEN,
    visitContact: ContactDto = ContactDto(name = "Jane Doe", telephone = "01234 098765", email = "email@example.com"),
    userType: UserType = STAFF,
  ): Visit {
    val application = createApplicationAndSave(prisonerId = prisonerId, sessionTemplate, sessionTemplate.prison.code, slotDate, applicationStatus = ACCEPTED, visitRestriction = visitRestriction, visitContact = visitContact, userType = userType)
    return createVisitAndSave(visitStatus = visitStatus!!, applicationEntity = application, sessionTemplateLocal = sessionTemplate)
  }

  fun createApplicationAndVisit(
    prisonerId: String? = "testPrisonerId",
    visitStatus: VisitStatus? = VisitStatus.BOOKED,
    slotDate: LocalDate,
    visitRestriction: VisitRestriction = VisitRestriction.OPEN,
    prisonCode: String? = null,
    userType: UserType = STAFF,
  ): Visit {
    val application = createApplicationAndSave(prisonerId = prisonerId, slotDate = slotDate, applicationStatus = ACCEPTED, visitRestriction = visitRestriction, prisonCode = prisonCode, userType = userType)
    return createVisitAndSave(visitStatus = visitStatus!!, applicationEntity = application)
  }

  fun createApplicationAndSave(
    prisonerId: String? = "testPrisonerId",
    prisonCode: String? = null,
    slotDate: LocalDate,
    applicationStatus: ApplicationStatus,
    reservedSlot: Boolean = true,
    visitRestriction: VisitRestriction = VisitRestriction.OPEN,
  ): Application = createApplicationAndSave(prisonerId = prisonerId, sessionTemplate = sessionTemplateDefault, prisonCode = prisonCode, slotDate = slotDate, applicationStatus = applicationStatus, reservedSlot = reservedSlot, visitRestriction = visitRestriction)

  fun createApplicationAndSave(
    prisonerId: String? = "testPrisonerId",
    sessionTemplate: SessionTemplate? = null,
    prisonCode: String? = sessionTemplateDefault.prison.code,
    slotDate: LocalDate? = null,
    applicationStatus: ApplicationStatus,
    reservedSlot: Boolean = true,
    visitRestriction: VisitRestriction = VisitRestriction.OPEN,
    visitContact: ContactDto = ContactDto(name = "Jane Doe", telephone = "01234 098765", email = "email@example.com"),
    userType: UserType = STAFF,
  ): Application {
    val sessionTemplateLocal = sessionTemplate ?: sessionTemplateDefault
    val slotDateLocal = slotDate ?: run {
      sessionTemplateLocal.validFromDate.with(sessionTemplateLocal.dayOfWeek).plusWeeks(1)
    }

    val applicationEntity = applicationEntityHelper.create(
      prisonerId = prisonerId!!,
      sessionTemplate = sessionTemplateLocal,
      applicationStatus = applicationStatus,
      reservedSlot = reservedSlot,
      prisonCode = prisonCode,
      slotDate = slotDateLocal,
      visitRestriction = visitRestriction,
      userType = userType,
    )
    applicationEntityHelper.createContact(application = applicationEntity, visitContact.name, visitContact.telephone, visitContact.email)
    applicationEntityHelper.createVisitor(application = applicationEntity, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createVisitor(application = applicationEntity, nomisPersonId = 621L, visitContact = false)
    applicationEntityHelper.createSupport(application = applicationEntity, description = "Some More Text")

    return applicationEntityHelper.save(applicationEntity)
  }

  fun createVisitAndSave(visitStatus: VisitStatus, applicationEntity: Application, sessionTemplateLocal: SessionTemplate? = null): Visit = visitEntityHelper.createFromApplication(visitStatus = visitStatus, sessionTemplate = sessionTemplateLocal ?: sessionTemplateDefault, application = applicationEntity)

  // creates a visit with a null session template reference
  fun createVisitAndSave(visitStatus: VisitStatus, applicationEntity: Application): Visit = visitEntityHelper.createFromApplication(visitStatus = visitStatus, application = applicationEntity)

  fun parseVisitsPageResponse(responseSpec: ResponseSpec): List<VisitDto> {
    class Page {
      @JsonProperty("content")
      lateinit var content: List<VisitDto>
    }

    val content = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, Page::class.java)
    return content.content
  }

  fun parseVisitsResponse(responseSpec: ResponseSpec): List<VisitDto> = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, Array<VisitDto>::class.java).toList()

  fun createVisit(
    prisonerId: String? = "testPrisonerId",
    actionedByValue: String,
    visitStatus: VisitStatus,
    sessionTemplate: SessionTemplate,
    userType: UserType,
    slotDateWeeks: Long,
  ): Visit {
    val eventJourney = mutableListOf(RESERVED_VISIT, BOOKED_VISIT)
    val actionedByValues = mutableListOf(actionedByValue, actionedByValue)
    val userTypes = mutableListOf(userType, userType)

    var visit = createApplicationAndVisit(
      prisonerId = prisonerId,
      slotDate = LocalDate.now().plusWeeks(slotDateWeeks),
      sessionTemplate = sessionTemplate,
      visitStatus = visitStatus,
      userType = userType,
    )

    if (CANCELLED == visitStatus) {
      eventJourney.add(CANCELLED_VISIT)
      visit.outcomeStatus = CANCELLATION
      actionedByValues.add(actionedByValue + "_staff")
      userTypes.add(STAFF)
    }

    visit = visitEntityHelper.save(visit)

    eventAuditEntityHelper.createForVisitAndApplication(
      visit,
      actionedByValues = actionedByValues,
      types = eventJourney,
      userTypes = userTypes,
    )
    return visit
  }

  fun createReserveVisitSlotDto(
    actionedBy: String = ReserveSlotTest.ACTIONED_BY_USER_NAME,
    prisonerId: String = "FF0000FF",
    sessionTemplate: SessionTemplate? = null,
    slotDate: LocalDate? = null,
    support: String = "Some Text",
    sessionRestriction: SessionRestriction = SessionRestriction.OPEN,
    allowOverBooking: Boolean = false,
    userType: UserType = STAFF,
  ): CreateApplicationDto = CreateApplicationDto(
    prisonerId,
    sessionTemplateReference = sessionTemplate?.reference ?: "IDontExistSessionTemplate",
    sessionDate = slotDate ?: sessionTemplate?.let { sessionDatesUtil.getFirstBookableSessionDay(sessionTemplate) } ?: LocalDate.now(),
    applicationRestriction = sessionRestriction,
    visitContact = ContactDto("John Smith", "013448811538", "email@example.com"),
    visitors = setOf(VisitorDto(123, true), VisitorDto(124, false)),
    visitorSupport = ApplicationSupportDto(support),
    actionedBy = actionedBy,
    userType = userType,
    allowOverBooking = allowOverBooking,
  )

  fun callCreateNotifyNotification(
    webTestClient: WebTestClient,
    dto: NotifyCreateNotificationDto? = null,
    authHttpHeaders: (HttpHeaders) -> Unit,
  ): ResponseSpec = callPut(
    dto,
    webTestClient,
    VISIT_NOTIFY_CONTROLLER_CREATE_PATH,
    authHttpHeaders,
  )

  fun callNotifyCallbackNotification(
    webTestClient: WebTestClient,
    dto: NotifyCallbackNotificationDto? = null,
    authHttpHeaders: (HttpHeaders) -> Unit,
  ): ResponseSpec = callPut(
    dto,
    webTestClient,
    VISIT_NOTIFY_CONTROLLER_CALLBACK_PATH,
    authHttpHeaders,
  )

  fun createNotifyCreateNotificationDto(
    notificationId: String = randomUUID().toString(),
    eventAuditReference: Long,
    createdAt: LocalDateTime = LocalDateTime.now(),
    notificationType: String,
    templateID: String = "template-id",
    templateVersion: String = "v1",
  ): NotifyCreateNotificationDto = NotifyCreateNotificationDto(
    notificationId = notificationId,
    eventAuditReference = eventAuditReference.toString(),
    createdAt = createdAt,
    notificationType = notificationType,
    templateId = templateID,
    templateVersion = templateVersion,
  )

  protected fun callGetSessions(
    prisonCode: String? = "SPC",
    prisonerId: String,
    policyNoticeDaysMin: Int? = null,
    policyNoticeDaysMax: Int? = null,
    userName: String? = null,
    userType: UserType,
    authHttpHeaders: (HttpHeaders) -> Unit,
  ): ResponseSpec {
    val urlParams = mutableListOf(
      "prisonerId=$prisonerId",
      "prisonId=$prisonCode",
      "userType=$userType",
    )
    policyNoticeDaysMin?.let {
      urlParams.add("min=$policyNoticeDaysMin")
    }
    policyNoticeDaysMax?.let {
      urlParams.add("max=$policyNoticeDaysMax")
    }
    userName?.let {
      urlParams.add("username=$userName")
    }

    val uri = VISIT_SESSION_CONTROLLER_PATH + "?" + urlParams.joinToString("&")

    return callGet(
      webTestClient,
      uri,
      authHttpHeaders,
    )
  }

  protected fun callGetAvailableSessions(
    prisonCode: String? = "SPC",
    prisonerId: String,
    sessionRestriction: SessionRestriction,
    policyNoticeDaysMin: Int = 2,
    policyNoticeDaysMax: Int = 28,
    excludedApplicationReference: String? = null,
    username: String? = null,
    userType: UserType = UserType.PUBLIC,
    authHttpHeaders: (HttpHeaders) -> Unit,
  ): ResponseSpec {
    val today = LocalDate.now()
    val fromDate = today.plusDays(policyNoticeDaysMin.toLong())
    val toDate = today.plusDays(policyNoticeDaysMax.toLong())
    val dateRange = DateRange(fromDate, toDate)

    val uri = VISIT_SESSIONS_AVAILABLE_CONTROLLER_PATH
    val uriQueryParams = getAvailableSessionsQueryParams(
      prisonCode = prisonCode!!,
      prisonerId = prisonerId,
      sessionRestriction = sessionRestriction,
      dateRange = dateRange,
      policyNoticeDaysMin = policyNoticeDaysMin,
      policyNoticeDaysMax = policyNoticeDaysMax,
      excludedApplicationReference = excludedApplicationReference,
      username = username,
      userType = userType,
    ).joinToString("&")

    return webTestClient.get().uri("$uri?$uriQueryParams")
      .headers(authHttpHeaders)
      .exchange()
  }

  private fun getAvailableSessionsQueryParams(
    prisonCode: String,
    prisonerId: String,
    sessionRestriction: SessionRestriction,
    dateRange: DateRange,
    policyNoticeDaysMin: Int,
    policyNoticeDaysMax: Int,
    excludedApplicationReference: String?,
    username: String?,
    userType: UserType,
  ): List<String> {
    val queryParams = ArrayList<String>()
    queryParams.add("prisonId=$prisonCode")
    queryParams.add("prisonerId=$prisonerId")
    queryParams.add("sessionRestriction=$sessionRestriction")
    queryParams.add("fromDate=${dateRange.fromDate}")
    queryParams.add("toDate=${dateRange.toDate}")
    queryParams.add("policyNoticeDaysMin=$policyNoticeDaysMin")
    queryParams.add("policyNoticeDaysMax=$policyNoticeDaysMax")

    excludedApplicationReference?.let {
      queryParams.add("excludedApplicationReference=$excludedApplicationReference")
    }

    username?.let {
      queryParams.add("username=$username")
    }

    queryParams.add("userType=${userType.name}")
    return queryParams
  }
}
