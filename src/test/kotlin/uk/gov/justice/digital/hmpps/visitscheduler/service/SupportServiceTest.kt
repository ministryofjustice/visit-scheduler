package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.SupportType
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SupportTypeRepository

@ExtendWith(MockitoExtension::class)
class SupportServiceTest {

  private val supportTypeRepository = mock<SupportTypeRepository>()

  private lateinit var supportService: SupportService

  @BeforeEach
  fun setUp() {
    supportService = SupportService(
      supportTypeRepository
    )
  }

  @Nested
  @DisplayName("Available support")
  inner class AvailableSupport {

    private fun mockRepositoryResponse(supportTypes: List<SupportType>) {
      whenever(
        supportTypeRepository.findAll()
      ).thenReturn(supportTypes)
    }

    @Test
    fun `returns available support`() {

      val supportType = SupportType(
        code = 10001,
        name = "TEST_NAME",
        description = "This is the description"
      )
      mockRepositoryResponse(listOf(supportType))

      val supportTypes = supportService.getSupportTypes()
      assertThat(supportTypes).size().isEqualTo(1)
      assertThat(supportTypes[0].type).isEqualTo(supportType.name)
      assertThat(supportTypes[0].description).isEqualTo(supportType.description)

      Mockito.verify(supportTypeRepository, times(1)).findAll()
    }
  }
}
