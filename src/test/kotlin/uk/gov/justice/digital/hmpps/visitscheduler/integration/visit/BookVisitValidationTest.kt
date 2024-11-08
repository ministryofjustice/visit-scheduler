package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import io.netty.handler.codec.http.HttpResponseStatus.UNPROCESSABLE_ENTITY
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.config.ApplicationValidationErrorResponse
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_BOOK
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationValidationErrorCodes.APPLICATION_INVALID_NON_ASSOCIATION_VISITS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationValidationErrorCodes.APPLICATION_INVALID_NO_SLOT_CAPACITY
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationValidationErrorCodes.APPLICATION_INVALID_NO_VO_BALANCE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationValidationErrorCodes.APPLICATION_INVALID_PRISON_PRISONER_MISMATCH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationValidationErrorCodes.APPLICATION_INVALID_SESSION_NOT_AVAILABLE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationValidationErrorCodes.APPLICATION_INVALID_VISIT_ALREADY_BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.VisitBalancesDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch.PrisonerSearchResultDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitBook
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application

@Transactional(propagation = SUPPORTS)
@DisplayName("test validations on PUT $VISIT_BOOK API call.")
class BookVisitValidationTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  private lateinit var reservedPublicApplication: Application
  private lateinit var reservedStaffApplication: Application

  private final val prisonerId = "ABC123QQ"
  private final val prisonCode = "ABC"

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    reservedPublicApplication = applicationEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
      completed = false,
      userType = UserType.PUBLIC,
    )

    applicationEntityHelper.createContact(application = reservedPublicApplication, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = reservedPublicApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = reservedPublicApplication, description = "Some Text")
    reservedPublicApplication = applicationEntityHelper.save(reservedPublicApplication)

    reservedStaffApplication = applicationEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
      completed = false,
      userType = UserType.STAFF,
    )

    applicationEntityHelper.createContact(application = reservedStaffApplication, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = reservedStaffApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = reservedStaffApplication, description = "Some Text")
    reservedStaffApplication = applicationEntityHelper.save(reservedStaffApplication)
  }

  @Test
  fun `when public application does not match prisoner prison code an exception is thrown`() {
    // Given
    val applicationReference = reservedPublicApplication.reference
    // application's prison code is different to prisoner's prison code
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, prisonId = "SWI")
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then

    responseSpec
      .expectStatus().isEqualTo(UNPROCESSABLE_ENTITY.code())

    val validationErrorResponse = getValidationErrorResponse(responseSpec)
    assertThat(validationErrorResponse.validationErrors.size).isEqualTo(1)
    assertThat(validationErrorResponse.validationErrors).contains(APPLICATION_INVALID_PRISON_PRISONER_MISMATCH)
  }

  @Test
  fun `when public application matches prisoner prison code visit is booked successfully`() {
    // Given
    val applicationReference = reservedPublicApplication.reference
    // application's prison code is different to prisoner's prison code
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when public application is not eligible for session due to incentive mismatch an exception is thrown`() {
    // Given
    // prisoner has incentive level as STANDARD
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, IncentiveLevel.STANDARD)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // session is available for Enhanced scope
    val sessionTemplate = sessionTemplateEntityHelper.create(
      permittedIncentiveLevels = mutableListOf(sessionPrisonerIncentiveLevelHelper.create("Enhanced", prisonCode, incentiveLevelList = listOf(IncentiveLevel.ENHANCED))),
    )

    // application has been created with session for ENHANCED scope
    val application = applicationEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplate,
      completed = false,
      userType = UserType.PUBLIC,
    )

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, application.reference)

    // Then
    responseSpec.expectStatus().isEqualTo(UNPROCESSABLE_ENTITY.code())

    val validationErrorResponse = getValidationErrorResponse(responseSpec)
    assertThat(validationErrorResponse.validationErrors.size).isEqualTo(1)
    assertThat(validationErrorResponse.validationErrors).contains(APPLICATION_INVALID_SESSION_NOT_AVAILABLE)
  }

  @Test
  fun `when prisoner incentive level matches session visit is booked successfully`() {
    // Given
    // prisoner has incentive level as STANDARD
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, IncentiveLevel.STANDARD)

    // session is available for STANDARD scope
    val sessionTemplate = sessionTemplateEntityHelper.create(
      permittedIncentiveLevels = mutableListOf(sessionPrisonerIncentiveLevelHelper.create("STANDARD", prisonCode, incentiveLevelList = listOf(IncentiveLevel.STANDARD))),
    )

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // application has been created with session for STANDARD scope
    val application = applicationEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplate,
      completed = false,
      userType = UserType.PUBLIC,
    )
    applicationEntityHelper.createContact(application = application, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = application, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.save(application)
    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, application.reference)

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when public application is not eligible for session due to category mismatch an exception is thrown`() {
    // Given
    // prisoner has category B
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, category = PrisonerCategoryType.B.code)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // session is available for Category C
    val sessionTemplate = sessionTemplateEntityHelper.create(
      permittedCategories = mutableListOf(sessionPrisonerCategoryHelper.create("Enhanced", prisonCode, prisonerCategories = listOf(PrisonerCategoryType.C))),
    )

    // application has been created with session for ENHANCED scope
    val application = applicationEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplate,
      completed = false,
      userType = UserType.PUBLIC,
    )

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, application.reference)

    // Then
    responseSpec
      .expectStatus().isEqualTo(UNPROCESSABLE_ENTITY.code())

    val validationErrorResponse = getValidationErrorResponse(responseSpec)
    assertThat(validationErrorResponse.validationErrors.size).isEqualTo(1)
    assertThat(validationErrorResponse.validationErrors).contains(APPLICATION_INVALID_SESSION_NOT_AVAILABLE)
  }

  @Test
  fun `when prisoner category matches session visit is booked successfully`() {
    // Given
    // prisoner has category as B
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, category = PrisonerCategoryType.B.code)

    // session is available for Category B
    val sessionTemplate = sessionTemplateEntityHelper.create(
      permittedCategories = mutableListOf(sessionPrisonerCategoryHelper.create("CatB", prisonCode, prisonerCategories = listOf(PrisonerCategoryType.B))),
    )

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // application has been created with session for STANDARD scope
    val application = applicationEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplate,
      completed = false,
      userType = UserType.PUBLIC,
    )
    applicationEntityHelper.createContact(application = application, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = application, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.save(application)
    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, application.reference)

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when public application is not eligible for session due to location mismatch an exception is thrown`() {
    // Given
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)

    // session is available for location B
    val sessionTemplate = sessionTemplateEntityHelper.create(
      permittedLocationGroups = mutableListOf(
        sessionLocationGroupHelper.create("Enhanced", prisonCode, listOf(AllowedSessionLocationHierarchy(levelOneCode = "B"))),
      ),
    )

    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    // prisoner is in location C
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // application has been created with session for location B
    val application = applicationEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplate,
      completed = false,
      userType = UserType.PUBLIC,
    )

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, application.reference)

    // Then
    responseSpec
      .expectStatus().isEqualTo(UNPROCESSABLE_ENTITY.code())

    val validationErrorResponse = getValidationErrorResponse(responseSpec)
    assertThat(validationErrorResponse.validationErrors.size).isEqualTo(1)
    assertThat(validationErrorResponse.validationErrors).contains(APPLICATION_INVALID_SESSION_NOT_AVAILABLE)
  }

  @Test
  fun `when public application location matches session template location visit is booked successfully`() {
    // Given
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    // prisoner is in location C
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // session is available for location C
    val sessionTemplate = sessionTemplateEntityHelper.create(
      permittedLocationGroups = mutableListOf(
        sessionLocationGroupHelper.create("location", prisonCode, listOf(AllowedSessionLocationHierarchy(levelOneCode = "C"))),
      ),
    )

    // application has been created with session for location C
    val application = applicationEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplate,
      completed = false,
      userType = UserType.PUBLIC,
    )
    applicationEntityHelper.createContact(application = application, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = application, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.save(application)

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, application.reference)

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when public application clashes with existing non association visits an exception is thrown`() {
    // Given
    val nonAssociationPrisonerId = "SS11ABC"
    val visitBalance = VisitBalancesDto(remainingVo = 5, remainingPvo = 5)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(prisonerId, nonAssociationPrisonerId)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, visitBalance)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")

    // visit for non-association has been added on the same date as reserved application
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      prisonerId = nonAssociationPrisonerId,
      slotDate = reservedPublicApplication.sessionSlot.slotDate,
      visitStatus = VisitStatus.BOOKED,
      prisonCode = reservedPublicApplication.prison.code,
    )

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, reservedPublicApplication.reference)

    // Then
    responseSpec
      .expectStatus().isEqualTo(UNPROCESSABLE_ENTITY.code())

    val validationErrorResponse = getValidationErrorResponse(responseSpec)
    assertThat(validationErrorResponse.validationErrors.size).isEqualTo(1)
    assertThat(validationErrorResponse.validationErrors).contains(APPLICATION_INVALID_NON_ASSOCIATION_VISITS)
  }

  @Test
  fun `when staff application clashes with existing non association visits an exception is thrown`() {
    // Given
    val nonAssociationPrisonerId = "SS11ABC"
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(prisonerId, nonAssociationPrisonerId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")

    // visit for non-association has been added on the same date as reserved application
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      prisonerId = nonAssociationPrisonerId,
      slotDate = reservedStaffApplication.sessionSlot.slotDate,
      visitStatus = VisitStatus.BOOKED,
      prisonCode = reservedStaffApplication.prison.code,
    )

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, reservedStaffApplication.reference)

    // Then
    responseSpec
      .expectStatus().isEqualTo(UNPROCESSABLE_ENTITY.code())

    val validationErrorResponse = getValidationErrorResponse(responseSpec)
    assertThat(validationErrorResponse.validationErrors.size).isEqualTo(1)
    assertThat(validationErrorResponse.validationErrors).contains(APPLICATION_INVALID_NON_ASSOCIATION_VISITS)
  }

  @Test
  fun `when public application clashes with existing non association application that is not booked then visit is booked successfully`() {
    // Given
    val nonAssociationPrisonerId = "SS11ABC"
    val visitBalance = VisitBalancesDto(remainingVo = 5, remainingPvo = 5)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(prisonerId, nonAssociationPrisonerId)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, visitBalance)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")

    // application for non-association has been added on the same date
    applicationEntityHelper.create(
      prisonerId = nonAssociationPrisonerId,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
      completed = false,
      userType = UserType.PUBLIC,
    )

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, reservedPublicApplication.reference)

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when staff application clashes with existing non association application that is not booked then visit is booked successfully`() {
    // Given
    val nonAssociationPrisonerId = "SS11ABC"
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(prisonerId, nonAssociationPrisonerId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")

    // application for non-association has been added on the same date
    applicationEntityHelper.create(
      prisonerId = nonAssociationPrisonerId,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
      completed = false,
      userType = UserType.STAFF,
    )

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, reservedStaffApplication.reference)

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when non association visit is for a different prison a public visit is booked successfully`() {
    // Given
    val nonAssociationPrisonerId = "SS11ABC"
    val visitBalance = VisitBalancesDto(remainingVo = 5, remainingPvo = 5)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(prisonerId, nonAssociationPrisonerId)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, visitBalance)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")

    // visit for non-association has been added on the same date but is for a different prison
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      prisonerId = nonAssociationPrisonerId,
      slotDate = reservedPublicApplication.sessionSlot.slotDate,
      visitStatus = VisitStatus.BOOKED,
      prisonCode = "TST",
    )

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, reservedPublicApplication.reference)

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when non association visit is for a different prison a staff visit is booked successfully`() {
    // Given
    val nonAssociationPrisonerId = "SS11ABC"
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(prisonerId, nonAssociationPrisonerId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")

    // visit for non-association has been added on the same date but is for a different prison
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      prisonerId = nonAssociationPrisonerId,
      slotDate = reservedStaffApplication.sessionSlot.slotDate,
      visitStatus = VisitStatus.BOOKED,
      prisonCode = "TST",
    )

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, reservedStaffApplication.reference)

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when public application is being booked on an already booked visit on same session for same prisoner an exception is thrown`() {
    // Given
    val visitBalance = VisitBalancesDto(remainingVo = 5, remainingPvo = 5)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, visitBalance)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")

    // visit for prisoner has already been booked on same session
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      prisonerId = prisonerId,
      slotDate = reservedPublicApplication.sessionSlot.slotDate,
      visitStatus = VisitStatus.BOOKED,
      prisonCode = reservedPublicApplication.prison.code,
    )

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, reservedPublicApplication.reference)

    // Then
    val validationErrorResponse = getValidationErrorResponse(responseSpec)
    assertThat(validationErrorResponse.validationErrors.size).isEqualTo(1)
    assertThat(validationErrorResponse.validationErrors).contains(APPLICATION_INVALID_VISIT_ALREADY_BOOKED)
  }

  @Test
  fun `when staff application is being booked on an already booked visit on same session for same prisoner an exception is thrown`() {
    // Given
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")

    // visit for prisoner has already been booked on same session
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      prisonerId = prisonerId,
      slotDate = reservedStaffApplication.sessionSlot.slotDate,
      visitStatus = VisitStatus.BOOKED,
      prisonCode = reservedStaffApplication.prison.code,
    )

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, reservedStaffApplication.reference)

    // Then
    val validationErrorResponse = getValidationErrorResponse(responseSpec)
    assertThat(validationErrorResponse.validationErrors.size).isEqualTo(1)
    assertThat(validationErrorResponse.validationErrors).contains(APPLICATION_INVALID_VISIT_ALREADY_BOOKED)
  }

  @Test
  fun `when public application is being booked on a cancelled visit on same session for same prisoner visit is booked successfully`() {
    // Given
    val visitBalance = VisitBalancesDto(remainingVo = 5, remainingPvo = 5)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, visitBalance)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")

    // visit for prisoner on same session has been cancelled
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      prisonerId = prisonerId,
      slotDate = reservedPublicApplication.sessionSlot.slotDate,
      visitStatus = VisitStatus.CANCELLED,
      prisonCode = reservedPublicApplication.prison.code,
    )

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, reservedPublicApplication.reference)

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when staff application is being booked on a cancelled visit on same session for same prisoner visit is booked successfully`() {
    // Given
    val visitBalance = VisitBalancesDto(remainingVo = 5, remainingPvo = 5)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, visitBalance)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")

    // visit for prisoner on same session has been cancelled
    visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      prisonerId = prisonerId,
      slotDate = reservedStaffApplication.sessionSlot.slotDate,
      visitStatus = VisitStatus.CANCELLED,
      prisonCode = reservedStaffApplication.prison.code,
    )

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, reservedStaffApplication.reference)

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when public application is being booked on an application on same session for same prisoner visit is booked successfully`() {
    // Given
    val visitBalance = VisitBalancesDto(remainingVo = 5, remainingPvo = 5)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, visitBalance)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")

    // application for same prisoner already exists
    applicationEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
      completed = false,
      userType = UserType.PUBLIC,
    )

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, reservedPublicApplication.reference)

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when staff application is being booked on an application on same session for same prisoner visit is booked successfully`() {
    // Given
    val visitBalance = VisitBalancesDto(remainingVo = 5, remainingPvo = 5)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, visitBalance)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")

    // application for same prisoner already exists
    applicationEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
      completed = false,
      userType = UserType.STAFF,
    )

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, reservedStaffApplication.reference)

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when application is being updated for the same date the prisoner visit is booked successfully`() {
    // Given
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")

    // visit for prisoner already exists
    var visit = visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      prisonerId = prisonerId,
      slotDate = startDate,
      visitStatus = VisitStatus.BOOKED,
    )
    visitEntityHelper.createContact(visit = visit, name = "Jane Doe", phone = "01234 098765")
    visit = visitEntityHelper.save(visit)

    var updateVisitApplication = applicationEntityHelper.create(visit)
    updateVisitApplication.completed = false
    updateVisitApplication.visitId = visit.id

    // change to only add a visitor
    applicationEntityHelper.createVisitor(application = updateVisitApplication, nomisPersonId = 331L, visitContact = false)
    updateVisitApplication = applicationEntityHelper.save(updateVisitApplication)

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, updateVisitApplication.reference)

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when application is being updated to a date where prisoners visit exist an exception is thrown`() {
    // Given
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")

    // 2 visits for prisoner already exists
    var visit1 = visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      prisonerId = prisonerId,
      slotDate = startDate,
      visitStatus = VisitStatus.BOOKED,
    )
    visitEntityHelper.createContact(visit = visit1, name = "Jane Doe", phone = "01234 098765")
    visit1 = visitEntityHelper.save(visit1)

    var visit2 = visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      prisonerId = prisonerId,
      slotDate = startDate.plusWeeks(1),
      visitStatus = VisitStatus.BOOKED,
    )
    visitEntityHelper.createContact(visit = visit2, name = "Jane Doe", phone = "01234 098765")
    visit2 = visitEntityHelper.save(visit2)

    // create application to update visit 1
    var updateVisitApplication = applicationEntityHelper.create(visit1)
    updateVisitApplication.visitId = visit1.id
    // update visit to change application slot to existing slot
    updateVisitApplication.sessionSlotId = visit2.sessionSlotId
    updateVisitApplication.completed = false
    updateVisitApplication = applicationEntityHelper.save(updateVisitApplication)

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, updateVisitApplication.reference)

    // Then
    responseSpec
      .expectStatus().isEqualTo(UNPROCESSABLE_ENTITY.code())

    val validationErrorResponse = getValidationErrorResponse(responseSpec)
    assertThat(validationErrorResponse.validationErrors.size).isEqualTo(1)
    assertThat(validationErrorResponse.validationErrors).contains(APPLICATION_INVALID_VISIT_ALREADY_BOOKED)
  }

  @Test
  fun `when public application has no pending VOs an exception is thrown`() {
    // Given
    val visitBalance = VisitBalancesDto(remainingVo = -2, remainingPvo = 0)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, visitBalance)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, reservedPublicApplication.reference)

    // Then
    responseSpec
      .expectStatus().isEqualTo(UNPROCESSABLE_ENTITY.code())

    val validationErrorResponse = getValidationErrorResponse(responseSpec)
    assertThat(validationErrorResponse.validationErrors.size).isEqualTo(1)
    assertThat(validationErrorResponse.validationErrors).contains(APPLICATION_INVALID_NO_VO_BALANCE)
  }

  @Test
  fun `when call to visit balances returns 404 a validation exception is thrown citing no available VOs`() {
    // Given
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    // call to visit balances returns 404
    prisonApiMockServer.stubGetVisitBalances(prisonerId, null)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, reservedPublicApplication.reference)

    // Then
    responseSpec
      .expectStatus().isEqualTo(UNPROCESSABLE_ENTITY.code())

    val validationErrorResponse = getValidationErrorResponse(responseSpec)
    assertThat(validationErrorResponse.validationErrors.size).isEqualTo(1)
    assertThat(validationErrorResponse.validationErrors).contains(APPLICATION_INVALID_NO_VO_BALANCE)
  }

  @Test
  fun `when public application has pending VOs visit is booked successfully`() {
    // Given
    val visitBalance = VisitBalancesDto(remainingVo = 5, remainingPvo = 0)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, visitBalance)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, reservedPublicApplication.reference)

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when public application has pending PVOs visit is booked successfully`() {
    // Given
    val visitBalance = VisitBalancesDto(remainingVo = 0, remainingPvo = 5)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, visitBalance)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, reservedPublicApplication.reference)

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session has no pending capacity an exception is thrown`() {
    // Given
    val visitBalance = VisitBalancesDto(remainingVo = 5, remainingPvo = 0)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, visitBalance)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")

    // sessionTemplate has open capacity of 1
    val sessionTemplate = sessionTemplateEntityHelper.create(openCapacity = 1, closedCapacity = 0)

    val application = applicationEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplate,
      completed = false,
      userType = UserType.PUBLIC,
    )

    // visit added and capacity filled
    visitEntityHelper.create(
      sessionTemplate = sessionTemplate,
      prisonerId = "QW1",
      slotDate = application.sessionSlot.slotDate,
      visitStatus = VisitStatus.BOOKED,
      prisonCode = application.prison.code,
    )

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, application.reference)

    // Then
    responseSpec
      .expectStatus().isEqualTo(UNPROCESSABLE_ENTITY.code())

    val validationErrorResponse = getValidationErrorResponse(responseSpec)
    assertThat(validationErrorResponse.validationErrors.size).isEqualTo(1)
    assertThat(validationErrorResponse.validationErrors).contains(APPLICATION_INVALID_NO_SLOT_CAPACITY)
  }

  @Test
  fun `when session has no pending capacity and no VO balance an exception is thrown with multiple error messages`() {
    // Given
    val visitBalance = VisitBalancesDto(remainingVo = 0, remainingPvo = 0)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, visitBalance)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")

    // sessionTemplate has open capacity of 1
    val sessionTemplate = sessionTemplateEntityHelper.create(openCapacity = 1, closedCapacity = 0)

    val application = applicationEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplate,
      completed = false,
      userType = UserType.PUBLIC,
    )

    // visit added and capacity filled
    visitEntityHelper.create(
      sessionTemplate = sessionTemplate,
      prisonerId = "QW1",
      slotDate = application.sessionSlot.slotDate,
      visitStatus = VisitStatus.BOOKED,
      prisonCode = application.prison.code,
    )

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, application.reference)

    // Then
    responseSpec
      .expectStatus().isEqualTo(UNPROCESSABLE_ENTITY.code())

    val validationErrorResponse = getValidationErrorResponse(responseSpec)
    assertThat(validationErrorResponse.validationErrors.size).isEqualTo(2)
    assertThat(validationErrorResponse.validationErrors).contains(APPLICATION_INVALID_NO_SLOT_CAPACITY)
    assertThat(validationErrorResponse.validationErrors).contains(APPLICATION_INVALID_NO_VO_BALANCE)
  }

  @Test
  fun `when session has pending open capacity visit is booked successfully`() {
    // Given
    val visitBalance = VisitBalancesDto(remainingVo = 5, remainingPvo = 0)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, visitBalance)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")

    // sessionTemplate has open capacity of 1
    val sessionTemplate = sessionTemplateEntityHelper.create(openCapacity = 2, closedCapacity = 0)

    val application = applicationEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplate,
      completed = false,
      userType = UserType.PUBLIC,
    )

    // visit added but capacity still there
    visitEntityHelper.create(
      sessionTemplate = sessionTemplate,
      prisonerId = "QW1",
      slotDate = application.sessionSlot.slotDate,
      visitStatus = VisitStatus.BOOKED,
      prisonCode = application.prison.code,
    )
    applicationEntityHelper.createContact(application = application, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = application, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.save(application)

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, application.reference)

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `when session has pending closed capacity visit is booked successfully`() {
    // Given
    val visitBalance = VisitBalancesDto(remainingVo = 5, remainingPvo = 0)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(prisonerId)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, visitBalance)
    prisonApiMockServer.stubGetPrisonerHousingLocation(prisonerId, "$prisonCode-C-1-C001")

    // sessionTemplate has open capacity of 1
    val sessionTemplate = sessionTemplateEntityHelper.create(openCapacity = 0, closedCapacity = 2)

    val application = applicationEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplate,
      completed = false,
      userType = UserType.PUBLIC,
      visitRestriction = VisitRestriction.CLOSED,
    )
    applicationEntityHelper.createContact(application = application, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = application, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.save(application)

    // visit added but capacity still there
    visitEntityHelper.create(
      sessionTemplate = sessionTemplate,
      prisonerId = "QW1",
      slotDate = application.sessionSlot.slotDate,
      visitStatus = VisitStatus.BOOKED,
      prisonCode = application.prison.code,
      visitRestriction = VisitRestriction.CLOSED,
    )

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, application.reference)

    // Then
    responseSpec.expectStatus().isOk
  }

  fun getValidationErrorResponse(responseSpec: WebTestClient.ResponseSpec): ApplicationValidationErrorResponse =
    objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, ApplicationValidationErrorResponse::class.java)
}
