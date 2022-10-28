package uk.gov.justice.digital.hmpps.visitscheduler.integration

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.transaction.annotation.Propagation.REQUIRED
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitRepository

@Transactional(propagation = REQUIRED)
@DisplayName("Data base test")
class DataBaseTest() : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  private lateinit var testVisitRepository: TestVisitRepository

  private lateinit var reservedVisit: Visit

  @BeforeEach
  internal fun setUp() {

    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    reservedVisit = visitEntityHelper.create()

    visitEntityHelper.createNote(visit = reservedVisit, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitEntityHelper.createNote(visit = reservedVisit, text = "Some text concerns", type = VISITOR_CONCERN)
    visitEntityHelper.createNote(visit = reservedVisit, text = "Some text comment", type = VISIT_COMMENT)
    visitEntityHelper.createContact(visit = reservedVisit, name = "Jane Doe", phone = "01234 098765")
    visitEntityHelper.createVisitor(visit = reservedVisit, nomisPersonId = 321L, visitContact = true)
    visitEntityHelper.createSupport(visit = reservedVisit, name = "OTHER", details = "Some Text")
    visitEntityHelper.save(reservedVisit)
  }

  @Test
  fun `When visit deleted - all connected child objects are also removed`() {

    // Given
    val applicationReference = reservedVisit.applicationReference

    // When
    val result = testVisitRepository.deleteByApplicationReference(applicationReference)

    // Then
    Assertions.assertThat(result).isEqualTo(1)
    Assertions.assertThat(testVisitRepository.hasContact(reservedVisit.id)).isFalse()
    Assertions.assertThat(testVisitRepository.hasNotes(reservedVisit.id)).isFalse()
    Assertions.assertThat(testVisitRepository.hasVisitors(reservedVisit.id)).isFalse()
    Assertions.assertThat(testVisitRepository.hasSupport(reservedVisit.id)).isFalse()
  }
}
