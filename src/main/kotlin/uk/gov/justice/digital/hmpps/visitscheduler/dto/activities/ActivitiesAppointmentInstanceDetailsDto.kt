package uk.gov.justice.digital.hmpps.visitscheduler.dto.activities

import java.time.LocalDate

class ActivitiesAppointmentInstanceDetailsDto(
  val categoryCode: String,
  val appointmentDate: LocalDate,
  val startTime: String,
  val endTime: String,
)
