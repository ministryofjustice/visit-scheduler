package uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitCreator
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitContact
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitVisitorPk
import java.time.LocalDateTime

// may need EntityManager injecting to clear session if read not hitting DB due to cache
@Transactional
class VisitRepositoryTest : IntegrationTestBase() {
  @Autowired
  lateinit var repository: VisitRepository

  companion object {
    val visitTime: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30)
    const val testPrisonerId: String = "AA12345F"
    const val testPrison: String = "MDI"
    const val testContactName: String = "Joe Blogs"
    const val testContactPhone: String = "01234 567890"
  }

  @Test
  internal fun `can write and read a visit`() {
    assertThat(repository.findByPrisonerId(testPrisonerId)).isEmpty()

    visitCreator(repository)
      .withPrisonerId(testPrisonerId)
      .withVisitStart(visitTime)
      .save()

    val visitList = repository.findByPrisonerId(testPrisonerId).toMutableList()

    visitList[0] = visitList[0].copy(
      visitors = mutableListOf(
        VisitVisitor(
          VisitVisitorPk(nomisPersonId = 123L, visitId = visitList[0].id),
          leadVisitor = false,
          visit = visitList[0]
        )
      )
    )

    repository.saveAndFlush(visitList[0])

    assertThat(visitList.size).isEqualTo(1)
    with(visitList[0]) {
      assertThat(this.prisonerId).isEqualTo(testPrisonerId)
      assertThat(this.id).isNotNull.isGreaterThan(0L)
      assertThat(this.visitStart).isEqualTo(visitTime)
      assertThat(this.visitType).isEqualTo(VisitType.STANDARD_SOCIAL)
      assertThat(this.status).isEqualTo(VisitStatus.RESERVED)
      assertThat(this.visitStart).isEqualTo(visitTime)
      assertThat(this.prisonId).isEqualTo(testPrison)
      assertThat(this.visitors).hasSize(1)
      assertThat(this.visitors[0].leadVisitor).isFalse
      assertThat(this.visitors[0].id.nomisPersonId).isEqualTo(123L)
      assertThat(this.visitors[0].id.visitId).isEqualTo(visitList[0].id)
    }
  }

  @Test
  internal fun `can write and read a visit with main contact`() {
    assertThat(repository.findByPrisonerId(testPrisonerId)).isEmpty()

    visitCreator(repository)
      .withPrisonId(testPrison)
      .withPrisonerId(testPrisonerId)
      .save()

    val visitSavedList = repository.findByPrisonerId(testPrisonerId)
    visitSavedList[0].mainContact = VisitContact(
      id = visitSavedList[0].id,
      contactName = testContactName,
      contactPhone = testContactPhone,
      visit = visitSavedList[0]
    )
    repository.saveAndFlush(visitSavedList[0])

    val visits = repository.findByPrisonerId(testPrisonerId)
    assertThat(visits.size).isEqualTo(1)
    with(visits[0]) {
      assertThat(this.id).isNotNull.isGreaterThan(0L)
      assertThat(this.prisonId).isEqualTo(testPrison)
      assertThat(this.prisonerId).isEqualTo(testPrisonerId)
      assertThat(this.mainContact).isNotNull
      assertThat(this.mainContact!!.contactName).isEqualTo(testContactName)
      assertThat(this.mainContact!!.contactPhone).isEqualTo(testContactPhone)
    }
  }
}
