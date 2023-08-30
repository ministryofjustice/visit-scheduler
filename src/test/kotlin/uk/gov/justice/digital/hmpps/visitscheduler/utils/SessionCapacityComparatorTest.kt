package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionCapacityDto

@ExtendWith(MockitoExtension::class)
class SessionCapacityComparatorTest {
  @InjectMocks
  val sessionCapacityComparator = SessionTemplateUtil().sessionCapacityComparator

  @Test
  fun `session template capacity comparator tests`() {
    // Given
    val a1 = SessionCapacityDto(open = 25, closed = 50)
    val a2 = SessionCapacityDto(open = 25, closed = 50)
    val a3 = SessionCapacityDto(open = 50, closed = 40)
    val a4 = SessionCapacityDto(open = 10, closed = 60)
    val a5 = SessionCapacityDto(open = 24, closed = 48)
    val a6 = SessionCapacityDto(open = 25, closed = 48)
    val a7 = SessionCapacityDto(open = 24, closed = 50)

    // same open and closed counts
    assertThat(sessionCapacityComparator.compare(a1, a2)).isEqualTo(0)

    // lower open count - a1 is less
    assertThat(sessionCapacityComparator.compare(a1, a3)).isEqualTo(-1)

    // lower closed count - so a3 is less than a1 - so -1
    assertThat(sessionCapacityComparator.compare(a3, a1)).isEqualTo(-1)

    // lower closed count - a1 is less
    assertThat(sessionCapacityComparator.compare(a1, a4)).isEqualTo(-1)

    // higher open and closed count - a1 is higher
    assertThat(sessionCapacityComparator.compare(a1, a5)).isEqualTo(1)
    assertThat(sessionCapacityComparator.compare(a5, a1)).isEqualTo(-1)

    assertThat(sessionCapacityComparator.compare(a1, a6)).isEqualTo(1)
    assertThat(sessionCapacityComparator.compare(a1, a7)).isEqualTo(1)

    // a5 is lower
    assertThat(sessionCapacityComparator.compare(a5, a2)).isEqualTo(-1)
    assertThat(sessionCapacityComparator.compare(a2, a5)).isEqualTo(1)
  }
}
