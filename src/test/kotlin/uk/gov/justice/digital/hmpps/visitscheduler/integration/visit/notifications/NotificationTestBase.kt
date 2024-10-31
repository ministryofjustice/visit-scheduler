package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.notifications

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NotificationCountDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.integration.mock.HmppsAuthExtension
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestEventAuditRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonerService
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit.MINUTES

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(HmppsAuthExtension::class)
abstract class NotificationTestBase : IntegrationTestBase() {

  @SpyBean
  lateinit var telemetryClient: TelemetryClient

  @SpyBean
  lateinit var prisonerService: PrisonerService

  @SpyBean
  lateinit var visitNotificationEventRepository: VisitNotificationEventRepository

  @Captor
  lateinit var mapCapture: ArgumentCaptor<Map<String, String>>

  @Autowired
  lateinit var testVisitNotificationEventRepository: TestVisitNotificationEventRepository

  @Autowired
  lateinit var testEventAuditRepository: TestEventAuditRepository

  fun assertFlaggedVisitEvent(visits: List<Visit>, type: NotificationEventType) {
    verify(telemetryClient, times(visits.size)).trackEvent(eq("flagged-visit-event"), mapCapture.capture(), isNull())

    val allData = mapCapture.allValues

    visits.forEachIndexed { index, visit ->
      val eventAudit = eventAuditRepository.findLastBookedVisitEventByBookingReference(visit.reference)

      val data = allData[index]
      assertThat(data["reference"]).isEqualTo(visit.reference)
      assertThat(data["applicationReference"]).isEqualTo(visit.getLastApplication()!!.reference)
      assertThat(data["prisonerId"]).isEqualTo(visit.prisonerId)
      assertThat(data["prisonId"]).isEqualTo(visit.prison.code)
      assertThat(data["visitStatus"]).isEqualTo(visit.visitStatus.name)
      assertThat(data["visitRestriction"]).isEqualTo(visit.visitRestriction.name)
      assertThat(data["visitStart"]).isEqualTo(visit.sessionSlot.slotStart.format(DateTimeFormatter.ISO_DATE_TIME))
      assertThat(data["visitEnd"]).isEqualTo(visit.sessionSlot.slotEnd.format(DateTimeFormatter.ISO_DATE_TIME))
      assertThat(data["visitType"]).isEqualTo(visit.visitType.name)
      assertThat(data["visitRoom"]).isEqualTo(visit.visitRoom)
      assertThat(data["hasPhoneNumber"]).isEqualTo((visit.visitContact!!.telephone != null).toString())
      assertThat(data["hasEmail"]).isEqualTo((visit.visitContact!!.email != null).toString())
      assertThat(data["totalVisitors"]).isEqualTo(visit.visitors.size.toString())
      assertThat(data["visitors"]).isEqualTo(visit.visitors.map { it.nomisPersonId }.joinToString(","))
      assertThat(data["reviewType"]).isEqualTo(type.reviewType)
      assertThat(LocalDateTime.parse(data["visitBooked"]).truncatedTo(MINUTES)).isEqualTo(visit.createTimestamp!!.truncatedTo(MINUTES).format(DateTimeFormatter.ISO_DATE_TIME))

      eventAudit.actionedBy.userName?.let { value ->
        assertThat(data["actionedBy"]).isEqualTo(value)
      }
      assertThat(data["source"]).isEqualTo(eventAudit.actionedBy.userType.name)
      assertThat(data["applicationMethodType"]).isEqualTo(eventAudit.applicationMethodType.name)
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
