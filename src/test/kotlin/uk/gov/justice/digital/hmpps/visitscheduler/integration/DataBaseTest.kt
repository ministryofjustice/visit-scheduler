package uk.gov.justice.digital.hmpps.visitscheduler.integration

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestApplicationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionLocationGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitRepository
import java.time.LocalDate

@DisplayName("Data base test")
class DataBaseTest(
  @Autowired val testVisitRepository: TestVisitRepository,
  @Autowired val testSessionLocationGroupRepository: TestSessionLocationGroupRepository,
  @Autowired val testTemplateRepository: TestSessionTemplateRepository,
  @Autowired val testApplicationRepository: TestApplicationRepository,
) : IntegrationTestBase() {

  private lateinit var inCompleteApplication: Application
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit
  private lateinit var visit: Visit
  private lateinit var application: Application

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    visit = visitEntityHelper.create(sessionTemplate = sessionTemplate)

    visitEntityHelper.createNote(visit = visit, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitEntityHelper.createNote(visit = visit, text = "Some text concerns", type = VISITOR_CONCERN)
    visitEntityHelper.createNote(visit = visit, text = "Some text comment", type = VISIT_COMMENT)
    visitEntityHelper.createContact(visit = visit, name = "Jane Doe", phone = "01234 098765")
    visitEntityHelper.createVisitor(visit = visit, nomisPersonId = 321L, visitContact = true)
    visitEntityHelper.createSupport(visit = visit, name = "OTHER", details = "Some Text")
    visitEntityHelper.save(visit)

    sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = LocalDate.now())
    val allowedPermittedLocations1 = listOf(AllowedSessionLocationHierarchy("A", "1", "001"))
    val sessionGroup1 = sessionLocationGroupHelper.create(prisonCode = sessionTemplate.prison.code, prisonHierarchies = allowedPermittedLocations1)
    val allowedPermittedLocations2 = listOf(AllowedSessionLocationHierarchy("B"))
    val sessionGroup2 = sessionLocationGroupHelper.create(prisonCode = sessionTemplate.prison.code, name = "get 2", prisonHierarchies = allowedPermittedLocations2)
    sessionTemplate.permittedSessionLocationGroups.add(sessionGroup1)
    sessionTemplate.permittedSessionLocationGroups.add(sessionGroup2)
    sessionTemplate = testTemplateRepository.saveAndFlush(sessionTemplate)

    application = applicationEntityHelper.create(slotDate = startDate, sessionTemplate = sessionTemplate, reservedSlot = true)
    applicationEntityHelper.createContact(application = application, name = "Jane Doe", phone = "01234 098765")
    applicationEntityHelper.createVisitor(application = application, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = application, name = "OTHER", details = "Some Text")
    applicationEntityHelper.save(application)

    visit.applications.add(application)
    visitEntityHelper.save(visit)

    inCompleteApplication = applicationEntityHelper.create(slotDate = startDate, sessionTemplate = sessionTemplate, reservedSlot = true, completed = false)
    applicationEntityHelper.createContact(application = inCompleteApplication, name = "Jane Doe", phone = "01234 098765")
    applicationEntityHelper.createVisitor(application = inCompleteApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = inCompleteApplication, name = "OTHER", details = "Some Text")
    applicationEntityHelper.save(inCompleteApplication)
  }

  @Transactional(propagation = REQUIRES_NEW)
  @Test
  fun `When visit deleted - all connected child objects are also removed`() {
    val didExist = testVisitRepository.hasVisit(visit.id)
    val didApplicationExist = testApplicationRepository.hasApplication(application.id)

    // When

    val results = testVisitRepository.deleteByReference(visit.reference)

    // Then
    Assertions.assertThat(results).isEqualTo(1)
    Assertions.assertThat(didExist).isTrue
    Assertions.assertThat(didApplicationExist).isTrue
    Assertions.assertThat(testVisitRepository.hasVisit(visit.id)).isFalse()
    Assertions.assertThat(testVisitRepository.hasContact(visit.id)).isFalse
    Assertions.assertThat(testVisitRepository.hasNotes(visit.id)).isFalse
    Assertions.assertThat(testVisitRepository.hasVisitors(visit.id)).isFalse
    Assertions.assertThat(testVisitRepository.hasSupport(visit.id)).isFalse
    Assertions.assertThat(testApplicationRepository.findByReference(visit.applications.last.reference)).isNull()
  }

  @Test
  fun `When in complete application is deleted - all connected child objects are also removed but not connected visit`() {
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
    Assertions.assertThat(testVisitRepository.findById(visit.id).isPresent).isTrue()
  }

  @Test()
  fun `When application deleted with visit - an exception is thrown`() {
    val applicationRef = application.reference

    // When Then
    val exception = assertThrows<Throwable> {
      testApplicationRepository.deleteByReference(applicationRef)
    }

    Assertions.assertThat(exception.message).contains("violates foreign key constraint \"application_must_exist\"")
  }

  @Transactional(propagation = REQUIRES_NEW)
  @Test
  fun `When sessionTemplate deleted - location groups are not deleted but join is`() {
    // Given
    val reference = sessionTemplate.reference
    val sessionId = sessionTemplate.id
    val grp1Id = sessionTemplate.permittedSessionLocationGroups[0].id
    val grp2Id = sessionTemplate.permittedSessionLocationGroups[1].id

    // When
    val result = testTemplateRepository.deleteByReference(reference)

    // Then
    Assertions.assertThat(result).isEqualTo(1)
    Assertions.assertThat(testSessionLocationGroupRepository.hasById(grp1Id)).isTrue
    Assertions.assertThat(testSessionLocationGroupRepository.hasById(grp2Id)).isTrue
    Assertions.assertThat(testSessionLocationGroupRepository.hasJoinTable(sessionId, grp1Id)).isFalse
    Assertions.assertThat(testSessionLocationGroupRepository.hasJoinTable(sessionId, grp2Id)).isFalse
  }
}
