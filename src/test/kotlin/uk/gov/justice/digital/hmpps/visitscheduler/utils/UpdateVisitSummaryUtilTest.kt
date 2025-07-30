package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.utils.diff.UpdateVisitSummaryUtil
import java.time.LocalDateTime

@DisplayName("Tests for UpdateVisitSummaryUtil")
class UpdateVisitSummaryUtilTest {
  val updateVisitSummaryUtil = UpdateVisitSummaryUtil()

  @Test
  fun `when visit rescheduled (and date changed) then update text mentions the updated time slot`() {
    val visitBeforeUpdate = createVisitDto(
      startTimestamp = LocalDateTime.of(2025, 8, 1, 11, 0, 0),
      endTimestamp = LocalDateTime.of(2025, 8, 1, 12, 0, 0),
    )

    val visitAfterUpdate = visitBeforeUpdate.copy(startTimestamp = visitBeforeUpdate.startTimestamp.plusDays(1), endTimestamp = visitBeforeUpdate.endTimestamp.plusDays(1))
    val updateText = updateVisitSummaryUtil.getDiff(visitDtoAfterUpdate = visitAfterUpdate, visitDtoBeforeUpdate = visitBeforeUpdate)
    assertThat(updateText).isEqualTo("Moved session from Fri 01-08-2025 (11:00 - 12:00) to Sat 02-08-2025 (11:00 - 12:00)")
  }

  @Test
  fun `when visit rescheduled (and date not changed) then update text mentions the updated time slot`() {
    val visitBeforeUpdate = createVisitDto(
      startTimestamp = LocalDateTime.of(2025, 8, 1, 11, 0, 0),
      endTimestamp = LocalDateTime.of(2025, 8, 1, 12, 0, 0),
    )

    val visitAfterUpdate = visitBeforeUpdate.copy(startTimestamp = visitBeforeUpdate.startTimestamp.plusHours(1), endTimestamp = visitBeforeUpdate.endTimestamp.plusHours(1))
    val updateText = updateVisitSummaryUtil.getDiff(visitDtoAfterUpdate = visitAfterUpdate, visitDtoBeforeUpdate = visitBeforeUpdate)
    assertThat(updateText).isEqualTo("Moved session from Fri 01-08-2025 (11:00 - 12:00) to (12:00 - 13:00)")
  }

  @Test
  fun `when visit updated but time remains same then update text is null`() {
    val visitBeforeUpdate = createVisitDto(
      startTimestamp = LocalDateTime.of(2025, 8, 1, 11, 0, 0),
      endTimestamp = LocalDateTime.of(2025, 8, 1, 12, 0, 0),
    )

    val visitAfterUpdate = visitBeforeUpdate.copy(startTimestamp = visitBeforeUpdate.startTimestamp, endTimestamp = visitBeforeUpdate.endTimestamp)
    val updateText = updateVisitSummaryUtil.getDiff(visitBeforeUpdate, visitAfterUpdate)
    assertThat(updateText).isNull()
  }

  @Test
  fun `when visit restriction changed then update text mentions change of restriction`() {
    val visitBeforeUpdate = createVisitDto(
      startTimestamp = LocalDateTime.now(),
      endTimestamp = LocalDateTime.now().plusHours(1),
      visitRestriction = VisitRestriction.OPEN,
    )

    val visitAfterUpdate = visitBeforeUpdate.copy(visitRestriction = VisitRestriction.CLOSED)
    val updateText = updateVisitSummaryUtil.getDiff(visitDtoAfterUpdate = visitAfterUpdate, visitDtoBeforeUpdate = visitBeforeUpdate)
    assertThat(updateText).isEqualTo("Visit restriction changed from OPEN to CLOSED")
  }

  @Test
  fun `when visit restriction not changed then update text is null`() {
    val visitBeforeUpdate = createVisitDto(
      startTimestamp = LocalDateTime.now(),
      endTimestamp = LocalDateTime.now().plusHours(1),
      visitRestriction = VisitRestriction.CLOSED,
    )

    val visitAfterUpdate = visitBeforeUpdate.copy(visitRestriction = VisitRestriction.CLOSED)
    val updateText = updateVisitSummaryUtil.getDiff(visitDtoAfterUpdate = visitAfterUpdate, visitDtoBeforeUpdate = visitBeforeUpdate)
    assertThat(updateText).isNull()
  }

