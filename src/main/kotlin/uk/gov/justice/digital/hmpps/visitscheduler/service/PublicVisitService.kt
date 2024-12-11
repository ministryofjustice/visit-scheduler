package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.builder.VisitDtoBuilder
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository

@Service
@Transactional
class PublicVisitService(
  private val visitRepository: VisitRepository,
) {
  @Autowired
  private lateinit var visitDtoBuilder: VisitDtoBuilder

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getFuturePublicBookedVisitsByBookerReference(bookerReference: String): List<VisitDto> {
    val x = visitRepository.getPublicFutureBookingsByBookerReference(bookerReference)

    val y = x.map { visitDtoBuilder.build(it) }.sortedBy { it.startTimestamp }

    return y
  }

  fun getPublicCanceledVisitsByBookerReference(bookerReference: String): List<VisitDto> {
    return visitRepository.getPublicCanceledVisitsByBookerReference(bookerReference).map { visitDtoBuilder.build(it) }.sortedByDescending { it.modifiedTimestamp }
  }

  fun getPublicPastVisitsByBookerReference(bookerReference: String): List<VisitDto> {
    return visitRepository.getPublicPastBookingsByBookerReference(bookerReference).map { visitDtoBuilder.build(it) }.sortedByDescending { it.startTimestamp }
  }
}
