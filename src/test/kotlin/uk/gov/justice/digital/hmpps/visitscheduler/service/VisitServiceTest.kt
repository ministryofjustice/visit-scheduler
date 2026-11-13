package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.ValidationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.springframework.security.access.AccessDeniedException
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitFromExternalSystemDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateVisitFromExternalSystemDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdateVisitFromExternalSystemDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitExternalSystemDetails
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
  fun `when actionedBy is missing or blank for staff or public then the visit is not created`(userType: UserType) {
    val request = mock<CreateVisitFromExternalSystemDto>()
    whenever(request.userType).thenReturn(userType)
    listOf(null, "", " ", "\t\n").forEach { identity ->
      whenever(request.actionedBy).thenReturn(identity)

      val exception = assertThrows<ValidationException> { visitService.createVisitFromExternalSystem(request) }
      assertThat(exception.message).isEqualTo("actionedBy must be provided for STAFF or PUBLIC users")
    }
    verifyNoServiceOrRepositoryCalls()
  }

  @ParameterizedTest
  @EnumSource(value = UserType::class, names = ["STAFF", "PUBLIC"])
  fun `when actionedBy is missing or blank for staff or public then the visit is not updated`(userType: UserType) {
    val request = mock<UpdateVisitFromExternalSystemDto>()
    whenever(request.userType).thenReturn(userType)
    listOf(null, "", " ", "\t\n").forEach { identity ->
      whenever(request.actionedBy).thenReturn(identity)

      val exception = assertThrows<ValidationException> { visitService.updateVisitFromExternalSystem("visit-reference", request) }
      assertThat(exception.message).isEqualTo("actionedBy must be provided for STAFF or PUBLIC users")
    }
    verifyNoServiceOrRepositoryCalls()
  }

  private fun verifyNoServiceOrRepositoryCalls() {
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

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = ["another-client", "mdi", " MDI "])
  fun `when clientName does not match then the visit is not updated`(owner: String?) {
    val request = mock<UpdateVisitFromExternalSystemDto>()
    whenever(request.clientName).thenReturn("MDI")
    val visit = mock<Visit>()
    if (owner != null) {
      val details = mock<VisitExternalSystemDetails>()
      whenever(details.clientName).thenReturn(owner)
      whenever(visit.visitExternalSystemDetails).thenReturn(details)
    }
    whenever(visitRepository.findBookedVisit("visit-reference")).thenReturn(visit)

    assertThrows<AccessDeniedException> { visitService.updateVisitFromExternalSystem("visit-reference", request) }

    verifyNoInteractions(visitStoreService, telemetryClientService, eventAuditService, snsService, updateVisitSummaryUtil, visitNotificationTransactionalService)
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = ["another-client", "mdi", " MDI "])
  fun `when clientName does not match then cancellation is not attempted`(owner: String?) {
    val request = mock<CancelVisitFromExternalSystemDto>()
    whenever(request.clientName).thenReturn("MDI")
    val visit = mock<Visit>()
    if (owner != null) {
      val details = mock<VisitExternalSystemDetails>()
      whenever(details.clientName).thenReturn(owner)
      whenever(visit.visitExternalSystemDetails).thenReturn(details)
    }
    whenever(visitRepository.findByReference("visit-reference")).thenReturn(visit)

    assertThrows<AccessDeniedException> { visitService.cancelVisitFromExternalSystem("visit-reference", request) }

    verifyNoInteractions(visitStoreService, telemetryClientService, eventAuditService, snsService, updateVisitSummaryUtil, visitNotificationTransactionalService)
  }

  @ParameterizedTest
  @NullSource
  @EnumSource(value = UserType::class, names = ["STAFF", "PRISONER"])
  fun `when the username or prisoner ID exceeds 60 characters then the visit is not created`(userType: UserType?) {
    val request = mock<CreateVisitFromExternalSystemDto>()
    whenever(request.userType).thenReturn(userType)
    whenever(request.actionedBy).thenReturn("s".repeat(61))
    whenever(request.prisonerId).thenReturn("p".repeat(61))

    val exception = assertThrows<ValidationException> { visitService.createVisitFromExternalSystem(request) }
    assertThat(exception.message).isEqualTo("Audit username must not exceed 60 characters")
    verifyNoServiceOrRepositoryCalls()
  }

  @ParameterizedTest
  @NullSource
  @EnumSource(value = UserType::class, names = ["STAFF", "PRISONER"])
  fun `when the username or prisoner ID exceeds 60 characters then the visit is not updated`(userType: UserType?) {
    val request = mock<UpdateVisitFromExternalSystemDto>()
    whenever(request.userType).thenReturn(userType)
    whenever(request.actionedBy).thenReturn("s".repeat(61))
    val visit = mock<Visit>()
    whenever(request.clientName).thenReturn("MDI")
    val externalDetails = mock<VisitExternalSystemDetails>()
    whenever(externalDetails.clientName).thenReturn("MDI")
    whenever(visit.visitExternalSystemDetails).thenReturn(externalDetails)
    whenever(visit.prisonerId).thenReturn("p".repeat(61))
    whenever(visitRepository.findBookedVisit("visit-reference")).thenReturn(visit)

    val exception = assertThrows<ValidationException> { visitService.updateVisitFromExternalSystem("visit-reference", request) }
    assertThat(exception.message).isEqualTo("Audit username must not exceed 60 characters")
    verify(visitRepository).findBookedVisit("visit-reference")
    verifyNoMoreInteractions(visitRepository)
    verifyNoInteractions(visitStoreService, telemetryClientService, eventAuditService, snsService, updateVisitSummaryUtil, visitNotificationTransactionalService)
  }
}
