package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitevents

import org.jetbrains.annotations.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.PermittedSessionLocationDto

data class SessionLocationGroupUpdatedDto(
  @NotNull
  val prisonCode: String,

  @NotNull
  val locationGroupReference: String,

  @NotNull
  val oldLocations: List<PermittedSessionLocationDto>,
) : VisitEventDto
