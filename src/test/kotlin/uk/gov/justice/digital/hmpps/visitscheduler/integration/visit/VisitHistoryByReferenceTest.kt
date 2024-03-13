package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_VISIT_BY_REFERENCE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ApplicationSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.CreateApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.CreateApplicationRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.audit.EventAuditDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callApplicationForVisitChange
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCancelVisit
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitBook
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitHistoryByReference
import uk.gov.justice.digital.hmpps.visitscheduler.helper.submitApplication
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.application.ReserveSlotTest
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethodType.EMAIL
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethodType.PHONE
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethodType.WEBSITE
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType.BOOKED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType.CANCELLED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType.CHANGING_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType.RESERVED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType.UPDATED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus.PRISONER_CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.DayOfWeek.SATURDAY

@DisplayName("GET $GET_VISIT_BY_REFERENCE")
class VisitHistoryByReferenceTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `visit history by reference correct sequence`() {
    // Given
    val sessionTemplate = sessionTemplateEntityHelper.create()
    val reserveVisitSlotDto = createReserveVisitSlotDto(
      actionedBy = "reserve_guy",
      sessionTemplate = sessionTemplate,
    )

    val reservedDto = submitApplication(reserveVisitSlotDto)
    val bookedDto = bookVisit(reservedDto.reference, PHONE)
    val changingVisitDto1 = sumbmitApplicationToUpdateBooking(sessionTemplate, bookedDto.reference)
    bookVisit(changingVisitDto1.reference, EMAIL)
    val changingVisitDto2 = sumbmitApplicationToUpdateBooking(sessionTemplate, bookedDto.reference)
    bookVisit(changingVisitDto2.reference, EMAIL)
    cancelVisit(bookedDto)

    // When
    val responseSpec = callVisitHistoryByReference(webTestClient, bookedDto.reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val eventAuditList = getEventAuditList(responseSpec)

    Assertions.assertThat(eventAuditList.size).isEqualTo(7)

    Assertions.assertThat(eventAuditList[0].actionedBy).isEqualTo("reserve_guy")
    Assertions.assertThat(eventAuditList[0].type).isEqualTo(RESERVED_VISIT)
    Assertions.assertThat(eventAuditList[0].applicationMethodType).isEqualTo(PHONE)
    Assertions.assertThat(eventAuditList[0].createTimestamp).isNotNull
    Assertions.assertThat(eventAuditList[0].sessionTemplateReference).isEqualTo(sessionTemplate.reference)

    Assertions.assertThat(eventAuditList[1].actionedBy).isEqualTo("booking_guy")
    Assertions.assertThat(eventAuditList[1].type).isEqualTo(BOOKED_VISIT)
    Assertions.assertThat(eventAuditList[1].applicationMethodType).isEqualTo(PHONE)
    Assertions.assertThat(eventAuditList[1].createTimestamp).isNotNull
    Assertions.assertThat(eventAuditList[1].sessionTemplateReference).isEqualTo(sessionTemplate.reference)

    Assertions.assertThat(eventAuditList[2].actionedBy).isEqualTo("updated_by")
    Assertions.assertThat(eventAuditList[2].type).isEqualTo(CHANGING_VISIT)
    Assertions.assertThat(eventAuditList[2].applicationMethodType).isEqualTo(EMAIL)
    Assertions.assertThat(eventAuditList[2].createTimestamp).isNotNull
    Assertions.assertThat(eventAuditList[2].sessionTemplateReference).isEqualTo(sessionTemplate.reference)

    Assertions.assertThat(eventAuditList[3].actionedBy).isEqualTo("booking_guy")
    Assertions.assertThat(eventAuditList[3].type).isEqualTo(UPDATED_VISIT)
    Assertions.assertThat(eventAuditList[3].applicationMethodType).isEqualTo(EMAIL)
    Assertions.assertThat(eventAuditList[3].createTimestamp).isNotNull
    Assertions.assertThat(eventAuditList[3].sessionTemplateReference).isEqualTo(sessionTemplate.reference)

    Assertions.assertThat(eventAuditList[4].actionedBy).isEqualTo("updated_by")
    Assertions.assertThat(eventAuditList[4].type).isEqualTo(CHANGING_VISIT)
    Assertions.assertThat(eventAuditList[4].applicationMethodType).isEqualTo(EMAIL)
    Assertions.assertThat(eventAuditList[4].createTimestamp).isNotNull
    Assertions.assertThat(eventAuditList[4].sessionTemplateReference).isEqualTo(sessionTemplate.reference)

    Assertions.assertThat(eventAuditList[5].actionedBy).isEqualTo("booking_guy")
    Assertions.assertThat(eventAuditList[5].type).isEqualTo(UPDATED_VISIT)
    Assertions.assertThat(eventAuditList[5].applicationMethodType).isEqualTo(EMAIL)
    Assertions.assertThat(eventAuditList[5].createTimestamp).isNotNull
    Assertions.assertThat(eventAuditList[5].sessionTemplateReference).isEqualTo(sessionTemplate.reference)

    Assertions.assertThat(eventAuditList[6].actionedBy).isEqualTo("cancel_guy")
    Assertions.assertThat(eventAuditList[6].type).isEqualTo(CANCELLED_VISIT)
    Assertions.assertThat(eventAuditList[6].applicationMethodType).isEqualTo(WEBSITE)
    Assertions.assertThat(eventAuditList[6].createTimestamp).isNotNull
    Assertions.assertThat(eventAuditList[6].sessionTemplateReference).isEqualTo(sessionTemplate.reference)
  }

