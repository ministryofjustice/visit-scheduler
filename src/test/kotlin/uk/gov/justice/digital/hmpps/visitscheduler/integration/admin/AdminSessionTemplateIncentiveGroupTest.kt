package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.INCENTIVE_GROUP_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel.BASIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel.ENHANCED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel.ENHANCED_2
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel.ENHANCED_3
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCreateIncentiveSessionGroupByReference
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callDeleteIncentiveGroupByReference
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGetIncentiveGroupByReference
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGetIncentiveGroupsByPrisonId
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callUpdateIncentiveGroupByReference
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createIncentiveGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.updateIncentiveGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.SessionIncentiveLevelGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.SessionPrisonerIncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionIncentiveLevelGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionIncentiveLevelGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionPrisonerIncentiveLevelRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionTemplateRepository

@DisplayName("Get/Delete $INCENTIVE_GROUP_ADMIN_PATH")
class AdminSessionTemplateIncentiveGroupTest(
  @Autowired val testTemplateRepository: TestSessionTemplateRepository,
  @Autowired val sessionIncentiveGroupRepository: SessionIncentiveLevelGroupRepository,
  @Autowired val testSessionIncentiveGroupRepository: TestSessionIncentiveLevelGroupRepository,
  @Autowired val testSessionIncentiveRepository: TestSessionPrisonerIncentiveLevelRepository,
) : IntegrationTestBase() {

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  private lateinit var sessionTemplateWithGrps: SessionTemplate
  private lateinit var incentiveGroup: SessionIncentiveLevelGroup
  private lateinit var incentiveGroupWithNoSessionTemplate: SessionIncentiveLevelGroup

  @BeforeEach
  internal fun setUpTests() {
    sessionTemplateWithGrps = sessionTemplateEntityHelper.create(validFromDate = java.time.LocalDate.now())

    prison = prisonEntityHelper.create()

    incentiveGroup = sessionIncentiveGroupRepository.saveAndFlush(
      SessionIncentiveLevelGroup(
        prison = prison,
        prisonId = prison.id,
        name = "test 1",
      ),
    )

    incentiveGroup.sessionIncentiveLevels.add(
      testSessionIncentiveRepository.saveAndFlush(
        SessionPrisonerIncentiveLevel(
          sessionIncentiveGroupId = incentiveGroup.id,
          sessionIncentiveLevelGroup = incentiveGroup,
          prisonerIncentiveLevel = ENHANCED,
        ),
      ),
    )

    incentiveGroup.sessionTemplates.add(sessionTemplateWithGrps)
    sessionTemplateWithGrps.permittedSessionIncentiveLevelGroups.add(incentiveGroup)
    sessionTemplateWithGrps = testTemplateRepository.saveAndFlush(sessionTemplateWithGrps)

    incentiveGroupWithNoSessionTemplate = sessionIncentiveGroupRepository.saveAndFlush(
      SessionIncentiveLevelGroup(
        prison = prison,
        prisonId = prison.id,
        name = "test 2",
      ),
    )

    incentiveGroupWithNoSessionTemplate.sessionIncentiveLevels.add(
      testSessionIncentiveRepository.saveAndFlush(
        SessionPrisonerIncentiveLevel(
          sessionIncentiveGroupId = incentiveGroupWithNoSessionTemplate.id,
          sessionIncentiveLevelGroup = incentiveGroup,
          prisonerIncentiveLevel = ENHANCED_3,
        ),
      ),
    )
  }

  @Test
  fun `get session incentive groups by prison id test`() {
    // Given
    val prisonCode = prison.code
    // When
    val responseSpec = callGetIncentiveGroupsByPrisonId(webTestClient, prisonCode, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val groups = getSessionIncentiveGroups(responseSpec)
    Assertions.assertThat(groups).hasSize(2)
    with(groups[0]) {
      Assertions.assertThat(name).isEqualTo(incentiveGroup.name)
      Assertions.assertThat(reference).isEqualTo(incentiveGroup.reference)
      Assertions.assertThat(incentiveLevels).hasSize(1)
      Assertions.assertThat(incentiveLevels[0]).isEqualTo(ENHANCED)
    }
    with(groups[1]) {
      Assertions.assertThat(name).isEqualTo(incentiveGroupWithNoSessionTemplate.name)
      Assertions.assertThat(reference).isEqualTo(incentiveGroupWithNoSessionTemplate.reference)
      Assertions.assertThat(incentiveLevels).hasSize(1)
      Assertions.assertThat(incentiveLevels[0]).isEqualTo(ENHANCED_3)
    }
  }

  @Test
  fun `get session group by reference test`() {
    // Given
    val reference = incentiveGroup.reference
    // When
    val responseSpec = callGetIncentiveGroupByReference(webTestClient, reference, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val group = getSessionIncentiveGroup(responseSpec)
    with(group) {
      Assertions.assertThat(name).isEqualTo(incentiveGroup.name)
      Assertions.assertThat(reference).isEqualTo(incentiveGroup.reference)
      Assertions.assertThat(incentiveLevels.size).isEqualTo(1)
      Assertions.assertThat(incentiveLevels[0]).isEqualTo(ENHANCED)
    }
  }

  @Test
  fun `create create incentive group without duplicate types test`() {
    // Given
    val incentiveGroup = createIncentiveGroupDto("test Updated", prisonCode = prison.code, ENHANCED_3, ENHANCED, ENHANCED_3)

    // When
    val responseSpec = callCreateIncentiveSessionGroupByReference(webTestClient, incentiveGroup, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val group = getSessionIncentiveGroup(responseSpec)
    with(group) {
      Assertions.assertThat(name).isEqualTo(incentiveGroup.name)
      Assertions.assertThat(reference).isNotNull
      Assertions.assertThat(incentiveLevels.size).isEqualTo(2)
      Assertions.assertThat(incentiveLevels[0]).isEqualTo(ENHANCED_3)
      Assertions.assertThat(incentiveLevels[1]).isEqualTo(ENHANCED)
    }

    // also check against the database post fix for VB-2458
    val sessionIncentiveGroupEntity = testSessionIncentiveGroupRepository.findByReference(group.reference)
    Assertions.assertThat(sessionIncentiveGroupEntity).isNotNull
    val sessionIncentiveLevels = testSessionIncentiveGroupRepository.findSessionIncentiveLevelsByGroup(sessionIncentiveGroupEntity!!)
    Assertions.assertThat(sessionIncentiveLevels?.size).isEqualTo(2)
    Assertions.assertThat(sessionIncentiveLevels!![0].prisonerIncentiveLevel).isEqualTo(ENHANCED_3)
    Assertions.assertThat(sessionIncentiveLevels[1].prisonerIncentiveLevel).isEqualTo(ENHANCED)
  }

  @Test
  fun `update create incentive group without duplicate types test`() {
    // Given
    val updateGroup = updateIncentiveGroupDto("test Updated", BASIC, ENHANCED_2, ENHANCED_2)

    // When
    val responseSpec = callUpdateIncentiveGroupByReference(webTestClient, incentiveGroup.reference, updateGroup, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val group = getSessionIncentiveGroup(responseSpec)
    with(group) {
      Assertions.assertThat(name).isEqualTo(updateGroup.name)
      Assertions.assertThat(reference).isNotNull
      Assertions.assertThat(incentiveLevels.size).isEqualTo(2)
      Assertions.assertThat(incentiveLevels[0]).isEqualTo(BASIC)
      Assertions.assertThat(incentiveLevels[1]).isEqualTo(ENHANCED_2)
    }
  }

  @Test
  fun `delete session group by reference test`() {
    // Given
    val reference = incentiveGroupWithNoSessionTemplate.reference

    // When
    val responseSpec = callDeleteIncentiveGroupByReference(webTestClient, reference, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
    responseSpec.expectBody()
      .jsonPath("$").isEqualTo("Session incentive group Deleted $reference!")

    Assertions.assertThat(testSessionIncentiveGroupRepository.hasById(sessionTemplateWithGrps.id)).isFalse
  }

  @Test
  fun `delete session group when session template uses the group exception is thrown`() {
    // Given
    val reference = incentiveGroup.reference

    // When
    val responseSpec = callDeleteIncentiveGroupByReference(webTestClient, reference, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").isEqualTo("Incentive group cannot be deleted $reference because session templates are using it!")
  }
}
