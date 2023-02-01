package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCreateSessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import java.time.LocalDate

@DisplayName("Get /visit-sessions")
class CreateSessionsTemplateTest : IntegrationTestBase() {

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  private var prison: Prison = Prison(code = "MDI", active = true)

  @BeforeEach
  internal fun setUpTests() {
    prison = prisonEntityHelper.create(prison.code, prison.active)
  }

  @Test
  fun `create session template`() {

    // Given
    val allowedPermittedLocations = listOf(AllowedSessionLocationHierarchy("A", "1", "001"))
    val sessionGroup = sessionLocationGroupHelper.create(prison = prison, prisonHierarchies = allowedPermittedLocations)

    val dto = createSessionTemplateDto(validToDate = LocalDate.now().plusDays(1), locationGroupReferences = mutableListOf(sessionGroup.reference))

    // When
    val responseSpec = callCreateSessionTemplate(webTestClient, dto, setAuthorisation(roles = requiredRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.validFromDate).isEqualTo(dto.validFromDate)
    Assertions.assertThat(sessionTemplateDto.validToDate).isEqualTo(dto.validToDate)
    Assertions.assertThat(sessionTemplateDto.closedCapacity).isEqualTo(dto.closedCapacity)
    Assertions.assertThat(sessionTemplateDto.openCapacity).isEqualTo(dto.openCapacity)
    Assertions.assertThat(sessionTemplateDto.prisonCode).isEqualTo(dto.prisonCode)
    Assertions.assertThat(sessionTemplateDto.visitRoom).isEqualTo(dto.visitRoom)
    Assertions.assertThat(sessionTemplateDto.startTime).isEqualTo(dto.startTime)
    Assertions.assertThat(sessionTemplateDto.endTime).isEqualTo(dto.endTime)
    Assertions.assertThat(sessionTemplateDto.dayOfWeek).isEqualTo(dto.dayOfWeek)
    Assertions.assertThat(sessionTemplateDto.permittedLocationGroups.size).isEqualTo(1)
    Assertions.assertThat(sessionTemplateDto.permittedLocationGroups[0].reference).isEqualTo(dto.locationGroupReferences!![0])
    Assertions.assertThat(sessionTemplateDto.biWeekly).isEqualTo(dto.biWeekly)
    Assertions.assertThat(sessionTemplateDto.enhanced).isEqualTo(true)
  }
}
