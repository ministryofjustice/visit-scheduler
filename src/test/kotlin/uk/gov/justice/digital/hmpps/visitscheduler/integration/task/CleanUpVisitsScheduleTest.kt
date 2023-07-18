package uk.gov.justice.digital.hmpps.visitscheduler.integration.task

import com.microsoft.applicationinsights.TelemetryClient
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CHANGING
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents.VISIT_SLOT_RELEASED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.task.VisitTask
import java.time.LocalDateTime

@Transactional(propagation = SUPPORTS)
@DisplayName("Clean K")
class CleanUpVisitsScheduleTest : IntegrationTestBase() {

  @Autowired
  private lateinit var visitRepository: VisitRepository

  @Autowired
  private lateinit var testVisitRepository: TestVisitRepository

  @Autowired
  private lateinit var visitTask: VisitTask

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  private lateinit var reservedVisitNotExpired: Visit

  private lateinit var reservedVisitNotExpiredChangingStatus: Visit

  private lateinit var reservedVisitExpired: Visit

  private lateinit var reservedVisitExpiredChangingStatus: Visit

  @MockBean
  private lateinit var lockProvider: JdbcTemplateLockProvider

  @BeforeEach
  internal fun setUp() {
    reservedVisitNotExpired = createVisit(prisonerId = "NOT_EXPIRED")
    visitRepository.saveAndFlush(reservedVisitNotExpired)

    reservedVisitNotExpiredChangingStatus = visitRepository.saveAndFlush(createVisit(prisonerId = "NOT_EXPIRED", visitStatus = CHANGING))

    reservedVisitExpired = createVisit(prisonerId = "EXPIRED")
    visitRepository.saveAndFlush(reservedVisitExpired)
    visitRepository.updateModifyTimestamp(LocalDateTime.now().minusHours(2), reservedVisitExpired.id)

    reservedVisitExpiredChangingStatus = visitRepository.saveAndFlush(createVisit(prisonerId = "EXPIRED", visitStatus = CHANGING))
    visitRepository.updateModifyTimestamp(LocalDateTime.now().minusHours(2), reservedVisitExpiredChangingStatus.id)
  }

  @Test
  fun `delete only expired reserved applications`() {
    // Given
    val notExpiredApplicationReference = reservedVisitNotExpired.applicationReference
    val visitExpiredApplicationReference = reservedVisitExpired.applicationReference

    // When
    visitTask.deleteExpiredReservations()

    // Then
    assertThat(testVisitRepository.findByApplicationReference(notExpiredApplicationReference)).isNotNull
    assertThat(testVisitRepository.findByApplicationReference(visitExpiredApplicationReference)).isNull()

    assertDeleteEvent(reservedVisitExpired)
  }

  @Test
  fun `delete only expired changing applications`() {
    // Given
    val notExpiredApplicationReferenceChangingStatus = reservedVisitNotExpiredChangingStatus.applicationReference
    val visitExpiredApplicationReferenceChangingStatus = reservedVisitExpiredChangingStatus.applicationReference

    // When
    visitTask.deleteExpiredReservations()

    // Then
    assertThat(testVisitRepository.findByApplicationReference(notExpiredApplicationReferenceChangingStatus)).isNotNull
    assertThat(testVisitRepository.findByApplicationReference(visitExpiredApplicationReferenceChangingStatus)).isNull()

    assertDeleteEvent(reservedVisitExpiredChangingStatus)
  }

  private fun assertDeleteEvent(visit: Visit) {
    verify(telemetryClient, times(1)).trackEvent(
      eq(VISIT_SLOT_RELEASED_EVENT.eventName),
      org.mockito.kotlin.check {
        assertThat(it["prisonId"]).isEqualTo(visit.prison.code)
        assertThat(it["reference"]).isEqualTo(visit.reference)
        assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
        assertThat(it["visitRestriction"]).isEqualTo(visit.visitRestriction.name)
        assertThat(it["prisonerId"]).isEqualTo(visit.prisonerId)
        assertThat(it["applicationReference"]).isEqualTo(visit.applicationReference)
        // assertThat(it["visitStart"]).isEqualTo(visit.visitStart.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitType"]).isEqualTo(visit.visitType.name)
        assertThat(it["visitRoom"]).isEqualTo(visit.visitRoom)
      },
      isNull(),
    )
  }

  private fun createVisit(
    visitStatus: VisitStatus = RESERVED,
    prisonerId: String = "FF0000AA",
    prisonCode: String = "MDI",
    visitRoom: String = "A1",
    visitStart: LocalDateTime = LocalDateTime.now().minusDays(3),
    visitEnd: LocalDateTime = visitStart.plusHours(1),
    visitType: VisitType = VisitType.SOCIAL,
    visitRestriction: VisitRestriction = VisitRestriction.OPEN,
    reference: String = "",
  ): Visit {
    return visitEntityHelper.create(
      visitStatus = visitStatus,
      prisonerId = prisonerId,
      prisonCode = prisonCode,
      visitRoom = visitRoom,
      visitStart = visitStart,
      visitEnd = visitEnd,
      visitType = visitType,
      visitRestriction = visitRestriction,
      reference = reference,
    )
  }
}