  @Test
  fun `when visit rescheduled and restriction updated then update text mentions the updated time slot and restriction change`() {
    val visitBeforeUpdate = createVisitDto(
      startTimestamp = LocalDateTime.of(2025, 8, 1, 11, 0, 0),
      endTimestamp = LocalDateTime.of(2025, 8, 1, 12, 0, 0),
      visitRestriction = VisitRestriction.OPEN,
    )

    val visitAfterUpdate = visitBeforeUpdate.copy(startTimestamp = visitBeforeUpdate.startTimestamp.plusHours(1), endTimestamp = visitBeforeUpdate.endTimestamp.plusHours(1), visitRestriction = VisitRestriction.CLOSED)
    val updateText = updateVisitSummaryUtil.getDiff(visitDtoAfterUpdate = visitAfterUpdate, visitDtoBeforeUpdate = visitBeforeUpdate)
    assertThat(updateText).isEqualTo("Visit restriction changed from OPEN to CLOSED, Moved session from Fri 01-08-2025 (11:00 - 12:00) to (12:00 - 13:00)")
  }

  @Test
  fun `when visitors added then update text mentions number of visitors added`() {
    val visitBeforeUpdate = createVisitDto(
      startTimestamp = LocalDateTime.now(),
      endTimestamp = LocalDateTime.now().plusHours(1),
      visitors = listOf(VisitorDto(1, true), VisitorDto(2, false)),
    )

    val updatedVisitors = listOf(
      VisitorDto(1, true),
      VisitorDto(2, false),
      VisitorDto(3, false),
      VisitorDto(4, false),
    )
    val visitAfterUpdate = visitBeforeUpdate.copy(visitors = updatedVisitors)
    val updateText = updateVisitSummaryUtil.getDiff(visitDtoAfterUpdate = visitAfterUpdate, visitDtoBeforeUpdate = visitBeforeUpdate)
    assertThat(updateText).isEqualTo("Added 2 visitor(s)")
  }

  @Test
  fun `when visitors removed then update text mentions number of visitors removed`() {
    val existingVisitors = listOf(
      VisitorDto(1, true),
      VisitorDto(2, false),
      VisitorDto(3, false),
      VisitorDto(4, false),
    )

    val visitBeforeUpdate = createVisitDto(
      startTimestamp = LocalDateTime.now(),
      endTimestamp = LocalDateTime.now().plusHours(1),
      visitors = existingVisitors,
    )

    // removed visitor IDs 1 and 2
    val updatedVisitors = listOf(
      VisitorDto(3, true),
      VisitorDto(4, false),
    )
    val visitAfterUpdate = visitBeforeUpdate.copy(visitors = updatedVisitors)
    val updateText = updateVisitSummaryUtil.getDiff(visitDtoAfterUpdate = visitAfterUpdate, visitDtoBeforeUpdate = visitBeforeUpdate)
    assertThat(updateText).isEqualTo("Removed 2 visitor(s)")
  }

  @Test
  fun `when visitors added and removed then update text mentions number of visitors added and removed`() {
    val existingVisitors = listOf(
      VisitorDto(1, true),
      VisitorDto(2, false),
      VisitorDto(3, false),
    )

    val visitBeforeUpdate = createVisitDto(
      startTimestamp = LocalDateTime.now(),
      endTimestamp = LocalDateTime.now().plusHours(1),
      visitors = existingVisitors,
    )

    // removed visitor ids 2 and added id 4
    val updatedVisitors = listOf(
      VisitorDto(1, true),
      VisitorDto(3, false),
      VisitorDto(4, false),
    )
    val visitAfterUpdate = visitBeforeUpdate.copy(visitors = updatedVisitors)
    val updateText = updateVisitSummaryUtil.getDiff(visitDtoAfterUpdate = visitAfterUpdate, visitDtoBeforeUpdate = visitBeforeUpdate)
    assertThat(updateText).isEqualTo("Added 1 visitor(s), Removed 1 visitor(s)")
  }

