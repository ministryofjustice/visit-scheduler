package uk.gov.justice.digital.hmpps.visitscheduler.integration

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestApplicationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionLocationGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitRepository
import java.time.LocalDate

@DisplayName("Data base test")
open class DataBaseTest(
  @Autowired val testVisitRepository: TestVisitRepository,
  @Autowired val testSessionLocationGroupRepository: TestSessionLocationGroupRepository,
  @Autowired val testTemplateRepository: TestSessionTemplateRepository,
  @Autowired val testApplicationRepository: TestApplicationRepository,
) : IntegrationTestBase() {

  private lateinit var inCompleteApplication: Application
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit
  private lateinit var visitWithApplication: Visit
  private lateinit var applicationWithVisit: Application

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    visitWithApplication = visitEntityHelper.create(sessionTemplate = sessionTemplateDefault)

    visitEntityHelper.createNote(visit = visitWithApplication, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitEntityHelper.createNote(visit = visitWithApplication, text = "Some text concerns", type = VISITOR_CONCERN)
    visitEntityHelper.createNote(visit = visitWithApplication, text = "Some text comment", type = VISIT_COMMENT)
    visitEntityHelper.createContact(visit = visitWithApplication, name = "Jane Doe", phone = "01234 098765")
    visitEntityHelper.createVisitor(visit = visitWithApplication, nomisPersonId = 321L, visitContact = true)
    visitEntityHelper.createSupport(visit = visitWithApplication, description = "Some Text")
    visitWithApplication = visitEntityHelper.save(visitWithApplication)

    applicationWithVisit = applicationEntityHelper.create(slotDate = startDate, sessionTemplate = sessionTemplateDefault, reservedSlot = true, completed = true)
    applicationEntityHelper.createContact(application = applicationWithVisit, name = "Jane Doe", phone = "01234 098765", email = "example@email.com")
    applicationEntityHelper.createVisitor(application = applicationWithVisit, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = applicationWithVisit, description = "Some Text")
    applicationEntityHelper.save(applicationWithVisit)

    visitWithApplication.addApplication(applicationWithVisit)

    sessionTemplateDefault = sessionTemplateEntityHelper.create(validFromDate = LocalDate.now())
    val allowedPermittedLocations1 = listOf(AllowedSessionLocationHierarchy("A", "1", "001"))
    val sessionGroup1 = sessionLocationGroupHelper.create(prisonCode = sessionTemplateDefault.prison.code, prisonHierarchies = allowedPermittedLocations1)
    val allowedPermittedLocations2 = listOf(AllowedSessionLocationHierarchy("B"))
    val sessionGroup2 = sessionLocationGroupHelper.create(prisonCode = sessionTemplateDefault.prison.code, name = "get 2", prisonHierarchies = allowedPermittedLocations2)
    sessionTemplateDefault.permittedSessionLocationGroups.add(sessionGroup1)
    sessionTemplateDefault.permittedSessionLocationGroups.add(sessionGroup2)
    sessionTemplateDefault = testTemplateRepository.saveAndFlush(sessionTemplateDefault)

    inCompleteApplication = applicationEntityHelper.create(slotDate = startDate, sessionTemplate = sessionTemplateDefault, reservedSlot = true, completed = false)
    applicationEntityHelper.createContact(application = inCompleteApplication, name = "Jane Doe", phone = "01234 098765", email = "example@email.com")
    applicationEntityHelper.createVisitor(application = inCompleteApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = inCompleteApplication, description = "Some Text")
    applicationEntityHelper.save(inCompleteApplication)
  }

  @Transactional(propagation = REQUIRES_NEW)
  @Test
  open fun `When visit deleted - all connected child objects are also removed`() {
    val didExist = testVisitRepository.hasVisit(visitWithApplication.id)
    val didApplicationExist = testApplicationRepository.hasApplication(applicationWithVisit.id)

    // When

    val results = testVisitRepository.deleteByReference(visitWithApplication.reference)

    // Then
    Assertions.assertThat(results).isEqualTo(1)
    Assertions.assertThat(didExist).isTrue
    Assertions.assertThat(didApplicationExist).isTrue
    Assertions.assertThat(testVisitRepository.hasVisit(visitWithApplication.id)).isFalse()
    Assertions.assertThat(testVisitRepository.hasContact(visitWithApplication.id)).isFalse
    Assertions.assertThat(testVisitRepository.hasNotes(visitWithApplication.id)).isFalse
    Assertions.assertThat(testVisitRepository.hasVisitors(visitWithApplication.id)).isFalse
    Assertions.assertThat(testVisitRepository.hasSupport(visitWithApplication.id)).isFalse
    Assertions.assertThat(testApplicationRepository.hasApplication(visitWithApplication.getLastApplication()!!.id)).isFalse
  }

  @Test
  fun `When in complete application is deleted - all connected child objects are also removed`() {
    val didApplicationExist = testApplicationRepository.hasApplication(inCompleteApplication.id)
    val applicationId = inCompleteApplication.id
    val applicationRef = inCompleteApplication.reference

    // When
    val results = testApplicationRepository.deleteByReference(applicationRef)

    // Then
    Assertions.assertThat(results).isEqualTo(1)
    Assertions.assertThat(didApplicationExist).isTrue
    Assertions.assertThat(testApplicationRepository.hasApplication(applicationId)).isFalse
    Assertions.assertThat(testApplicationRepository.hasContact(applicationId)).isFalse
    Assertions.assertThat(testApplicationRepository.hasVisitors(applicationId)).isFalse
    Assertions.assertThat(testApplicationRepository.hasSupport(applicationId)).isFalse
  }

  @Test()
  fun `When a complete application is deleted the visit is not`() {
    val applicationId = applicationWithVisit.id
    val applicationRef = applicationWithVisit.reference

    // When
    val results = testApplicationRepository.deleteByReference(applicationRef)

    // Then
    Assertions.assertThat(results).isEqualTo(1)
    Assertions.assertThat(testApplicationRepository.hasApplication(applicationId)).isFalse
    Assertions.assertThat(testVisitRepository.findById(visitWithApplication.id).isPresent).isTrue()
  }

  @Transactional(propagation = REQUIRES_NEW)
  @Test
  open fun `When sessionTemplate deleted - location groups are not deleted but join is`() {
    // Given
    val reference = sessionTemplateDefault.reference
    val sessionId = sessionTemplateDefault.id
    val grp1Id = sessionTemplateDefault.permittedSessionLocationGroups[0].id
    val grp2Id = sessionTemplateDefault.permittedSessionLocationGroups[1].id

    // When
    val result = testTemplateRepository.deleteByReference(reference)

    // Then
    Assertions.assertThat(result).isEqualTo(1)
    Assertions.assertThat(testSessionLocationGroupRepository.hasById(grp1Id)).isTrue
    Assertions.assertThat(testSessionLocationGroupRepository.hasById(grp2Id)).isTrue
    Assertions.assertThat(testSessionLocationGroupRepository.hasJoinTable(sessionId, grp1Id)).isFalse
    Assertions.assertThat(testSessionLocationGroupRepository.hasJoinTable(sessionId, grp2Id)).isFalse
  }

  @Test
  fun `test constraint on session template slot, with all values then exception is thrown`() {
    // Given
    val slotDate = LocalDate.now()
    testSessionSlotRepository.save(SessionSlot("ref", 1, slotDate, slotDate.atTime(1, 30), slotDate.atTime(2, 30)))

    // When
    assertThrows<DataIntegrityViolationException> {
      testSessionSlotRepository.save(SessionSlot("ref", 1, slotDate, slotDate.atTime(1, 30), slotDate.atTime(2, 30)))
    }
  }

  @Test
  fun `test constraint on session template, with no session template reference then exception is thrown`() {
    // Given
    val slotDate = LocalDate.now()
    testSessionSlotRepository.save(SessionSlot(null, 1, slotDate, slotDate.atTime(1, 30), slotDate.atTime(2, 30)))

    // When
    assertThrows<DataIntegrityViolationException> {
      testSessionSlotRepository.save(SessionSlot(null, 1, slotDate, slotDate.atTime(1, 30), slotDate.atTime(2, 30)))
    }
  }
}
