package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Visitor Details passed while making a booking")
data class BookingRequestVisitorDetailsDto(
  @param:Schema(description = "Person ID (nomis) of the visitor", required = true)
  val visitorId: Long,
  @param:Schema(description = "Age of the visitor while making the booking, null if not available", required = false)
  val visitorAge: Int?,
)
