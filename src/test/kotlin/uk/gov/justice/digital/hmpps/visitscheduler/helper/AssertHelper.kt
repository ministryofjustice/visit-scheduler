package uk.gov.justice.digital.hmpps.visitscheduler.helper

import org.assertj.core.api.Assertions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestEventAuditRepository

@Component
class AssertHelper {

  @Autowired
  protected lateinit var eventAuditRepository: TestEventAuditRepository

  fun assertVisitCancellation(
    cancelledVisit: VisitDto,
    expectedOutcomeStatus: OutcomeStatus = OutcomeStatus.CANCELLATION,
    cancelledBy: String,
    applicationMethodType: ApplicationMethodType? = ApplicationMethodType.PHONE,
  ) {
    val eventAudit = this.eventAuditRepository.findLastEventByBookingReference(cancelledVisit.reference)

    Assertions.assertThat(eventAudit.type).isEqualTo(EventAuditType.CANCELLED_VISIT)
    Assertions.assertThat(eventAudit.actionedBy).isEqualTo(cancelledBy)
    Assertions.assertThat(eventAudit.applicationMethodType).isEqualTo(applicationMethodType)
    Assertions.assertThat(eventAudit.bookingReference).isEqualTo(cancelledVisit.reference)
    Assertions.assertThat(eventAudit.sessionTemplateReference).isEqualTo(cancelledVisit.sessionTemplateReference)
    Assertions.assertThat(eventAudit.applicationReference).isEqualTo(cancelledVisit.applicationReference)

    Assertions.assertThat(cancelledVisit.visitStatus).isEqualTo(VisitStatus.CANCELLED)
    Assertions.assertThat(cancelledVisit.outcomeStatus).isEqualTo(expectedOutcomeStatus)
  }
}
