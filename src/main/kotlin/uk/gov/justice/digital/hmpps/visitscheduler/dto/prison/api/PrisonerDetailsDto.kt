package uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api

interface PrisonerDetailsDto {
  fun getLevels(): Map<PrisonerHousingLevels, String?>
}
