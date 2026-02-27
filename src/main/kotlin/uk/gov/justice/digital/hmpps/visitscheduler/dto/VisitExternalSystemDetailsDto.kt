package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitExternalSystemDetails

data class VisitExternalSystemDetailsDto(
  @param:Schema(description = "Client name", example = "client_name")
  val clientName: String?,
  @param:Schema(description = "Client visit reference", example = "Reference ID in the client system")
  val clientVisitReference: String?,
) {
  constructor(visitExternalSystemDetailsEntity: VisitExternalSystemDetails) : this (
    clientName = visitExternalSystemDetailsEntity.clientName,
    clientVisitReference = visitExternalSystemDetailsEntity.clientReference,
  )
}
