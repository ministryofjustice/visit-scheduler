package uk.gov.justice.digital.hmpps.visitscheduler.integration

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestApplicationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionLocationGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitRepository
import java.time.LocalDate

@DisplayName("Data base test")
@Transactional(propagation = NOT_SUPPORTED)
class DataBaseTest(
  @Autowired val testVisitRepository: TestVisitRepository,
  @Autowired val testSessionLocationGroupRepository: TestSessionLocationGroupRepository,
  @Autowired val testTemplateRepository: TestSessionTemplateRepository,
  @Autowired val testApplicationRepository: TestApplicationRepository,
) : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit
  private lateinit var reservedVisit: Visit

  @Transactional(propagation = REQUIRES_NEW)
  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    reservedVisit = visitEntityHelper.create(sessionTemplate = sessionTemplate, createApplication = true)

    visitEntityHelper.createNote(visit = reservedVisit, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitEntityHelper.createNote(visit = reservedVisit, text = "Some text concerns", type = VISITOR_CONCERN)
    visitEntityHelper.createNote(visit = reservedVisit, text = "Some text comment", type = VISIT_COMMENT)
    visitEntityHelper.createContact(visit = reservedVisit, name = "Jane Doe", phone = "01234 098765")
    visitEntityHelper.createVisitor(visit = reservedVisit, nomisPersonId = 321L, visitContact = true)
    visitEntityHelper.createSupport(visit = reservedVisit, name = "OTHER", details = "Some Text")
    visitEntityHelper.save(reservedVisit)

    sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = LocalDate.now())
    val allowedPermittedLocations1 = listOf(AllowedSessionLocationHierarchy("A", "1", "001"))
    val sessionGroup1 = sessionLocationGroupHelper.create(prisonCode = sessionTemplate.prison.code, prisonHierarchies = allowedPermittedLocations1)
    val allowedPermittedLocations2 = listOf(AllowedSessionLocationHierarchy("B"))
    val sessionGroup2 = sessionLocationGroupHelper.create(prisonCode = sessionTemplate.prison.code, name = "get 2", prisonHierarchies = allowedPermittedLocations2)
    sessionTemplate.permittedSessionLocationGroups.add(sessionGroup1)
    sessionTemplate.permittedSessionLocationGroups.add(sessionGroup2)
    sessionTemplate = testTemplateRepository.saveAndFlush(sessionTemplate)
  }

  @Transactional(propagation = REQUIRES_NEW)
  @Test
  fun `When visit deleted - all connected child objects are also removed`() {

    // When
    val didExist = testVisitRepository.hasVisit(reservedVisit.id)

    testVisitRepository.delete(reservedVisit)

    // Then
    Assertions.assertThat(didExist).isTrue
    Assertions.assertThat(testVisitRepository.hasVisit(reservedVisit.id)).isFalse()
    Assertions.assertThat(testVisitRepository.hasContact(reservedVisit.id)).isFalse
    Assertions.assertThat(testVisitRepository.hasNotes(reservedVisit.id)).isFalse
    Assertions.assertThat(testVisitRepository.hasVisitors(reservedVisit.id)).isFalse
    Assertions.assertThat(testVisitRepository.hasSupport(reservedVisit.id)).isFalse
    Assertions.assertThat(testApplicationRepository.findByReference(reservedVisit.applications.last.reference)).isNull()
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
