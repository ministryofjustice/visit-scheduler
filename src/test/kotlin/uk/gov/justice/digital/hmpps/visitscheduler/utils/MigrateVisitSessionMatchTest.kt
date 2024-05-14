package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonerService
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class MigrateVisitSessionMatchTest {

  private val prisonerService: PrisonerService = Mockito.mock(PrisonerService::class.java)
  private val sessionTemplateRepository: SessionTemplateRepository = Mockito.mock(SessionTemplateRepository::class.java)
  private val prisonerCategoryMatcher: PrisonerCategoryMatcher = Mockito.mock(PrisonerCategoryMatcher::class.java)
  private val prisonerIncentiveLevelMatcher: PrisonerIncentiveLevelMatcher = Mockito.mock(PrisonerIncentiveLevelMatcher::class.java)
  private val sessionValidator: PrisonerSessionValidator = Mockito.mock(PrisonerSessionValidator::class.java)
  private val sessionDatesUtil: SessionDatesUtil = Mockito.mock(SessionDatesUtil::class.java)

  @InjectMocks
  private val migrationSessionTemplateMatcher = MigrationSessionTemplateMatcher(
    prisonerService,
    sessionTemplateRepository,
    prisonerCategoryMatcher,
    prisonerIncentiveLevelMatcher,
    sessionValidator,
    sessionDatesUtil,
  )

  @Test
  fun `When session start date is before migrated visit start date`() {
    // Given
    val (migrateVisitRequest, sessionTemplate) = setUpTest(-1)

    // When
    val result = doTest(migrateVisitRequest, sessionTemplate)

    // Then
    assertThat(result).isTrue()
  }

  @Test
  fun `When session start date is after migrated visit start date`() {
    // Given
    val (migrateVisitRequest, sessionTemplate) = setUpTest(1)

    // When
    val result = doTest(migrateVisitRequest, sessionTemplate)

    // Then
    assertThat(result).isTrue()
  }

  @Test
  fun `When session start date is same as migrated visit start date`() {
    // Given
    val (migrateVisitRequest, sessionTemplate) = setUpTest(0, 0)

    // When
    val result = doTest(migrateVisitRequest, sessionTemplate)

    // Then
    assertThat(result).isFalse()
  }

  @Test
  fun `When session end date is before migrated visit end date`() {
    // Given
    val (migrateVisitRequest, sessionTemplate) = setUpTest(0, -30)

    // When
    val result = doTest(migrateVisitRequest, sessionTemplate)

    // Then
    assertThat(result).isTrue()
  }

  @Test
  fun `When session end date is after migrated visit end date`() {
    // Given
    val (migrateVisitRequest, sessionTemplate) = setUpTest(0, 30)

    // When
    val result = doTest(migrateVisitRequest, sessionTemplate)

    // Then
    assertThat(result).isTrue()
  }

  @Test
  fun `When session end date is same as migrated visit end date`() {
    // Given
    val (migrateVisitRequest, sessionTemplate) = setUpTest(0, 0)

    // When
    val result = doTest(migrateVisitRequest, sessionTemplate)
    // Then
    assertThat(result).isFalse()
  }

  private fun setUpTest(
    sessionTemplateStartMinDiff: Int,
    sessionTemplateEndMinDiff: Int? = null,
  ): Pair<MigrateVisitRequestDto, SessionTemplate> {
    val migrateVisitRequest = Mockito.mock(MigrateVisitRequestDto::class.java)
    val sessionTemplate = Mockito.mock(SessionTemplate::class.java)

    val startLocalDateTime = LocalDateTime.now()
    val endLocalDateTime = startLocalDateTime.plusHours(1)

    Mockito.`when`(migrateVisitRequest.startTimestamp).thenReturn(startLocalDateTime)
    Mockito.`when`(sessionTemplate.startTime).thenReturn(startLocalDateTime.toLocalTime().plusMinutes(sessionTemplateStartMinDiff.toLong()))

    sessionTemplateEndMinDiff?.let {
      Mockito.`when`(migrateVisitRequest.endTimestamp).thenReturn(endLocalDateTime)
      Mockito.`when`(sessionTemplate.endTime).thenReturn(endLocalDateTime.toLocalTime().plusMinutes(it.toLong()))
    }

    return Pair(migrateVisitRequest, sessionTemplate)
  }

  private fun doTest(
    migrateVisitRequest: MigrateVisitRequestDto,
    sessionTemplate: SessionTemplate,
  ): Boolean {
    val result = migrationSessionTemplateMatcher.isThereASessionTimeMisMatch(
      migrateVisitRequest = migrateVisitRequest,
      sessionTemplate = sessionTemplate,
    )
    return result
  }
}