  @Test
  fun `when visit rescheduled, restriction updated and visitors added then update text mentions the updated time slot, restriction change and visitors added`() {
    val visitBeforeUpdate = createVisitDto(
      startTimestamp = LocalDateTime.of(2025, 8, 1, 11, 0, 0),
      endTimestamp = LocalDateTime.of(2025, 8, 1, 12, 0, 0),
      visitRestriction = VisitRestriction.OPEN,
      visitors = listOf(VisitorDto(1, true)),
    )

    val visitAfterUpdate = visitBeforeUpdate.copy(
      startTimestamp = visitBeforeUpdate.startTimestamp.plusHours(1),
      endTimestamp = visitBeforeUpdate.endTimestamp.plusHours(1),
      visitRestriction = VisitRestriction.CLOSED,
      visitors = listOf(VisitorDto(1, true), VisitorDto(2, false)),
    )
    val updateText = updateVisitSummaryUtil.getDiff(visitDtoAfterUpdate = visitAfterUpdate, visitDtoBeforeUpdate = visitBeforeUpdate)
    assertThat(updateText).isEqualTo("Visit restriction changed from OPEN to CLOSED, Added 1 visitor(s), Moved session from Fri 01-08-2025 (11:00 - 12:00) to (12:00 - 13:00)")
  }

  @Test
  fun `when visit support info updated then update text mentions support text updated`() {
    val visitBeforeUpdate = createVisitDto(
      startTimestamp = LocalDateTime.now(),
      endTimestamp = LocalDateTime.now().plusHours(1),
      visitorSupport = VisitorSupportDto("additional info"),
    )

    val visitAfterUpdate = visitBeforeUpdate.copy(visitorSupport = VisitorSupportDto("additional info updated"))
    val updateText = updateVisitSummaryUtil.getDiff(visitDtoAfterUpdate = visitAfterUpdate, visitDtoBeforeUpdate = visitBeforeUpdate)
    assertThat(updateText).isEqualTo("Updated additional support")
  }

  @Test
  fun `when visit support info not updated then update text is null`() {
    val visitBeforeUpdate = createVisitDto(
      startTimestamp = LocalDateTime.now(),
      endTimestamp = LocalDateTime.now().plusHours(1),
      visitorSupport = VisitorSupportDto("additional info"),
    )

    val visitAfterUpdate = visitBeforeUpdate.copy(visitorSupport = VisitorSupportDto("additional info"))
    val updateText = updateVisitSummaryUtil.getDiff(visitDtoAfterUpdate = visitAfterUpdate, visitDtoBeforeUpdate = visitBeforeUpdate)
    assertThat(updateText).isNull()
  }

  @Test
  fun `when visit support info not updated (null before and after update) then update text is null`() {
    val visitBeforeUpdate = createVisitDto(
      startTimestamp = LocalDateTime.now(),
      endTimestamp = LocalDateTime.now().plusHours(1),
      visitorSupport = null,
    )

    val visitAfterUpdate = visitBeforeUpdate.copy(visitorSupport = null)
    val updateText = updateVisitSummaryUtil.getDiff(visitDtoAfterUpdate = visitAfterUpdate, visitDtoBeforeUpdate = visitBeforeUpdate)
    assertThat(updateText).isNull()
  }

  @Test
  fun `when visit rescheduled, restriction updated, visitors added and support updated then update text mentions the updated time slot, restriction change, visitors added and support updated`() {
    val visitBeforeUpdate = createVisitDto(
      startTimestamp = LocalDateTime.of(2025, 8, 1, 11, 0, 0),
      endTimestamp = LocalDateTime.of(2025, 8, 1, 12, 0, 0),
      visitRestriction = VisitRestriction.OPEN,
      visitors = listOf(VisitorDto(1, true)),
      visitorSupport = VisitorSupportDto("additional info"),
    )

    val visitAfterUpdate = visitBeforeUpdate.copy(
      startTimestamp = visitBeforeUpdate.startTimestamp.plusHours(1),
      endTimestamp = visitBeforeUpdate.endTimestamp.plusHours(1),
      visitRestriction = VisitRestriction.CLOSED,
      visitors = listOf(VisitorDto(1, true), VisitorDto(2, false)),
      visitorSupport = VisitorSupportDto("additional info updated"),
    )
    val updateText = updateVisitSummaryUtil.getDiff(visitDtoAfterUpdate = visitAfterUpdate, visitDtoBeforeUpdate = visitBeforeUpdate)
    assertThat(updateText).isEqualTo("Visit restriction changed from OPEN to CLOSED, Added 1 visitor(s), Moved session from Fri 01-08-2025 (11:00 - 12:00) to (12:00 - 13:00), Updated additional support")
  }

