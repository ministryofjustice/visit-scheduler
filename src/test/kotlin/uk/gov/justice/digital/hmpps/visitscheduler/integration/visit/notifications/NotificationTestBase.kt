package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.notifications

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NotificationCountDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.integration.mock.HmppsAuthExtension
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestEventAuditRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonerService

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(HmppsAuthExtension::class)
abstract class NotificationTestBase() : IntegrationTestBase() {

  @SpyBean
  lateinit var telemetryClient: TelemetryClient

  @SpyBean
  lateinit var prisonerService: PrisonerService

  @SpyBean
  lateinit var visitNotificationEventRepository: VisitNotificationEventRepository

  @Autowired
  lateinit var testVisitNotificationEventRepository: TestVisitNotificationEventRepository

  @Autowired
  lateinit var testEventAuditRepository: TestEventAuditRepository

  fun assertBookedEvent(visits: List<Visit>, type: NotificationEventType) {
    visits.forEach { visit ->
      run {
        val eventAudit = eventAuditRepository.findLastBookedVisitEventByBookingReference(visit.reference)

        verify(telemetryClient).trackEvent(
          eq("flagged-visit-event"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["prisonId"]).isEqualTo(visit.prison.code)
            Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
            Assertions.assertThat(it["reviewType"]).isEqualTo(type.reviewType)
            Assertions.assertThat(it["visitBooked"]).isEqualTo(formatDateTimeToString(eventAudit.createTimestamp))
            Assertions.assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
            Assertions.assertThat(it["applicationReference"]).isEqualTo(visit.getLastApplication()?.reference)
            Assertions.assertThat(it["prisonerId"]).isEqualTo(visit.prisonerId)
            Assertions.assertThat(it["actionedBy"]).isEqualTo(eventAudit.actionedBy)
            Assertions.assertThat(it["visitRestriction"]).isEqualTo(visit.visitRestriction.name)
            Assertions.assertThat(it["visitStart"]).isEqualTo(formatStartSlotDateTimeToString(visit.sessionSlot))
            Assertions.assertThat(it["visitType"]).isEqualTo(visit.visitType.name)
            Assertions.assertThat(it["visitRoom"]).isEqualTo(visit.visitRoom)
          },
          isNull(),
        )
      }
    }
  }

  fun verifyNoInteractions(vararg mocks: Any) {
    Mockito.verifyNoInteractions(*mocks)
  }

  fun getNotificationCountDto(responseSpec: ResponseSpec): NotificationCountDto =
    objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, NotificationCountDto::class.java)

  fun getNotificationTypes(responseSpec: ResponseSpec): Array<NotificationEventType> =
    objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, Array<NotificationEventType>::class.java)
}
