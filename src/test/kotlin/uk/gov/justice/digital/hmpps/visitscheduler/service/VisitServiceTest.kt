package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.ValidationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateVisitFromExternalSystemDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdateVisitFromExternalSystemDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.utils.diff.UpdateVisitSummaryUtil

@ExtendWith(MockitoExtension::class)
class VisitServiceTest {
  private val visitRepository = mock<VisitRepository>()
  private val visitStoreService = mock<VisitStoreService>()
  private val telemetryClientService = mock<TelemetryClientService>()
  private val eventAuditService = mock<VisitEventAuditService>()
  private val snsService = mock<SnsService>()
  private val updateVisitSummaryUtil = mock<UpdateVisitSummaryUtil>()
  private val visitNotificationTransactionalService = mock<VisitNotificationTransactionalService>()
  private val visitService = VisitService(
    visitRepository,
    visitStoreService,
    telemetryClientService,
    eventAuditService,
    snsService,
    updateVisitSummaryUtil,
    visitNotificationTransactionalService,
  )

  @ParameterizedTest
  @EnumSource(value = UserType::class, names = ["STAFF", "PUBLIC"])
  fun `External creation rejects missing or blank actor identity before any side effects`(userType: UserType) {
    val request = mock<CreateVisitFromExternalSystemDto>()
    whenever(request.userType).thenReturn(userType)
    listOf(null, "", " ", "\t\n").forEach { identity ->
      whenever(request.actionedBy).thenReturn(identity)

      val exception = assertThrows<ValidationException> { visitService.createVisitFromExternalSystem(request) }
      assertThat(exception.message).isEqualTo("actionedBy must be provided for STAFF or PUBLIC users")
    }
    assertNoSideEffects()
  }

  @ParameterizedTest
  @EnumSource(value = UserType::class, names = ["STAFF", "PUBLIC"])
  fun `External update rejects missing or blank actor identity before any side effects`(userType: UserType) {
    val request = mock<UpdateVisitFromExternalSystemDto>()
    whenever(request.userType).thenReturn(userType)
    listOf(null, "", " ", "\t\n").forEach { identity ->
      whenever(request.actionedBy).thenReturn(identity)

      val exception = assertThrows<ValidationException> { visitService.updateVisitFromExternalSystem("visit-reference", request) }
      assertThat(exception.message).isEqualTo("actionedBy must be provided for STAFF or PUBLIC users")
    }
    assertNoSideEffects()
  }

  private fun assertNoSideEffects() {
    verifyNoInteractions(
      visitRepository,
      visitStoreService,
      telemetryClientService,
      eventAuditService,
      snsService,
      updateVisitSummaryUtil,
      visitNotificationTransactionalService,
    )
  }
}