  @Test
  fun `when visit contact name updated then update text mentions contact updated`() {
    val visitBeforeUpdate = createVisitDto(
      startTimestamp = LocalDateTime.now(),
      endTimestamp = LocalDateTime.now().plusHours(1),
      visitContact = ContactDto(name = "test user", telephone = "01234 567890", email = "test@example.com"),
    )

    val updatedVisitContact = ContactDto(name = "updated user", telephone = "01234 567890", email = "test@example.com")
    val visitAfterUpdate = visitBeforeUpdate.copy(visitContact = updatedVisitContact)
    val updateText = updateVisitSummaryUtil.getDiff(visitDtoAfterUpdate = visitAfterUpdate, visitDtoBeforeUpdate = visitBeforeUpdate)
    assertThat(updateText).isEqualTo("Updated contact information")
  }

  @Test
  fun `when visit contact phone number updated then update text mentions contact updated`() {
    val visitBeforeUpdate = createVisitDto(
      startTimestamp = LocalDateTime.now(),
      endTimestamp = LocalDateTime.now().plusHours(1),
      visitContact = ContactDto(name = "test user", telephone = "01234 567890", email = "test@example.com"),
    )

    val updatedVisitContact = ContactDto(name = "test user", telephone = "99999 567890", email = "test@example.com")
    val visitAfterUpdate = visitBeforeUpdate.copy(visitContact = updatedVisitContact)
    val updateText = updateVisitSummaryUtil.getDiff(visitDtoAfterUpdate = visitAfterUpdate, visitDtoBeforeUpdate = visitBeforeUpdate)
    assertThat(updateText).isEqualTo("Updated contact information")
  }

  @Test
  fun `when visit contact email updated then update text mentions contact updated`() {
    val visitBeforeUpdate = createVisitDto(
      startTimestamp = LocalDateTime.now(),
      endTimestamp = LocalDateTime.now().plusHours(1),
      visitContact = ContactDto(name = "test user", telephone = "01234 567890", email = "test@example.com"),
    )

    val updatedVisitContact = ContactDto(name = "test user", telephone = "01234 567890", email = "updatedtest@example.com")
    val visitAfterUpdate = visitBeforeUpdate.copy(visitContact = updatedVisitContact)
    val updateText = updateVisitSummaryUtil.getDiff(visitDtoAfterUpdate = visitAfterUpdate, visitDtoBeforeUpdate = visitBeforeUpdate)
    assertThat(updateText).isEqualTo("Updated contact information")
  }

  @Test
  fun `when visit contact name phone amd email updated then update text mentions contact updated`() {
    val visitBeforeUpdate = createVisitDto(
      startTimestamp = LocalDateTime.now(),
      endTimestamp = LocalDateTime.now().plusHours(1),
      visitContact = ContactDto(name = "test user", telephone = "01234 567890", email = "test@example.com"),
    )

    val updatedVisitContact = ContactDto(name = "updated test user", telephone = "99999 567890", email = "updatedtest@example.com")
    val visitAfterUpdate = visitBeforeUpdate.copy(visitContact = updatedVisitContact)
    val updateText = updateVisitSummaryUtil.getDiff(visitDtoAfterUpdate = visitAfterUpdate, visitDtoBeforeUpdate = visitBeforeUpdate)
    assertThat(updateText).isEqualTo("Updated contact information")
  }

  @Test
  fun `when visit contact not updated then update text is null`() {
    val contact = ContactDto(name = "test user", telephone = "01234 567890", email = "test@example.com")

    val visitBeforeUpdate = createVisitDto(
      startTimestamp = LocalDateTime.now(),
      endTimestamp = LocalDateTime.now().plusHours(1),
      visitContact = contact,
    )

    val visitAfterUpdate = visitBeforeUpdate.copy(visitContact = contact)
    val updateText = updateVisitSummaryUtil.getDiff(visitDtoAfterUpdate = visitAfterUpdate, visitDtoBeforeUpdate = visitBeforeUpdate)
    assertThat(updateText).isNull()
  }

