package uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitCreator
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
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
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val visitTime: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30)
    val testPrisonerId: String = "AA12345F"
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
          VisitVisitorPk(contactId = 123L, visitId = visitList[0].id),
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
      assertThat(this.prisonId).isEqualTo("MDI")
      assertThat(this.visitors).hasSize(1)
      assertThat(this.visitors[0].leadVisitor).isFalse()
      assertThat(this.visitors[0].id.contactId).isEqualTo(123L)
      assertThat(this.visitors[0].id.visitId).isEqualTo(visitList[0].id)
    }
  }
}
