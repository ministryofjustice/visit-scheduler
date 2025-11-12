package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.ApplicationVisitor

@Schema(description = "Visitor")
data class VisitorDto(
  @param:Schema(description = "Person ID (nomis) of the visitor", example = "1234", required = true)
  @field:NotNull
  val nomisPersonId: Long,
  @param:Schema(description = "true if visitor is the contact for the visit otherwise false", example = "true", required = false)
  val visitContact: Boolean?,
) {

  constructor(entity: VisitVisitor) : this(
    nomisPersonId = entity.nomisPersonId,
    visitContact = entity.visitContact,
  )

  constructor(entity: ApplicationVisitor) : this(
    nomisPersonId = entity.nomisPersonId,
    visitContact = entity.contact,
  )
}
