package uk.gov.justice.digital.hmpps.visitscheduler.dto.audit

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.ActionedBy

@Schema(description = "Actioned By")
data class ActionedByDto(

  @param:Schema(description = "booker reference", example = "asd-aed-vhj", required = false)
  val bookerReference: String?,

  @param:Schema(description = "User Name", example = "AS/ALED", required = false)
  val userName: String?,

  @param:Schema(description = "User type", example = "STAFF", required = false)
  @field:NotNull
  val userType: UserType,
) {
  constructor(entity: ActionedBy) : this(
    bookerReference = entity.bookerReference,
    userName = entity.userName,
    userType = entity.userType,
  )
}
