package uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "Offender non-association detail")
class OffenderNonAssociationDetailDto(
  @Schema(description = "Date and time the mom-association is effective from.", example = "2019-12-01", required = true)
  val effectiveDate: LocalDate,
  @Schema(description = "Date and time the mom-association expires.", example = "2019-12-01", required = false)
  val expiryDate: LocalDate? = null,
  @Schema(description = "The offender with whom not to associate.", required = true)
  val offenderNonAssociation: OffenderNonAssociationDto
)