  @Test
  fun `visit history in sequence for session template change`() {
    // Given
    val sessionTemplateToChangeTo = sessionTemplateEntityHelper.create(prisonCode = sessionTemplateDefault.prison.code, dayOfWeek = SATURDAY)

    val reserveVisitSlotDto = createReserveVisitSlotDto(
      actionedBy = "reserve_guy",
      sessionTemplate = sessionTemplateDefault,
    )

    val applicationDto = submitApplication(reserveVisitSlotDto)
    val bookedDto = bookVisit(applicationDto.reference, PHONE)
    val changingVisitDto = sumbmitApplicationToUpdateBooking(sessionTemplateToChangeTo, bookedDto.reference)
    bookVisit(changingVisitDto.reference, EMAIL)

    // When
    val responseSpec = callVisitHistoryByReference(webTestClient, bookedDto.reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val eventAuditList = getEventAuditList(responseSpec)

    Assertions.assertThat(eventAuditList.size).isEqualTo(4)
    Assertions.assertThat(eventAuditList[0].type).isEqualTo(RESERVED_VISIT)
    Assertions.assertThat(eventAuditList[0].sessionTemplateReference).isEqualTo(sessionTemplateDefault.reference)
    Assertions.assertThat(eventAuditList[1].type).isEqualTo(BOOKED_VISIT)
    Assertions.assertThat(eventAuditList[1].sessionTemplateReference).isEqualTo(sessionTemplateDefault.reference)
    Assertions.assertThat(eventAuditList[2].type).isEqualTo(RESERVED_VISIT)
    Assertions.assertThat(eventAuditList[2].sessionTemplateReference).isEqualTo(sessionTemplateToChangeTo.reference)
    Assertions.assertThat(eventAuditList[3].type).isEqualTo(UPDATED_VISIT)
    Assertions.assertThat(eventAuditList[3].sessionTemplateReference).isEqualTo(sessionTemplateToChangeTo.reference)
  }

  private fun getEventAuditList(responseSpec: ResponseSpec) =
    objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, Array<EventAuditDto>::class.java)

  private fun cancelVisit(bookedDto: VisitDto) {
    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        PRISONER_CANCELLED,
        "Prisoner got covid",
      ),
      "cancel_guy",
      WEBSITE,
    )
    val cancelResponse = callCancelVisit(
      webTestClient,
      setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")),
      bookedDto.reference,
      cancelVisitDto,
    )
    cancelResponse.expectStatus().isOk
  }

  private fun submitApplication(createApplicationDto: CreateApplicationDto): ApplicationDto {
    val reservedResponse = submitApplication(webTestClient, roleVisitSchedulerHttpHeaders, createApplicationDto)
    reservedResponse.expectStatus().isCreated
    return getApplicationFomRestResponse(reservedResponse)
  }

  private fun bookVisit(applicationReference: String, applicationMethodType: ApplicationMethodType): VisitDto {
    val bookedResponse = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference, applicationMethodType)
    bookedResponse.expectStatus().isOk
    return getVisitFromRestResponse(bookedResponse)
  }

  private fun sumbmitApplicationToUpdateBooking(
    sessionTemplate: SessionTemplate,
    bookingReference: String,
  ): ApplicationDto {
    val changeVisitRequest = createReserveVisitSlotDto(actionedBy = "updated_by", sessionTemplate)
    val changedBookingResponse =
      callApplicationForVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, changeVisitRequest, bookingReference)
    changedBookingResponse.expectStatus().isCreated
    return getApplicationFomRestResponse(changedBookingResponse)
  }

  private fun getVisitFromRestResponse(responseSpec: ResponseSpec): VisitDto {
    return objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, VisitDto::class.java)
  }

  private fun getApplicationFomRestResponse(responseSpec: ResponseSpec): ApplicationDto {
    return objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, ApplicationDto::class.java)
  }

  private fun createReserveVisitSlotDto(actionedBy: String = ReserveSlotTest.ACTIONED_BY_USER_NAME, sessionTemplate: SessionTemplate): CreateApplicationDto {
    return CreateApplicationDto(
      prisonerId = "FF0000FF",
      sessionDate = startDate,
      sessionTemplateReference = sessionTemplate.reference,
      applicationRestriction = OPEN,
      visitContact = ContactDto("John Smith", "013448811538"),
      visitors = setOf(VisitorDto(123, true), VisitorDto(124, false)),
      visitorSupport = ApplicationSupportDto("Some Text"),
      actionedBy = actionedBy,
    )
  }
}
