package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus.COMPLETED_NORMALLY
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSearchSubject.PRISONER
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSearchType.RUBDOWN_LEVEL_A
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.COMPLETED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitByReference
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitAttendanceDetails
import java.time.LocalTime

class VisitAttendanceTest : IntegrationTestBase() {
  @Autowired
  private lateinit var jdbcTemplate: JdbcTemplate

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
    sessionTemplateDefault = sessionTemplateEntityHelper.create(startTime = LocalTime.of(12, 30), endTime = LocalTime.of(13, 30))
  }

  @Test
  fun `Booked visit omits unrecorded attendance details`() {
    val visit = visitEntityHelper.create(
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
      visitContact = ContactDto("Jane Doe", null, null),
    )

    callVisitByReference(webTestClient, visit.reference, roleVisitSchedulerHttpHeaders)
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.attendanceDetails").doesNotHaveJsonPath()
  }

  @Test
  fun `Completed visit by reference returns saved attendance details and visitor flags`() {
    val visit = visitEntityHelper.create(
      visitStatus = COMPLETED,
      visitSubStatus = VisitSubStatus.ATTENDED,
      outcomeStatus = COMPLETED_NORMALLY,
      sessionTemplate = sessionTemplateDefault,
      visitContact = ContactDto("Jane Doe", null, null),
    )
    listOf(true, false, null).forEachIndexed { index, attended ->
      visitEntityHelper.createVisitor(visit, index + 1L, false)
      visit.visitors.last().attended = attended
    }
    visit.attendanceDetails = VisitAttendanceDetails(
      visitId = visit.id,
      visit = visit,
      searchSubject = PRISONER,
      searchType = RUBDOWN_LEVEL_A,
      actualStartTime = LocalTime.of(12, 35, 1),
      actualEndTime = LocalTime.of(13, 30, 2),
    )
    visitEntityHelper.save(visit)

    val storedSearch = jdbcTemplate.queryForMap(
      "SELECT search_subject, search_type FROM visit_attendance_details WHERE visit_id = ?",
      visit.id,
    )
    assertThat(storedSearch).containsEntry("search_subject", "PRISONER")
      .containsEntry("search_type", "RUBDOWN_LEVEL_A")

    callVisitByReference(webTestClient, visit.reference, roleVisitSchedulerHttpHeaders)
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.visitStatus").isEqualTo("COMPLETED")
      .jsonPath("$.visitSubStatus").isEqualTo("ATTENDED")
      .jsonPath("$.outcomeStatus").isEqualTo("COMPLETED_NORMALLY")
      .jsonPath("$.attendanceDetails.search.subject").isEqualTo("PRISONER")
      .jsonPath("$.attendanceDetails.search.type").isEqualTo("RUBDOWN_LEVEL_A")
      .jsonPath("$.attendanceDetails.actualStartTime").isEqualTo("12:35:01")
      .jsonPath("$.attendanceDetails.actualEndTime").isEqualTo("13:30:02")
      .jsonPath("$.visitors[?(@.nomisPersonId == 1)].attended").isEqualTo(listOf(true))
      .jsonPath("$.visitors[?(@.nomisPersonId == 2)].attended").isEqualTo(listOf(false))
      .jsonPath("$.visitors[?(@.nomisPersonId == 3)].attended").doesNotExist()
  }

  @Test
  fun `Completed visit can be not attended without additional details or an outcome`() {
    val visit = visitEntityHelper.create(
      visitStatus = COMPLETED,
      visitSubStatus = VisitSubStatus.NOT_ATTENDED,
      sessionTemplate = sessionTemplateDefault,
      visitContact = ContactDto("Jane Doe", null, null),
    )

    callVisitByReference(webTestClient, visit.reference, roleVisitSchedulerHttpHeaders)
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.visitSubStatus").isEqualTo("NOT_ATTENDED")
      .jsonPath("$.outcomeStatus").doesNotHaveJsonPath()
      .jsonPath("$.attendanceDetails").doesNotHaveJsonPath()
  }

  @Test
  fun `Database rejects attendance substatus on a booked visit`() {
    assertThatThrownBy {
      visitEntityHelper.create(
        visitStatus = BOOKED,
        visitSubStatus = VisitSubStatus.ATTENDED,
        sessionTemplate = sessionTemplateDefault,
      )
    }.isInstanceOf(DataIntegrityViolationException::class.java)
      .hasMessageContaining("chk_visit_sub_status")
  }
}