  @Test
  fun `when visit rescheduled, restriction updated, visitors added, support updated and contact info updated then update text mentions the updated time slot, restriction change, visitors added, support updated and contact info updated`() {
    val visitBeforeUpdate = createVisitDto(
      startTimestamp = LocalDateTime.of(2025, 8, 1, 11, 0, 0),
      endTimestamp = LocalDateTime.of(2025, 8, 1, 12, 0, 0),
      visitRestriction = VisitRestriction.OPEN,
      visitors = listOf(VisitorDto(1, true)),
      visitorSupport = VisitorSupportDto("additional info"),
      visitContact = ContactDto(name = "test user", telephone = "01234 567890", email = "test@example.com"),
    )

    val visitAfterUpdate = visitBeforeUpdate.copy(
      startTimestamp = visitBeforeUpdate.startTimestamp.plusHours(1),
      endTimestamp = visitBeforeUpdate.endTimestamp.plusHours(1),
      visitRestriction = VisitRestriction.CLOSED,
      visitors = listOf(VisitorDto(1, true), VisitorDto(2, false)),
      visitorSupport = VisitorSupportDto("additional info updated"),
      visitContact = ContactDto(name = "updated test user", telephone = "01234 567890", email = "test@example.com"),
    )

    val updateText = updateVisitSummaryUtil.getDiff(visitDtoAfterUpdate = visitAfterUpdate, visitDtoBeforeUpdate = visitBeforeUpdate)
    assertThat(updateText).isEqualTo("Visit restriction changed from OPEN to CLOSED, Added 1 visitor(s), Moved session from Fri 01-08-2025 (11:00 - 12:00) to (12:00 - 13:00), Updated additional support, Updated contact information")
  }

  @Test
  fun `when no changes are made in update then updated text is null`() {
    val visitBeforeUpdate = createVisitDto(
      startTimestamp = LocalDateTime.now(),
      endTimestamp = LocalDateTime.now().plusHours(1),
    )

    val visitAfterUpdate = visitBeforeUpdate.copy()

    val updateText = updateVisitSummaryUtil.getDiff(visitDtoAfterUpdate = visitAfterUpdate, visitDtoBeforeUpdate = visitBeforeUpdate)
    assertThat(updateText).isNull()
  }

  private fun createVisitDto(
    applicationReference: String? = null,
    sessionTemplateReference: String = "ss-aa-bb-cc",
    reference: String = "aa-bb-cc-dd",
    prisonerId: String = "A123",
    prisonCode: String = "XYZ",
    visitRoom: String = "visit room",
    visitType: VisitType = VisitType.SOCIAL,
    visitStatus: VisitStatus = VisitStatus.BOOKED,
    visitSubStatus: VisitSubStatus = VisitSubStatus.AUTO_APPROVED,
    outcomeStatus: OutcomeStatus? = null,
    visitRestriction: VisitRestriction = VisitRestriction.OPEN,
    startTimestamp: LocalDateTime,
    endTimestamp: LocalDateTime,
    visitContact: ContactDto = ContactDto(name = "test", telephone = "01234 567890", email = "test@example.com"),
    visitors: List<VisitorDto> = listOf(VisitorDto(1, true), VisitorDto(2, false)),
    visitorSupport: VisitorSupportDto? = null,
  ): VisitDto = VisitDto(
    applicationReference = applicationReference,
    sessionTemplateReference = sessionTemplateReference,
    reference = reference,
    prisonerId = prisonerId,
    prisonCode = prisonCode,
    visitRoom = visitRoom,
    visitType = visitType,
    visitStatus = visitStatus,
    visitSubStatus = visitSubStatus,
    outcomeStatus = outcomeStatus,
    visitRestriction = visitRestriction,
    startTimestamp = startTimestamp,
    endTimestamp = endTimestamp,
    visitNotes = emptyList(),
    visitContact = visitContact,
    visitors = visitors,
    visitorSupport = visitorSupport,
    createdTimestamp = LocalDateTime.now(),
    modifiedTimestamp = LocalDateTime.now(),
    userType = UserType.STAFF,
    firstBookedDateTime = null,
    visitExternalSystemDetails = null,
  )
}
