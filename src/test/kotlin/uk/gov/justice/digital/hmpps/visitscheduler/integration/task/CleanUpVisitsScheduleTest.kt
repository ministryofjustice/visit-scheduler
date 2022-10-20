package uk.gov.justice.digital.hmpps.visitscheduler.integration.task

import com.microsoft.applicationinsights.TelemetryClient
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
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
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.task.VisitTask
import java.time.LocalDateTime

@Transactional(propagation = SUPPORTS)
@DisplayName("Clean K")
class CleanUpVisitsScheduleTest : IntegrationTestBase() {

  @Autowired
  private lateinit var visitRepository: VisitRepository

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

    reservedVisitNotExpiredChangingStatus = createVisit(prisonerId = "NOT_EXPIRED", visitStatus = CHANGING)
    visitRepository.saveAndFlush(reservedVisitNotExpiredChangingStatus)

    reservedVisitExpired = createVisit(prisonerId = "EXPIRED")
    visitRepository.saveAndFlush(reservedVisitExpired)
    visitRepository.updateModifyTimestamp(LocalDateTime.now().minusHours(2), reservedVisitExpired.id)

    reservedVisitExpiredChangingStatus = createVisit(prisonerId = "EXPIRED", visitStatus = CHANGING)
    visitRepository.saveAndFlush(reservedVisitExpiredChangingStatus)
    visitRepository.updateModifyTimestamp(LocalDateTime.now().minusHours(2), reservedVisitExpiredChangingStatus.id)
  }

  @AfterEach
  internal fun deleteAllVisits() = visitEntityHelper.deleteAll()

  @Test
  fun `delete only expired reservations`() {

    // Given
    val notExpiredApplicationReference = reservedVisitNotExpired.applicationReference
    val notExpiredApplicationReferenceChangingStatus = reservedVisitNotExpiredChangingStatus.applicationReference
    val visitExpiredApplicationReference = reservedVisitExpired.applicationReference
    val visitExpiredApplicationReferenceChangingStatus = reservedVisitExpiredChangingStatus.applicationReference

    // When
    visitTask.deleteExpiredReservations()

    // Then
    assertThat(visitRepository.findByApplicationReference(notExpiredApplicationReference)).isNotNull
    assertThat(visitRepository.findByApplicationReference(notExpiredApplicationReferenceChangingStatus)).isNotNull
    assertThat(visitRepository.findByApplicationReference(visitExpiredApplicationReference)).isNull()
    assertThat(visitRepository.findByApplicationReference(visitExpiredApplicationReferenceChangingStatus)).isNull()

    verify(telemetryClient, times(1)).trackEvent(eq("visit-expired-visits-deleted"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("visit-expired-visits-deleted"),
      check {
        assertThat(it["applicationReferences"]).contains(visitExpiredApplicationReference)
        assertThat(it["applicationReferences"]).contains(visitExpiredApplicationReferenceChangingStatus)
      },
      isNull()
    )
  }

  private fun createVisit(
    visitStatus: VisitStatus = RESERVED,
    prisonerId: String = "FF0000AA",
    prisonId: String = "MDI",
    visitRoom: String = "A1",
    visitStart: LocalDateTime = LocalDateTime.now().minusDays(3),
    visitEnd: LocalDateTime = visitStart.plusHours(1),
    visitType: VisitType = VisitType.SOCIAL,
    visitRestriction: VisitRestriction = VisitRestriction.OPEN,
    reference: String = ""
  ): Visit {

    return visitRepository.saveAndFlush(
      Visit(
        visitStatus = visitStatus,
        prisonerId = prisonerId,
        prisonId = prisonId,
        visitRoom = visitRoom,
        visitStart = visitStart,
        visitEnd = visitEnd,
        visitType = visitType,
        visitRestriction = visitRestriction,
        _reference = reference
      )
    )
  }
}
